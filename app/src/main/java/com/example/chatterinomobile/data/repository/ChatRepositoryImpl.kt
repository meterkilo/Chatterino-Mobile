package com.example.chatterinomobile.data.repository

import com.example.chatterinomobile.data.model.ChatMessage
import com.example.chatterinomobile.data.model.Channel
import com.example.chatterinomobile.data.model.ChannelHydrationState
import com.example.chatterinomobile.data.model.ModerationEvent
import com.example.chatterinomobile.data.model.RoomState
import com.example.chatterinomobile.data.model.SendMessageResult
import com.example.chatterinomobile.data.model.UserChatState
import com.example.chatterinomobile.data.remote.irc.IrcMessageMapper
import com.example.chatterinomobile.data.remote.irc.MessageEnricher
import com.example.chatterinomobile.data.remote.irc.ModerationEventMapper
import com.example.chatterinomobile.data.remote.irc.RoomStateMapper
import com.example.chatterinomobile.data.remote.irc.TwitchIrcClient
import com.example.chatterinomobile.data.remote.irc.UserStateMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ChatRepositoryImpl(
    private val ircClient: TwitchIrcClient,
    private val mapper: IrcMessageMapper,
    private val moderationMapper: ModerationEventMapper,
    private val roomStateMapper: RoomStateMapper,
    private val userStateMapper: UserStateMapper,
    private val enricher: MessageEnricher,
    private val channelRepository: ChannelRepository,
    private val badgeRepository: BadgeRepository,
    private val emoteRepository: EmoteRepository
) : ChatRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val globalWarmupMutex = Mutex()
    private val connectMutex = Mutex()
    private val reconnectMutex = Mutex()
    private val joinedChannelMutex = Mutex()
    private val sendRateLimitMutex = Mutex()
    @Volatile
    private var globalsLoaded = false
    @Volatile
    private var shouldReconnect = false
    private val joinedChannelLogins = LinkedHashSet<String>()
    private val channelCacheByLogin = HashMap<String, Channel>()
    private val hydrationJobsByLogin = HashMap<String, Job>()
    private val requestedThirdPartyBadgeUsers = HashSet<String>()
    private val sendTimestampsByChannel = HashMap<String, ArrayDeque<Long>>()

    private val _roomStates = MutableStateFlow<Map<String, RoomState>>(emptyMap())
    override val roomStates: StateFlow<Map<String, RoomState>> = _roomStates.asStateFlow()

    private val _globalUserState = MutableStateFlow<UserChatState?>(null)
    override val globalUserState: StateFlow<UserChatState?> = _globalUserState.asStateFlow()

    private val _channelUserStates = MutableStateFlow<Map<String, UserChatState>>(emptyMap())
    override val channelUserStates: StateFlow<Map<String, UserChatState>> =
        _channelUserStates.asStateFlow()

    private val _channelHydrationStates = MutableStateFlow<Map<String, ChannelHydrationState>>(emptyMap())
    override val channelHydrationStates: StateFlow<Map<String, ChannelHydrationState>> =
        _channelHydrationStates.asStateFlow()

    init {
        scope.launch {
            ircClient.incoming.collect { raw ->
                roomStateMapper.map(raw)?.let { state ->
                    _roomStates.value = _roomStates.value + mapOf(
                        state.channelId to state,
                        state.channelLogin to state
                    )
                }

                userStateMapper.map(raw)?.let { state ->
                    if (state.channelId == null && state.channelLogin == null) {
                        _globalUserState.value = state
                    } else {
                        val updates = buildMap {
                            state.channelId?.let { put(it, state) }
                            state.channelLogin?.let { put(it, state) }
                        }
                        if (updates.isNotEmpty()) {
                            _channelUserStates.value = _channelUserStates.value + updates
                        }
                    }
                }
            }
        }

        scope.launch {
            ircClient.disconnects.collect {
                if (shouldReconnect) reconnectLoop()
            }
        }
    }

    override val messages: Flow<ChatMessage> =
        ircClient.incoming
            .buffer(MESSAGE_PIPELINE_BUFFER_CAPACITY)
            .mapNotNull { raw ->
                mapper.map(raw)
            }
            .map { msg ->
                enricher.requestCosmetics(msg)
                if (shouldRequestThirdPartyBadges(msg.author.id)) {
                    scope.launch {
                        runCatching { enricher.loadThirdPartyBadges(msg) }
                    }
                }
                enricher.enrichFromCache(msg)
            }
            .shareIn(scope, SharingStarted.Eagerly, replay = 0)

    override val moderationEvents: Flow<ModerationEvent> =
        ircClient.incoming
            .mapNotNull { raw -> moderationMapper.map(raw) }
            .shareIn(scope, SharingStarted.Eagerly, replay = 0)

    override suspend fun connect() {
        shouldReconnect = true
        connectMutex.withLock {
            ircClient.connect()
        }
        ensureGlobalCachesLoadedAsync()
    }

    override suspend fun joinChannel(channelLogin: String) {
        val normalizedLogin = channelLogin.lowercase().removePrefix("#")
        joinedChannelMutex.withLock {
            joinedChannelLogins.add(normalizedLogin)
        }
        _channelHydrationStates.value = _channelHydrationStates.value + (
            normalizedLogin to ChannelHydrationState(
                channelLogin = normalizedLogin,
                isLoading = true,
                isReady = false,
                errorMessage = null
            )
        )
        ircClient.joinChannel(normalizedLogin)
        ensureGlobalCachesLoadedAsync()
        hydrateChannelCachesAsync(normalizedLogin)
    }

    override suspend fun leaveChannel(channelLogin: String) {
        val normalizedLogin = channelLogin.lowercase().removePrefix("#")
        joinedChannelMutex.withLock {
            joinedChannelLogins.remove(normalizedLogin)
        }
        cancelChannelHydration(normalizedLogin)
        ircClient.partChannel(normalizedLogin)
    }

    override suspend fun sendMessage(channelLogin: String, text: String): SendMessageResult {
        val normalizedLogin = channelLogin.lowercase().removePrefix("#")
        if (text.isBlank()) return SendMessageResult.EmptyMessage
        awaitSendPermit(normalizedLogin)
        return ircClient.sendMessage(normalizedLogin, text)
    }

    override suspend fun disconnect() {
        shouldReconnect = false
        ircClient.disconnect()
    }

    private suspend fun ensureGlobalCachesLoaded() {
        if (globalsLoaded) return
        globalWarmupMutex.withLock {
            if (globalsLoaded) return
            coroutineScope {
                val badges = async { badgeRepository.loadGlobalBadges() }
                val emotes = async { emoteRepository.loadEmotesForChannel(channelId = null) }
                badges.await()
                emotes.await()
            }
            globalsLoaded = true
        }
    }

    private fun ensureGlobalCachesLoadedAsync() {
        if (globalsLoaded) return
        scope.launch {
            runCatching { ensureGlobalCachesLoaded() }
        }
    }

    private suspend fun reconnectLoop() {
        reconnectMutex.withLock {
            var attempt = 0
            while (shouldReconnect) {
                val result = runCatching {
                    connectMutex.withLock {
                        ircClient.connect()
                    }
                    rejoinTrackedChannels()
                }

                if (result.isSuccess) return

                val backoffMillis =
                    (1_000L * (1L shl attempt.coerceAtMost(5))).coerceAtMost(30_000L)
                delay(backoffMillis)
                attempt++
            }
        }
    }

    private suspend fun rejoinTrackedChannels() {
        val channels = joinedChannelMutex.withLock { joinedChannelLogins.toList() }
        for (channelLogin in channels) {
            ircClient.joinChannel(channelLogin)
            hydrateChannelCachesAsync(channelLogin)
        }
    }

    private fun hydrateChannelCachesAsync(channelLogin: String) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val channel = resolveChannel(channelLogin) ?: error("Channel not found")
                _channelHydrationStates.value = _channelHydrationStates.value + (
                    channelLogin to ChannelHydrationState(
                        channelLogin = channelLogin,
                        channelId = channel.id,
                        channel = channel,
                        isLoading = true,
                        isReady = false,
                        errorMessage = null
                    )
                )

                coroutineScope {
                    val badges = async { badgeRepository.loadChannelBadges(channel.id) }
                    val emotes = async { emoteRepository.loadEmotesForChannel(channel.id) }
                    badges.await()
                    emotes.await()
                }

                _channelHydrationStates.value = _channelHydrationStates.value + (
                    channelLogin to ChannelHydrationState(
                        channelLogin = channelLogin,
                        channelId = channel.id,
                        channel = channel,
                        isLoading = false,
                        isReady = true,
                        errorMessage = null
                    )
                )
            } catch (throwable: CancellationException) {
                throw throwable
            } catch (throwable: Throwable) {
                val channelId = channelCacheByLogin[channelLogin]?.id
                _channelHydrationStates.value = _channelHydrationStates.value + (
                    channelLogin to ChannelHydrationState(
                        channelLogin = channelLogin,
                        channelId = channelId,
                        channel = channelCacheByLogin[channelLogin],
                        isLoading = false,
                        isReady = false,
                        errorMessage = throwable.message ?: "Failed to hydrate channel"
                    )
                )
            } finally {
                synchronized(hydrationJobsByLogin) {
                    if (hydrationJobsByLogin[channelLogin] === this.coroutineContext[Job]) {
                        hydrationJobsByLogin.remove(channelLogin)
                    }
                }
            }
        }
        synchronized(hydrationJobsByLogin) {
            hydrationJobsByLogin.remove(channelLogin)?.cancel()
            hydrationJobsByLogin[channelLogin] = job
        }
        job.start()
    }

    private fun cancelChannelHydration(channelLogin: String) {
        synchronized(hydrationJobsByLogin) {
            hydrationJobsByLogin.remove(channelLogin)?.cancel()
        }
    }

    private fun shouldRequestThirdPartyBadges(twitchUserId: String): Boolean {
        if (twitchUserId.isBlank()) return false
        synchronized(requestedThirdPartyBadgeUsers) {
            return requestedThirdPartyBadgeUsers.add(twitchUserId)
        }
    }

    private suspend fun resolveChannel(channelLogin: String): Channel? {
        channelCacheByLogin[channelLogin]?.let { return it }
        val channel = channelRepository.getChannelByLogin(channelLogin) ?: return null
        channelCacheByLogin[channelLogin] = channel
        return channel
    }

    private suspend fun awaitSendPermit(channelLogin: String) {
        while (true) {
            val waitMillis = sendRateLimitMutex.withLock {
                val now = System.currentTimeMillis()
                val timestamps = sendTimestampsByChannel.getOrPut(channelLogin) { ArrayDeque() }
                while (timestamps.isNotEmpty() && now - timestamps.first() >= SEND_WINDOW_MILLIS) {
                    timestamps.removeFirst()
                }

                if (timestamps.size < MAX_MESSAGES_PER_WINDOW) {
                    timestamps.addLast(now)
                    0L
                } else {
                    val oldest = timestamps.first()
                    (SEND_WINDOW_MILLIS - (now - oldest)).coerceAtLeast(1L)
                }
            }

            if (waitMillis == 0L) return
            delay(waitMillis)
        }
    }

    companion object {
        private const val MAX_MESSAGES_PER_WINDOW = 20
        private const val SEND_WINDOW_MILLIS = 30_000L
        private const val MESSAGE_PIPELINE_BUFFER_CAPACITY = 4_096
    }
}
