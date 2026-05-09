package com.example.chatterinomobile.data.repository

import com.example.chatterinomobile.data.local.EmoteDimensionStore
import com.example.chatterinomobile.data.local.EmoteDiskCache
import com.example.chatterinomobile.data.model.Emote
import com.example.chatterinomobile.data.model.EmoteProvider
import com.example.chatterinomobile.data.remote.api.BttvApi
import com.example.chatterinomobile.data.remote.api.FfzApi
import com.example.chatterinomobile.data.remote.api.SevenTvApi
import com.example.chatterinomobile.data.remote.api.TwitchHelixApi
import com.example.chatterinomobile.data.remote.mapper.toDomain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class EmoteRepositoryImpl(
    private val sevenTvApi: SevenTvApi,
    private val bttvApi: BttvApi,
    private val ffzApi: FfzApi,
    private val helixApi: TwitchHelixApi,
    private val diskCache: EmoteDiskCache,
    private val dimensionStore: EmoteDimensionStore,
    scopeOverride: CoroutineScope? = null
) : EmoteRepository {

    private val scope = scopeOverride ?: CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val globalEmotesByName = HashMap<String, Emote>()
    private val channelEmotesByChannelId = HashMap<String, HashMap<String, Emote>>()

    private var globalSortedIndex: List<IndexEntry> = emptyList()
    private val channelSortedIndex = HashMap<String, List<IndexEntry>>()

    private var globalLoadedAtMillis = 0L
    private val channelLoadedAtMillis = HashMap<String, Long>()
    private val channelLastAccessedAtMillis = HashMap<String, Long>()
    private val inFlightLoads = HashMap<String, Deferred<Map<String, Emote>>>()
    private val mutex = Mutex()

    init {
        scope.launch { dimensionStore.ensureLoaded() }
        scope.launch { idleEvictionLoop() }
    }

    override suspend fun loadEmotesForChannel(channelId: String?) {
        if (channelId == null) {
            loadGlobalWithDiskFallback()
        } else {
            loadChannelWithDiskFallback(channelId)
        }
    }

    override fun findEmote(name: String, channelId: String?): Emote? {
        if (channelId != null) {
            channelEmotesByChannelId[channelId]?.get(name)?.let {
                channelLastAccessedAtMillis[channelId] = System.currentTimeMillis()
                return decorate(it)
            }
        }
        return globalEmotesByName[name]?.let(::decorate)
    }

    override fun listEmotesForChannel(channelId: String?): EmoteCatalog {
        if (channelId != null) {
            channelLastAccessedAtMillis[channelId] = System.currentTimeMillis()
        }

        val merged = HashMap<String, Emote>(globalEmotesByName.size + 64)
        merged.putAll(globalEmotesByName)
        val channelEmotes = if (channelId != null) {
            channelEmotesByChannelId[channelId].orEmpty()
        } else {
            emptyMap()
        }
        if (channelId != null) {
            merged.putAll(channelEmotes)
        }

        return EmoteCatalog(
            byProvider = groupByProvider(merged.values),
            globalByProvider = groupByProvider(globalEmotesByName.values),
            channelByProvider = groupByProvider(channelEmotes.values)
        )
    }

    private fun groupByProvider(emotes: Collection<Emote>): Map<EmoteProvider, List<Emote>> {
        val byProvider = LinkedHashMap<EmoteProvider, ArrayList<Emote>>(EmoteProvider.entries.size)
        byProvider[EmoteProvider.TWITCH] = ArrayList()
        byProvider[EmoteProvider.SEVENTV] = ArrayList()
        byProvider[EmoteProvider.BTTV] = ArrayList()
        byProvider[EmoteProvider.FFZ] = ArrayList()

        for (emote in emotes) {
            byProvider.getOrPut(emote.provider) { ArrayList() }.add(decorate(emote))
        }

        for (list in byProvider.values) {
            list.sortBy { it.name.lowercase() }
        }

        return byProvider.mapValues { it.value.toList() }
    }

    override fun searchByPrefix(query: String, channelId: String?, limit: Int): List<Emote> {
        if (query.isEmpty()) return emptyList()
        if (channelId != null) {
            channelLastAccessedAtMillis[channelId] = System.currentTimeMillis()
        }

        val needle = query.lowercase()
        val results = ArrayList<ScoredEntry>(64)

        scanIndex(channelSortedIndex[channelId].orEmpty(), needle, results, fromChannel = true)
        scanIndex(globalSortedIndex, needle, results, fromChannel = false)

        if (results.isEmpty()) return emptyList()

        results.sortWith(SCORED_COMPARATOR)
        val seen = HashSet<String>(results.size)
        val out = ArrayList<Emote>(minOf(limit, results.size))
        for (entry in results) {
            if (out.size >= limit) break
            if (seen.add(entry.entry.emote.name)) {
                out.add(decorate(entry.entry.emote))
            }
        }
        return out
    }

    override fun recordDimensions(emoteId: String, channelId: String?, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val prior = dimensionStore.get(emoteId)
        if (prior != null && prior.width == width && prior.height == height) return
        dimensionStore.record(emoteId, width, height)

        scope.launch { patchDiskAspectRatio(emoteId, channelId, width, height) }
    }

    override fun clearCache(channelId: String?) {
        if (channelId == null) {
            globalEmotesByName.clear()
            channelEmotesByChannelId.clear()
            channelLoadedAtMillis.clear()
            channelLastAccessedAtMillis.clear()
            globalSortedIndex = emptyList()
            channelSortedIndex.clear()
            globalLoadedAtMillis = 0L
            return
        }

        channelEmotesByChannelId.remove(channelId)
        channelLoadedAtMillis.remove(channelId)
        channelLastAccessedAtMillis.remove(channelId)
        channelSortedIndex.remove(channelId)
    }

    private fun decorate(emote: Emote): Emote {
        if (emote.aspectRatio != null) return emote
        val learned = dimensionStore.get(emote.id) ?: return emote
        return emote.copy(aspectRatio = learned.aspectRatio)
    }

    private suspend fun loadGlobalWithDiskFallback() {
        val snapshot = diskCache.readGlobal()
        if (snapshot != null && globalLoadedAtMillis == 0L) {
            mutex.withLock {
                if (globalLoadedAtMillis == 0L) {
                    globalEmotesByName.clear()
                    globalEmotesByName.putAll(snapshot.emotes)
                    globalLoadedAtMillis = snapshot.savedAtEpochMillis
                    globalSortedIndex = buildIndex(globalEmotesByName.values)
                }
            }
        }

        val needsRefresh = snapshot == null ||
            System.currentTimeMillis() - snapshot.savedAtEpochMillis >= GLOBAL_CACHE_TTL_MILLIS

        if (!needsRefresh) return

        val refresh = scope.launch { refreshGlobalFromNetwork() }
        if (snapshot == null) refresh.join()
    }

    private suspend fun refreshGlobalFromNetwork() = withContext(Dispatchers.IO) {
        val loaded = loadGlobalEmotes()
        if (loaded.isEmpty()) return@withContext
        mutex.withLock {
            globalEmotesByName.clear()
            globalEmotesByName.putAll(loaded)
            globalLoadedAtMillis = System.currentTimeMillis()
            globalSortedIndex = buildIndex(globalEmotesByName.values)
        }
        diskCache.writeGlobal(loaded)
    }

    private suspend fun loadChannelWithDiskFallback(channelId: String) {
        channelLastAccessedAtMillis[channelId] = System.currentTimeMillis()

        val snapshot = diskCache.readChannel(channelId)
        if (snapshot != null && !channelEmotesByChannelId.containsKey(channelId)) {
            mutex.withLock {
                if (!channelEmotesByChannelId.containsKey(channelId)) {
                    val map = HashMap(snapshot.emotes)
                    channelEmotesByChannelId[channelId] = map
                    channelLoadedAtMillis[channelId] = snapshot.savedAtEpochMillis
                    channelSortedIndex[channelId] = buildIndex(map.values)
                }
            }
        }

        val needsRefresh = snapshot == null ||
            System.currentTimeMillis() - snapshot.savedAtEpochMillis >= CHANNEL_CACHE_TTL_MILLIS

        if (!needsRefresh) return

        val refresh: Deferred<Map<String, Emote>> = mutex.withLock {
            inFlightLoads[channelId] ?: scope.async {
                runCatching { fetchChannelEmotes(channelId) }.getOrElse { emptyMap() }
            }.also { inFlightLoads[channelId] = it }
        }

        val refreshJob = scope.launch {
            val loaded = try {
                refresh.await()
            } finally {
                mutex.withLock { inFlightLoads.remove(channelId) }
            }
            if (loaded.isNotEmpty()) {
                mutex.withLock {
                    val map = HashMap(loaded)
                    channelEmotesByChannelId[channelId] = map
                    channelLoadedAtMillis[channelId] = System.currentTimeMillis()
                    channelSortedIndex[channelId] = buildIndex(map.values)
                }
                diskCache.writeChannel(channelId, loaded)
            }
        }

        if (snapshot == null) refreshJob.join()
    }

    private suspend fun patchDiskAspectRatio(
        emoteId: String,
        channelId: String?,
        width: Int,
        height: Int
    ) {
        val ratio = width.toFloat() / height.toFloat()

        val (owningChannelId, emote) = locateEmote(emoteId, channelId) ?: return
        if (emote.aspectRatio == ratio) return
        val updated = emote.copy(aspectRatio = ratio)

        mutex.withLock {
            if (owningChannelId == null) {
                globalEmotesByName[emote.name] = updated
            } else {
                channelEmotesByChannelId[owningChannelId]?.put(emote.name, updated)
            }
        }
        diskCache.patchEmote(owningChannelId, updated)
    }

    private fun locateEmote(emoteId: String, hintedChannelId: String?): Pair<String?, Emote>? {
        if (hintedChannelId != null) {
            channelEmotesByChannelId[hintedChannelId]?.values
                ?.firstOrNull { it.id == emoteId }
                ?.let { return hintedChannelId to it }
        }
        globalEmotesByName.values.firstOrNull { it.id == emoteId }
            ?.let { return null to it }
        for ((cid, map) in channelEmotesByChannelId) {
            map.values.firstOrNull { it.id == emoteId }?.let { return cid to it }
        }
        return null
    }

    private suspend fun idleEvictionLoop() {
        while (true) {
            delay(IDLE_EVICTION_TICK_MILLIS)
            val cutoff = System.currentTimeMillis() - CHANNEL_IDLE_EVICT_MILLIS
            val victims = mutex.withLock {
                channelLastAccessedAtMillis
                    .filterValues { it < cutoff }
                    .keys
                    .toList()
            }
            if (victims.isEmpty()) continue
            mutex.withLock {
                for (channelId in victims) {
                    channelEmotesByChannelId.remove(channelId)
                    channelLoadedAtMillis.remove(channelId)
                    channelLastAccessedAtMillis.remove(channelId)
                    channelSortedIndex.remove(channelId)
                }
            }
        }
    }

    private fun mergeWithPrecedence(
        twitch: List<Emote>,
        sevenTv: List<Emote>,
        ffz: List<Emote>,
        bttv: List<Emote>
    ): HashMap<String, Emote> {
        val merged = HashMap<String, Emote>(twitch.size + sevenTv.size + ffz.size + bttv.size)
        for (emote in twitch) merged[emote.name] = emote
        for (emote in sevenTv) merged[emote.name] = emote
        for (emote in ffz) merged[emote.name] = emote
        for (emote in bttv) merged[emote.name] = emote
        return merged
    }

    private suspend fun loadGlobalEmotes(): HashMap<String, Emote> = coroutineScope {
        val globalTwitch: Deferred<List<Emote>> = async {
            runCatching {
                val response = helixApi.getGlobalEmotes()
                response.data.map { it.toDomain(response.template) }
            }.getOrElse { emptyList() }
        }
        val globalSevenTv: Deferred<List<Emote>> = async {
            runCatching { sevenTvApi.getGlobalEmoteSet().emotes.map { it.toDomain() } }
                .getOrElse { emptyList() }
        }
        val globalFfz: Deferred<List<Emote>> = async {
            runCatching { ffzApi.getGlobalEmotes().map { it.toDomain() } }
                .getOrElse { emptyList() }
        }
        val globalBttv: Deferred<List<Emote>> = async {
            runCatching { bttvApi.getGlobalEmotes().map { it.toDomain() } }
                .getOrElse { emptyList() }
        }

        mergeWithPrecedence(
            twitch = globalTwitch.await(),
            sevenTv = globalSevenTv.await(),
            ffz = globalFfz.await(),
            bttv = globalBttv.await()
        )
    }

    private suspend fun fetchChannelEmotes(channelId: String): Map<String, Emote> = coroutineScope {
        val channelTwitch: Deferred<List<Emote>> = async {
            runCatching {
                val response = helixApi.getChannelEmotes(channelId)
                response.data.map { it.toDomain(response.template) }
            }.getOrElse { emptyList() }
        }
        val channelSevenTv: Deferred<List<Emote>> = async {
            runCatching { sevenTvApi.getChannelEmotes(channelId).map { it.toDomain() } }
                .getOrElse { emptyList() }
        }
        val channelFfz: Deferred<List<Emote>> = async {
            runCatching { ffzApi.getChannelEmotes(channelId).map { it.toDomain() } }
                .getOrElse { emptyList() }
        }
        val channelBttv: Deferred<List<Emote>> = async {
            runCatching { bttvApi.getChannelEmotes(channelId).map { it.toDomain() } }
                .getOrElse { emptyList() }
        }

        mergeWithPrecedence(
            twitch = channelTwitch.await(),
            sevenTv = channelSevenTv.await(),
            ffz = channelFfz.await(),
            bttv = channelBttv.await()
        )
    }

    private fun buildIndex(emotes: Collection<Emote>): List<IndexEntry> {
        val list = ArrayList<IndexEntry>(emotes.size)
        for (emote in emotes) list.add(IndexEntry(emote.name.lowercase(), emote))
        list.sortBy { it.lowerName }
        return list
    }

    private fun scanIndex(
        index: List<IndexEntry>,
        needle: String,
        out: ArrayList<ScoredEntry>,
        fromChannel: Boolean
    ) {
        if (index.isEmpty()) return

        val prefixStart = lowerBound(index, needle)
        var i = prefixStart
        while (i < index.size && index[i].lowerName.startsWith(needle)) {
            out.add(ScoredEntry(index[i], score = scorePrefix(needle, index[i]), fromChannel = fromChannel))
            i++
        }

        for (j in index.indices) {
            if (j in prefixStart until i) continue
            val entry = index[j]
            if (entry.lowerName.contains(needle)) {
                out.add(ScoredEntry(entry, score = scoreContains(needle, entry), fromChannel = fromChannel))
            }
        }
    }

    private fun lowerBound(index: List<IndexEntry>, needle: String): Int {
        var lo = 0
        var hi = index.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (index[mid].lowerName < needle) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private fun scorePrefix(needle: String, entry: IndexEntry): Int {
        val lengthPenalty = (entry.lowerName.length - needle.length) * 100
        val caseBoost = if (entry.emote.name.startsWith(needle, ignoreCase = false)) 0 else 5_000
        return lengthPenalty + caseBoost
    }

    private fun scoreContains(needle: String, entry: IndexEntry): Int {
        val idx = entry.lowerName.indexOf(needle)
        val positionPenalty = idx * 200
        val lengthPenalty = (entry.lowerName.length - needle.length) * 50
        return 200_000 + positionPenalty + lengthPenalty
    }

    private data class IndexEntry(val lowerName: String, val emote: Emote)

    private data class ScoredEntry(
        val entry: IndexEntry,
        val score: Int,
        val fromChannel: Boolean
    )

    companion object {
        private const val GLOBAL_CACHE_TTL_MILLIS = 6L * 60L * 60L * 1000L
        private const val CHANNEL_CACHE_TTL_MILLIS = 30L * 60L * 1000L
        private const val CHANNEL_IDLE_EVICT_MILLIS = 15L * 60L * 1000L
        private const val IDLE_EVICTION_TICK_MILLIS = 60L * 1000L

        private val SCORED_COMPARATOR = Comparator<ScoredEntry> { a, b ->
            if (a.fromChannel != b.fromChannel) {
                if (a.fromChannel) -1 else 1
            } else {
                a.score.compareTo(b.score)
            }
        }
    }
}
