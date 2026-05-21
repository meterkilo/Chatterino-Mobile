package com.example.chatterinomobile.ui.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatterinomobile.data.local.DiscoverySnapshotCache
import com.example.chatterinomobile.data.local.FollowListCache
import com.example.chatterinomobile.data.model.Category
import com.example.chatterinomobile.data.model.Channel
import com.example.chatterinomobile.data.remote.api.TwitchHelixApi
import com.example.chatterinomobile.data.remote.dto.HelixGameDto
import com.example.chatterinomobile.data.remote.dto.HelixStreamDto
import com.example.chatterinomobile.data.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiscoveryUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val followedLive: List<Channel> = emptyList(),
    val followedLogins: List<String> = emptyList(),
    val knownChannels: List<Channel> = emptyList(),
    val recommendedStreams: List<Channel> = emptyList(),
    val topLiveStreams: List<Channel> = emptyList(),
    val topCategories: List<Category> = emptyList(),
    val activeCategory: Category? = null,
    val activeCategoryStreams: List<Channel> = emptyList(),
    val isLoadingCategoryStreams: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val searchResults: List<Channel> = emptyList(),
    val isSearching: Boolean = false,
    val searchActive: Boolean = false
)

class DiscoveryViewModel(
    private val helixApi: TwitchHelixApi,
    private val authRepository: AuthRepository,
    private val followListCache: FollowListCache,
    private val snapshotCache: DiscoverySnapshotCache
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoveryUiState())
    val uiState: StateFlow<DiscoveryUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var pinnedChannelsJob: Job? = null
    private var categoryCountsJob: Job? = null
    private var foregroundRefreshJob: Job? = null
    private var followRefreshJob: Job? = null

    init {
        load()
    }

    fun refresh() {
        quickRefresh()
        refreshActiveCategoryStreams()
    }

    fun openSearch() {
        _uiState.update { it.copy(searchActive = true, searchQuery = "", searchResults = emptyList()) }
    }

    fun closeSearch() {
        searchJob?.cancel()
        _uiState.update { it.copy(searchActive = false, searchQuery = "", searchResults = emptyList(), isSearching = false) }
    }

    fun hydratePinnedChannels(logins: List<String>) {
        val normalized = logins
            .map { it.lowercase().removePrefix("#").trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (normalized.isEmpty()) return
        val knownLogins = _uiState.value.knownChannels
            .map { it.login.lowercase() }
            .toSet()
        val missing = normalized.filterNot { it in knownLogins }
        if (missing.isEmpty()) return

        pinnedChannelsJob?.cancel()
        pinnedChannelsJob = viewModelScope.launch {
            // Let the cached discovery snapshot render first; pinned metadata is nice-to-have.
            delay(PINNED_HYDRATION_DELAY_MILLIS)
            val channels = runCatching { fetchChannelsByLogin(missing) }
                .getOrElse { error ->
                    if (error is CancellationException) throw error
                    emptyList()
                }
            if (channels.isNotEmpty()) {
                _uiState.update { it.copy(knownChannels = mergeChannels(it.knownChannels, channels)) }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _uiState.update { it.copy(isSearching = true) }
            val results = runCatching { helixApi.searchChannels(query) }
                .getOrElse { error ->
                    if (error is CancellationException) throw error
                    emptyList()
                }
            val usersByLogin = results
                .map { it.broadcasterLogin }
                .chunked(100)
                .map { batch ->
                    async {
                        runCatching { helixApi.getUsersByLogin(batch) }
                            .getOrElse { error ->
                                if (error is CancellationException) throw error
                                emptyList()
                            }
                    }
                }
                .awaitAll()
                .flatten()
                .associateBy { it.login.lowercase() }
            val channels = results
                .map { dto ->
                    val user = usersByLogin[dto.broadcasterLogin.lowercase()]
                    Channel(
                        id = dto.id,
                        login = dto.broadcasterLogin,
                        displayName = dto.displayName,
                        isLive = dto.isLive,
                        gameName = dto.gameName,
                        title = dto.title,
                        profileImageUrl = user?.profileImageUrl ?: dto.thumbnailUrl,
                        isPartner = user?.broadcasterType == "partner"
                    )
                }
                .sortedWith(
                    compareByDescending<Channel> { it.isLive }
                        .thenBy { it.displayName.lowercase() }
                )
            _uiState.update { it.copy(searchResults = channels, isSearching = false) }
        }
    }

    private fun load(showRefreshIndicator: Boolean = false) {
        viewModelScope.launch {
            try {
                val userId = authRepository.getUserId()
                val userKey = userId ?: ANON_KEY

                val followSnapshot = if (userId != null) followListCache.read(userId) else null
                val cachedLogins = followSnapshot?.logins
                val shouldRefreshFollows = userId != null &&
                    (cachedLogins == null ||
                        System.currentTimeMillis() - followSnapshot.savedAtEpochMillis >= FOLLOW_LIST_TTL_MILLIS)

                _uiState.update { current ->
                    current.copy(
                        isLoading = !showRefreshIndicator,
                        isRefreshing = showRefreshIndicator,
                        followedLive = if (showRefreshIndicator) current.followedLive else emptyList(),
                        followedLogins = cachedLogins ?: current.followedLogins,
                        recommendedStreams = if (showRefreshIndicator) current.recommendedStreams else emptyList(),
                        topLiveStreams = if (showRefreshIndicator) current.topLiveStreams else emptyList(),
                        topCategories = if (showRefreshIndicator) current.topCategories else emptyList(),
                        error = null
                    )
                }

                loadLiveAndRecommended(
                    userId = userId,
                    logins = cachedLogins,
                    refreshFollows = showRefreshIndicator || shouldRefreshFollows,
                    includeExpensiveCategoryCounts = true,
                    lightweight = false
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = e.message) }
            }
        }
    }

    private fun quickRefresh() {
        if (_uiState.value.isRefreshing) return
        foregroundRefreshJob?.cancel()
        foregroundRefreshJob = viewModelScope.launch {
            val userId = authRepository.getUserId()
            val userKey = userId ?: ANON_KEY
            val cachedLogins = if (userId != null) followListCache.read(userId)?.logins else null
            val followedLogins = cachedLogins ?: _uiState.value.followedLogins

            _uiState.update {
                it.copy(
                    isRefreshing = true,
                    followedLogins = followedLogins,
                    error = null
                )
            }

            try {
                loadLiveAndRecommended(
                    userId = userId,
                    logins = followedLogins,
                    refreshFollows = false,
                    includeExpensiveCategoryCounts = true,
                    lightweight = true
                )
                refreshFollowListInBackground(userId = userId, userKey = userKey)
            } catch (e: Exception) {
                _uiState.update { it.copy(isRefreshing = false, error = e.message) }
            }
        }
    }

    private suspend fun loadLiveAndRecommended(
        userId: String?,
        logins: List<String>?,
        refreshFollows: Boolean,
        includeExpensiveCategoryCounts: Boolean,
        lightweight: Boolean
    ) {
        categoryCountsJob?.cancel()
        categoryCountsJob = null

        coroutineScope {
            val freshFollowsDeferred = if (refreshFollows && userId != null) {
                async { fetchAllFollows(userId) }
            } else null

            val topStreamsDeferred = async {
                runCatching { helixApi.getTopStreams(limit = 50) }.getOrElse { emptyList() }
            }

            val topGamesDeferred = async {
                runCatching { helixApi.getTopGames(limit = 30) }.getOrElse { emptyList() }
            }

            val followedLogins = if (freshFollowsDeferred != null) {
                val fresh = freshFollowsDeferred.await()
                if (userId != null && fresh.isNotEmpty()) followListCache.write(userId, fresh)
                fresh
            } else {
                logins ?: emptyList()
            }

            val topStreams = topStreamsDeferred.await()
            val knownByLogin = _uiState.value.knownChannels
                .distinctBy { it.login.lowercase() }
                .associateBy { it.login.lowercase() }

            val followedLive: List<Channel> = if (followedLogins.isNotEmpty()) {
                val liveStreams = followedLogins
                    .chunked(100)
                    .map { batch ->
                        async {
                            runCatching { helixApi.getStreamsByLogin(batch) }.getOrElse { emptyList() }
                        }
                    }
                    .awaitAll()
                    .flatten()

                val usersByLogin = if (!lightweight) {
                    val liveLogins = liveStreams.map { it.userLogin }
                    if (liveLogins.isNotEmpty()) {
                        liveLogins
                            .chunked(100)
                            .map { batch ->
                                async {
                                    runCatching { helixApi.getUsersByLogin(batch) }.getOrElse { emptyList() }
                                }
                            }
                            .awaitAll()
                            .flatten()
                            .associateBy { it.login }
                    } else emptyMap()
                } else emptyMap()

                val streamsByLogin = liveStreams.associateBy { it.userLogin }
                followedLogins
                    .mapNotNull { login -> streamsByLogin[login] }
                    .map { stream ->
                        val key = stream.userLogin.lowercase()
                        val known = knownByLogin[key]
                        val user = usersByLogin[stream.userLogin]
                        Channel(
                            id = stream.userId,
                            login = stream.userLogin,
                            displayName = stream.userName,
                            isLive = true,
                            viewerCount = stream.viewerCount,
                            gameName = stream.gameName,
                            title = stream.title,
                            thumbnailUrl = thumbnailUrl(stream.thumbnailUrl),
                            profileImageUrl = user?.profileImageUrl ?: known?.profileImageUrl,
                            isPartner = user?.broadcasterType == "partner" || known?.isPartner == true
                        )
                    }
            } else emptyList()

            val followedLoginSet = followedLogins.toSet()
            val followedChannels = if (!lightweight && followedLogins.isNotEmpty() && (refreshFollows || logins == null)) {
                runCatching { fetchChannelsByLogin(followedLogins) }
                    .getOrElse { error ->
                        if (error is CancellationException) throw error
                        emptyList()
                    }
            } else {
                emptyList()
            }

            val topUsers = if (!lightweight) {
                topStreams
                    .map { it.userLogin }
                    .chunked(100)
                    .map { batch ->
                        async {
                            runCatching { helixApi.getUsersByLogin(batch) }.getOrElse { emptyList() }
                        }
                    }
                    .awaitAll()
                    .flatten()
                    .associateBy { it.login }
            } else emptyMap()

            val topLive = topStreams
                .sortedByDescending { it.viewerCount }
                .map { stream ->
                    val known = knownByLogin[stream.userLogin.lowercase()]
                    Channel(
                        id = stream.userId,
                        login = stream.userLogin,
                        displayName = stream.userName,
                        isLive = true,
                        viewerCount = stream.viewerCount,
                        gameName = stream.gameName,
                        title = stream.title,
                        thumbnailUrl = thumbnailUrl(stream.thumbnailUrl),
                        profileImageUrl = topUsers[stream.userLogin]?.profileImageUrl ?: known?.profileImageUrl,
                        isPartner = topUsers[stream.userLogin]?.broadcasterType == "partner" || known?.isPartner == true
                    )
                }

            val recommended = topLive.filter { it.login !in followedLoginSet }.take(10)

            val viewersFromTopStreamsByGameId = topStreams
                .filter { it.gameId != null }
                .groupBy { it.gameId!! }
                .mapValues { (_, list) -> list.sumOf { it.viewerCount } }

            val topGames = topGamesDeferred.await()
            val topCategories = buildTopCategories(
                topGames = topGames,
                fallbackViewersByGameId = viewersFromTopStreamsByGameId
            )

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    followedLive = followedLive,
                    followedLogins = followedLogins,
                    knownChannels = mergeChannels(
                        it.knownChannels,
                        followedChannels + followedLive + recommended + topLive
                    ),
                    recommendedStreams = recommended,
                    topLiveStreams = topLive,
                    topCategories = topCategories,
                    error = null
                )
            }

            runCatching {
                snapshotCache.write(
                    userKey = userId ?: ANON_KEY,
                    followedLive = followedLive,
                    topLiveStreams = topLive,
                    topCategories = topCategories
                )
            }

            if (includeExpensiveCategoryCounts && topGames.isNotEmpty()) {
                categoryCountsJob = viewModelScope.launch {
                    val sampledViewersByGameId = sampleCategoryViewerCounts(topGames)
                    if (sampledViewersByGameId.isEmpty()) return@launch

                    val refreshedTopCategories = buildTopCategories(
                        topGames = topGames,
                        fallbackViewersByGameId = viewersFromTopStreamsByGameId,
                        sampledViewersByGameId = sampledViewersByGameId
                    )

                    _uiState.update { current ->
                        current.copy(
                            topCategories = current.topCategories.map { category ->
                                sampledViewersByGameId[category.id]?.let { category.copy(viewerCount = it) }
                                    ?: category
                            }
                        )
                    }

                    runCatching {
                        snapshotCache.write(
                            userKey = userId ?: ANON_KEY,
                            followedLive = followedLive,
                            topLiveStreams = topLive,
                            topCategories = refreshedTopCategories
                        )
                    }
                }
            }
        }
    }

    private fun refreshFollowListInBackground(userId: String?, userKey: String) {
        if (userId == null) return
        followRefreshJob?.cancel()
        followRefreshJob = viewModelScope.launch {
            val fresh = runCatching { fetchAllFollows(userId) }
                .getOrElse { error ->
                    if (error is CancellationException) throw error
                    emptyList()
                }
            if (fresh.isEmpty()) return@launch

            followListCache.write(userId, fresh)
            _uiState.update { it.copy(followedLogins = fresh) }

            val liveStreams = fresh
                .chunked(100)
                .map { batch ->
                    async {
                        runCatching { helixApi.getStreamsByLogin(batch) }.getOrElse { emptyList() }
                    }
                }
                .awaitAll()
                .flatten()

            val knownByLogin = _uiState.value.knownChannels
                .distinctBy { it.login.lowercase() }
                .associateBy { it.login.lowercase() }
            val streamsByLogin = liveStreams.associateBy { it.userLogin }
            val followedSet = fresh.toSet()
            val followedLive = fresh
                .mapNotNull { login -> streamsByLogin[login] }
                .map { stream ->
                    val known = knownByLogin[stream.userLogin.lowercase()]
                    Channel(
                        id = stream.userId,
                        login = stream.userLogin,
                        displayName = stream.userName,
                        isLive = true,
                        viewerCount = stream.viewerCount,
                        gameName = stream.gameName,
                        title = stream.title,
                        thumbnailUrl = thumbnailUrl(stream.thumbnailUrl),
                        profileImageUrl = known?.profileImageUrl,
                        isPartner = known?.isPartner == true
                    )
                }

            _uiState.update {
                it.copy(
                    followedLive = followedLive,
                    knownChannels = mergeChannels(it.knownChannels, followedLive),
                    recommendedStreams = it.topLiveStreams.filter { channel -> channel.login !in followedSet }.take(10)
                )
            }

            runCatching {
                snapshotCache.write(
                    userKey = userKey,
                    followedLive = followedLive,
                    topLiveStreams = _uiState.value.topLiveStreams,
                    topCategories = _uiState.value.topCategories
                )
            }
        }
    }

    private fun buildTopCategories(
        topGames: List<HelixGameDto>,
        fallbackViewersByGameId: Map<String, Int>,
        sampledViewersByGameId: Map<String, Int> = emptyMap()
    ): List<Category> = topGames.map { game ->
        Category(
            id = game.id,
            name = game.name,
            boxArtUrl = boxArtUrl(game.boxArtUrl),
            viewerCount = sampledViewersByGameId[game.id]
                ?: fallbackViewersByGameId[game.id]
                ?: 0
        )
    }

    private suspend fun sampleCategoryViewerCounts(topGames: List<HelixGameDto>): Map<String, Int> =
        coroutineScope {
            topGames
                .take(CATEGORY_COUNT_SAMPLE_GAME_LIMIT)
                .map { game ->
                    async {
                        val streams = runCatching {
                            fetchCategoryStreams(game.id, CATEGORY_COUNT_STREAM_SAMPLE)
                        }.getOrElse { error ->
                            if (error is CancellationException) throw error
                            emptyList()
                        }
                        game.id to streams.sumOf { it.viewerCount }
                    }
                }
                .awaitAll()
                .toMap()
        }

    private suspend fun fetchAllFollows(userId: String): List<String> {
        val all = mutableListOf<String>()
        var cursor: String? = null
        do {
            val page = runCatching {
                helixApi.getFollowedChannelsPaged(userId, after = cursor)
            }.getOrNull() ?: break
            all.addAll(page.logins)
            cursor = page.nextCursor
        } while (cursor != null && all.size < MAX_FOLLOWS)
        return all
    }

    private suspend fun fetchChannelsByLogin(logins: List<String>): List<Channel> {
        val normalized = logins
            .map { it.lowercase().removePrefix("#").trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (normalized.isEmpty()) return emptyList()

        return coroutineScope {
            val usersDeferred = normalized
                .chunked(100)
                .map { batch ->
                    async { runCatching { helixApi.getUsersByLogin(batch) }.getOrElse { emptyList() } }
                }
            val streamsDeferred = normalized
                .chunked(100)
                .map { batch ->
                    async { runCatching { helixApi.getStreamsByLogin(batch) }.getOrElse { emptyList() } }
                }
            val users = usersDeferred.awaitAll().flatten()
            val streamsByLogin = streamsDeferred.awaitAll().flatten().associateBy { it.userLogin.lowercase() }

            users.map { user ->
                val stream = streamsByLogin[user.login.lowercase()]
                Channel(
                    id = user.id,
                    login = user.login,
                    displayName = user.displayName,
                    isLive = stream != null,
                    viewerCount = stream?.viewerCount ?: 0,
                    gameName = stream?.gameName,
                    title = stream?.title,
                    thumbnailUrl = thumbnailUrl(stream?.thumbnailUrl),
                    profileImageUrl = user.profileImageUrl,
                    isPartner = user.broadcasterType == "partner"
                )
            }
        }
    }

    private fun mergeChannels(existing: List<Channel>, updates: List<Channel>): List<Channel> {
        if (updates.isEmpty()) return existing
        val merged = LinkedHashMap<String, Channel>()
        existing.forEach { merged[it.login.lowercase()] = it }
        updates.forEach { update ->
            val key = update.login.lowercase()
            val current = merged[key]
            merged[key] = when {
                current == null -> update
                current.isLive && !update.isLive -> current.copy(
                    profileImageUrl = current.profileImageUrl ?: update.profileImageUrl,
                    isPartner = current.isPartner || update.isPartner
                )
                update.isLive -> update
                current.profileImageUrl.isNullOrBlank() && !update.profileImageUrl.isNullOrBlank() -> update
                update.isPartner && !current.isPartner -> current.copy(isPartner = true)
                else -> current
            }
        }
        return merged.values.toList()
    }

    private fun thumbnailUrl(raw: String?): String? {
        if (raw == null) return null
        val bucket = System.currentTimeMillis() / 1000 / 300
        return raw.replace("{width}", "440").replace("{height}", "248") + "?cb=$bucket"
    }

    private fun boxArtUrl(raw: String?): String? {
        if (raw == null) return null
        return raw.replace("{width}", "285").replace("{height}", "380")
    }

    private var categoryStreamsJob: Job? = null

    fun openCategory(category: Category) {
        loadCategoryStreams(category, clearExisting = true)
    }

    private fun refreshActiveCategoryStreams() {
        val category = _uiState.value.activeCategory ?: return
        loadCategoryStreams(category, clearExisting = false)
    }

    private fun loadCategoryStreams(category: Category, clearExisting: Boolean) {
        categoryStreamsJob?.cancel()
        _uiState.update {
            it.copy(
                activeCategory = category,
                activeCategoryStreams = if (clearExisting) emptyList() else it.activeCategoryStreams,
                isLoadingCategoryStreams = true
            )
        }
        categoryStreamsJob = viewModelScope.launch {
            val streams = runCatching { fetchCategoryStreams(category.id, CATEGORY_DETAIL_STREAM_LIMIT) }
                .getOrElse { error ->
                    if (error is CancellationException) throw error
                    emptyList()
                }
                .sortedByDescending { it.viewerCount }
            val users = streams
                .map { it.userLogin }
                .chunked(100)
                .map { batch ->
                    runCatching { helixApi.getUsersByLogin(batch) }.getOrElse { emptyList() }
                }
                .flatten()
                .associateBy { it.login }
            val channels = streams.map { stream ->
                Channel(
                    id = stream.userId,
                    login = stream.userLogin,
                    displayName = stream.userName,
                    isLive = true,
                    viewerCount = stream.viewerCount,
                    gameName = stream.gameName,
                    title = stream.title,
                    thumbnailUrl = thumbnailUrl(stream.thumbnailUrl),
                    profileImageUrl = users[stream.userLogin]?.profileImageUrl,
                    isPartner = users[stream.userLogin]?.broadcasterType == "partner"
                )
            }
            val sampledViewerCount = streams.sumOf { it.viewerCount }
            val updatedCategory = category.takeIf { it.viewerCount > 0 || sampledViewerCount <= 0 }
                ?: category.copy(viewerCount = sampledViewerCount)
            _uiState.update {
                it.copy(
                    activeCategory = updatedCategory,
                    topCategories = if (updatedCategory == category) {
                        it.topCategories
                    } else {
                        it.topCategories.map { existing ->
                            if (existing.id == updatedCategory.id) updatedCategory else existing
                        }
                    },
                    activeCategoryStreams = channels,
                    isLoadingCategoryStreams = false
                )
            }
        }
    }

    private suspend fun fetchCategoryStreams(gameId: String, maxStreams: Int): List<HelixStreamDto> {
        val streams = mutableListOf<HelixStreamDto>()
        var cursor: String? = null
        do {
            val remaining = maxStreams - streams.size
            val page = helixApi.getStreamsByGameIdPaged(
                gameId = gameId,
                limit = remaining.coerceIn(1, 100),
                after = cursor
            )
            streams.addAll(page.streams)
            cursor = page.nextCursor
        } while (cursor != null && streams.size < maxStreams)
        return streams
    }

    fun closeCategory() {
        categoryStreamsJob?.cancel()
        _uiState.update {
            it.copy(
                activeCategory = null,
                activeCategoryStreams = emptyList(),
                isLoadingCategoryStreams = false
            )
        }
    }

    companion object {
        private const val MAX_FOLLOWS = 1000
        private const val CATEGORY_COUNT_STREAM_SAMPLE = 100
        private const val CATEGORY_COUNT_SAMPLE_GAME_LIMIT = 6
        private const val CATEGORY_DETAIL_STREAM_LIMIT = 100
        private const val FOLLOW_LIST_TTL_MILLIS = 30 * 60 * 1000L
        private const val BACKGROUND_REFRESH_DELAY_MILLIS = 350L
        private const val PINNED_HYDRATION_DELAY_MILLIS = 500L
        private const val ANON_KEY = "anon"
    }
}
