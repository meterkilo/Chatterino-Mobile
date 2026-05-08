    package com.example.chatterinomobile.ui.chat

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatterinomobile.data.model.ChatMessage
import com.example.chatterinomobile.data.model.ModerationEvent
import com.example.chatterinomobile.data.model.Paint
import com.example.chatterinomobile.data.model.ReplyMetadata
import com.example.chatterinomobile.data.model.SendMessageResult
import com.example.chatterinomobile.data.repository.ChatRepository
import com.example.chatterinomobile.data.repository.PaintRepository
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.collections.immutable.toPersistentHashMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.launch

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val paintRepository: PaintRepository
) : ViewModel() {

    val recentMessages: SnapshotStateList<ChatMessage> = mutableStateListOf()

    private val _uiState = MutableStateFlow(
        ChatUiState(paintsByUserId = paintRepository.snapshot().toPersistentHashMap())
    )
    val uiState = _uiState.asStateFlow()

    private var liveCollector: Job? = null
    private var moderationCollector: Job? = null

    init {
        moderationCollector = viewModelScope.launch {
            chatRepository.moderationEvents.collect { event ->
                val state = _uiState.value
                val active = state.activeChannelLogin
                Log.d(MOD_LOG_TAG, "received event=$event active=$active")
                if (active == null) return@collect
                if (!event.channelLogin.equals(active, ignoreCase = true)) {
                    Log.d(MOD_LOG_TAG, "dropped (channel mismatch) event.channel=${event.channelLogin} active=$active")
                    return@collect
                }
                when (event) {
                    is ModerationEvent.ChatCleared -> update {
                        copy(deletedIds = persistentHashSetOf(), bannedLogins = persistentHashSetOf())
                    }
                    is ModerationEvent.MessageDeleted -> {
                        Log.d(MOD_LOG_TAG, "marking deleted id=${event.targetMessageId}")
                        update { copy(deletedIds = deletedIds.add(event.targetMessageId)) }
                    }
                    is ModerationEvent.UserBanned -> update {
                        copy(bannedLogins = bannedLogins.add(event.targetLogin))
                    }
                    is ModerationEvent.UserTimedOut -> update {
                        copy(bannedLogins = bannedLogins.add(event.targetLogin))
                    }
                }
            }
        }

        viewModelScope.launch {
            paintRepository.paintAssignments.collect { assignment ->
                update {
                    copy(paintsByUserId = paintsByUserId.put(assignment.twitchUserId, assignment.paint))
                }
            }
        }
    }

    fun setActiveChannel(channelLogin: String?, channelId: String? = null) {
        val state = _uiState.value
        if (state.activeChannelLogin == channelLogin && state.activeChannelId == channelId) return

        val isChannelSwitch = state.activeChannelLogin != channelLogin
        liveCollector?.cancel()

        if (isChannelSwitch) recentMessages.clear()

        update {
            copy(
                activeChannelLogin = channelLogin,
                activeChannelId = channelId,
                deletedIds = if (isChannelSwitch) persistentHashSetOf() else deletedIds,
                bannedLogins = if (isChannelSwitch) persistentHashSetOf() else bannedLogins,
                sendStatusMessage = null,
                sendErrorMessage = null
            )
        }

        if (channelLogin == null) return

        liveCollector = viewModelScope.launch {
            chatRepository.messages.buffer(MESSAGE_UI_BUFFER_CAPACITY).collect { message ->
                val current = _uiState.value
                val active = current.activeChannelLogin ?: return@collect
                if (!message.belongsTo(active, current.activeChannelId)) return@collect
                appendMessage(message)
            }
        }
    }

    fun stopActiveChannel() {
        liveCollector?.cancel()
        liveCollector = null
        recentMessages.clear()
        update {
            copy(
                activeChannelLogin = null,
                activeChannelId = null,
                deletedIds = persistentHashSetOf(),
                bannedLogins = persistentHashSetOf(),
                sendStatusMessage = null,
                sendErrorMessage = null
            )
        }
    }

    fun sendMessage(text: String) {
        val active = _uiState.value.activeChannelLogin ?: run {
            update { copy(sendErrorMessage = "Join a channel first.") }
            return
        }

        viewModelScope.launch {
            when (val result = chatRepository.sendMessage(active, text)) {
                SendMessageResult.Sent -> update {
                    copy(sendStatusMessage = "Message sent.", sendErrorMessage = null)
                }
                SendMessageResult.EmptyMessage -> update {
                    copy(sendErrorMessage = "Message cannot be empty.")
                }
                SendMessageResult.Anonymous -> update {
                    copy(sendErrorMessage = "Sign in to send chat messages.")
                }
                SendMessageResult.Disconnected -> update {
                    copy(sendErrorMessage = "Chat socket is disconnected.")
                }
                is SendMessageResult.Failed -> update {
                    copy(sendErrorMessage = result.message)
                }
            }
        }
    }

    fun consumeSendMessages() {
        update { copy(sendStatusMessage = null, sendErrorMessage = null) }
    }

    private fun appendMessage(message: ChatMessage) {
        if (recentMessages.size >= MAX_RECENT_MESSAGES) {
            recentMessages.removeAt(0)
        }
        recentMessages.add(message)
    }

    private inline fun update(transform: ChatUiState.() -> ChatUiState) {
        _uiState.value = _uiState.value.transform()
    }

    private fun ChatMessage.belongsTo(activeLogin: String, activeId: String?): Boolean =
        channelId == activeLogin || (activeId != null && channelId == activeId)

    companion object {
        private const val MAX_RECENT_MESSAGES = 1_000
        private const val MESSAGE_UI_BUFFER_CAPACITY = 4_096
        private const val MOD_LOG_TAG = "ChatMod"
    }
}

@Immutable
data class ChatUiState(
    val activeChannelLogin: String? = null,
    val activeChannelId: String? = null,
    val deletedIds: PersistentSet<String> = persistentHashSetOf(),
    val bannedLogins: PersistentSet<String> = persistentHashSetOf(),
    val paintsByUserId: PersistentMap<String, Paint> = persistentHashMapOf(),
    val sendStatusMessage: String? = null,
    val sendErrorMessage: String? = null
)

fun ReplyMetadata.describeParent(): String =
    parentDisplayName ?: parentUserLogin ?: parentMessageId
