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
    private val joinedChannels = HashSet<String>()

    suspend fun connect() {
        stateMutex.withLock {
            if (session != null) return
            val token = authRepository.getAccessToken()
            val nick = if (token != null) {
                authRepository.getUserId()?.let { "user_$it" } ?: anonymousNick()
            } else {
                anonymousNick()
            }

            val ws = httpClient.webSocketSession(urlString = WS_URL)
            session = ws

            ws.send("CAP REQ :twitch.tv/tags twitch.tv/commands twitch.tv/membership")
            if (token != null) ws.send("PASS oauth:$token")
            ws.send("NICK $nick")

            for (channel in joinedChannels) {
                ws.send("JOIN #$channel")
            }

            readerJob = scope.launch { readLoop(ws) }
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
        stateMutex.withLock {
            if (!joinedChannels.add(normalized)) return
            session?.send("JOIN #$normalized")
        }
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
        if (authRepository.getAccessToken() == null) return SendMessageResult.Anonymous
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
        }
    }

    private fun anonymousNick(): String =
        "justinfan${Random.nextInt(10_000, 99_999)}"

    companion object {
        private const val WS_URL = "wss://irc-ws.chat.twitch.tv:443"
        private const val INCOMING_BUFFER_CAPACITY = 4_096
    }
}
