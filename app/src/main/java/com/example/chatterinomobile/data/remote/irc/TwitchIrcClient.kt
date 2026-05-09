package com.example.chatterinomobile.data.remote.irc

import android.util.Log
import com.example.chatterinomobile.data.repository.AuthRepository
import com.example.chatterinomobile.data.model.SendMessageResult
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

class TwitchIrcClient(
    private val httpClient: HttpClient,
    private val authRepository: AuthRepository
) {

    private val _incoming = MutableSharedFlow<IrcMessage>(
        replay = 0,
        extraBufferCapacity = INCOMING_BUFFER_CAPACITY
    )
    val incoming: Flow<IrcMessage> = _incoming.asSharedFlow()

    private val _disconnects = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val disconnects: Flow<Unit> = _disconnects.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateMutex = Mutex()

    private var session: DefaultClientWebSocketSession? = null
    private var readerJob: Job? = null
    private var connectedAccessToken: String? = null
    private var connectedNick: String? = null
    private val joinedChannels = HashSet<String>()

    suspend fun connect() {
        val auth = currentAuth()
        stateMutex.withLock {
            if (session != null && isConnectionCurrent(auth)) return

            openSessionLocked(auth)
        }
    }

    private suspend fun readLoop(ws: DefaultClientWebSocketSession) {
        try {
            while (scope.isActive) {
                val frame = ws.incoming.receive()
                if (frame !is Frame.Text) continue
                val text = frame.readText()

                for (line in text.split("\r\n")) {
                    if (line.isEmpty()) continue
                    val parsed = IrcParser.parse(line) ?: continue
                    if (parsed.command == "PING") {

                        val pongTarget = parsed.trailing ?: "tmi.twitch.tv"
                        ws.send("PONG :$pongTarget")
                        continue
                    }
                    if (parsed.command == "CLEARMSG" || parsed.command == "CLEARCHAT") {
                        Log.d("ChatMod", "irc frame: $line")
                    }
                    if (parsed.command == "USERSTATE" || parsed.command == "GLOBALUSERSTATE" ||
                        parsed.command == "NOTICE" || parsed.command == "001" ||
                        parsed.command == "CAP" || parsed.command == "RECONNECT"
                    ) {
                        Log.d("TwitchIrc", "frame: $line")
                    }
                    _incoming.emit(parsed)
                }
            }
        } catch (t: CancellationException) {

            throw t
        } catch (_: Throwable) {

            if (session === ws) {
                session = null
                _disconnects.tryEmit(Unit)
            }
        }
    }

    suspend fun joinChannel(login: String) {
        val normalized = login.lowercase().removePrefix("#")
        val auth = currentAuth()
        stateMutex.withLock {
            val wasAdded = joinedChannels.add(normalized)
            if (session != null && !isConnectionCurrent(auth)) {
                openSessionLocked(auth)
            } else if (wasAdded) {
                session?.send("JOIN #$normalized")
            }
        }
    }

    suspend fun prepareToReceiveOwnMessages(channelLogin: String): SendMessageResult {
        val auth = currentAuth()
        if (auth.accessToken == null) return SendMessageResult.Anonymous
        val normalized = channelLogin.lowercase().removePrefix("#")
        stateMutex.withLock {
            val wasAdded = joinedChannels.add(normalized)
            if (session == null || !isConnectionCurrent(auth)) {
                openSessionLocked(auth)
            } else if (wasAdded) {
                session?.send("JOIN #$normalized")
            }
        }
        return SendMessageResult.Sent
    }

    suspend fun partChannel(login: String) {
        val normalized = login.lowercase().removePrefix("#")
        stateMutex.withLock {
            if (!joinedChannels.remove(normalized)) return
            session?.send("PART #$normalized")
        }
    }

    suspend fun sendMessage(channelLogin: String, text: String): SendMessageResult {
        if (text.isBlank()) return SendMessageResult.EmptyMessage
        val prepareResult = prepareToReceiveOwnMessages(channelLogin)
        if (prepareResult != SendMessageResult.Sent) return prepareResult
        val channel = channelLogin.lowercase().removePrefix("#")
        val ws = stateMutex.withLock { session } ?: return SendMessageResult.Disconnected
        return runCatching {
            ws.send("PRIVMSG #$channel :$text")
            SendMessageResult.Sent
        }.getOrElse { throwable ->
            SendMessageResult.Failed(throwable.message ?: "Failed to send message")
        }
    }

    suspend fun disconnect() {
        stateMutex.withLock {
            readerJob?.cancel()
            readerJob = null
            try {
                session?.close()
            } catch (_: Throwable) {

            }
            session = null
            connectedAccessToken = null
            connectedNick = null
        }
    }

    private suspend fun openSessionLocked(auth: ConnectionAuth) {
        readerJob?.cancel()
        readerJob = null
        try {
            session?.close()
        } catch (_: Throwable) {

        }
        session = null
        connectedAccessToken = null
        connectedNick = null

        val ws = httpClient.webSocketSession(urlString = WS_URL)
        session = ws
        connectedAccessToken = auth.accessToken
        connectedNick = auth.nick

        Log.d("TwitchIrc", "connecting authenticated=${auth.accessToken != null} nick=${auth.nick}")
        ws.send("CAP REQ :twitch.tv/tags twitch.tv/commands twitch.tv/membership")
        if (auth.accessToken != null) ws.send("PASS oauth:${auth.accessToken}")
        ws.send("NICK ${auth.nick}")

        for (channel in joinedChannels) {
            ws.send("JOIN #$channel")
        }

        readerJob = scope.launch { readLoop(ws) }
    }

    private suspend fun currentAuth(): ConnectionAuth {
        val token = authRepository.getAccessToken()
        val nick = if (token != null) {
            authRepository.getLogin()?.lowercase() ?: anonymousNick()
        } else {
            anonymousNick()
        }
        return ConnectionAuth(accessToken = token, nick = nick)
    }

    private fun isConnectionCurrent(auth: ConnectionAuth): Boolean {
        if (auth.accessToken == null) return connectedAccessToken == null
        return connectedAccessToken == auth.accessToken && connectedNick == auth.nick
    }

    private fun anonymousNick(): String =
        "justinfan${Random.nextInt(10_000, 99_999)}"

    private data class ConnectionAuth(
        val accessToken: String?,
        val nick: String
    )

    companion object {
        private const val WS_URL = "wss://irc-ws.chat.twitch.tv:443"
        private const val INCOMING_BUFFER_CAPACITY = 4_096
    }
}
