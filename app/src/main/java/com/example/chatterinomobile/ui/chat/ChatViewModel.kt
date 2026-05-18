    package com.example.chatterinomobile.ui.chat

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatterinomobile.data.model.ChatMessage
import com.example.chatterinomobile.data.model.Emote
import com.example.chatterinomobile.data.model.MessageFragment
import com.example.chatterinomobile.data.model.MessageType
import com.example.chatterinomobile.data.model.ModerationEvent
import com.example.chatterinomobile.data.model.Paint
import com.example.chatterinomobile.data.model.ReplyMetadata
import com.example.chatterinomobile.data.model.SendMessageResult
import com.example.chatterinomobile.data.repository.ChatRepository
import com.example.chatterinomobile.data.repository.EmoteCatalog
import com.example.chatterinomobile.data.repository.EmoteRepository
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
    private val paintRepository: PaintRepository,
    private val emoteRepository: EmoteRepository
) : ViewModel() {

    val recentMessages: SnapshotStateList<ChatMessage> = mutableStateListOf()

    private val _uiState = MutableStateFlow(
        ChatUiState(paintsByUserId = paintRepository.snapshot().toPersistentHashMap())
    )
    val uiState = _uiState.asStateFlow()

    private val _emoteCatalog = MutableStateFlow(EmoteCatalog.EMPTY)
    val emoteCatalog = _emoteCatalog.asStateFlow()

    private val _autocomplete = MutableStateFlow<EmoteAutocompleteState>(EmoteAutocompleteState.Hidden)
    val autocomplete = _autocomplete.asStateFlow()

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
                pendingReply = if (isChannelSwitch) null else pendingReply,
                sendStatusMessage = null,
                sendErrorMessage = null
            )
        }

        refreshEmoteCatalog()

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
        _autocomplete.value = EmoteAutocompleteState.Hidden
        update {
            copy(
                activeChannelLogin = null,
                activeChannelId = null,
                deletedIds = persistentHashSetOf(),
                bannedLogins = persistentHashSetOf(),
                pendingReply = null,
                sendStatusMessage = null,
                sendErrorMessage = null
            )
        }
    }

    fun beginReply(message: ChatMessage) {
        if (message.Type is MessageType.System) return
        update {
            copy(
                pendingReply = ReplyMetadata(
                    parentMessageId = message.id,
                    parentUserId = message.author.id,
                    parentUserLogin = message.author.login,
                    parentDisplayName = message.author.displayName,
                    parentBody = message.fragment.toPlainText().take(MAX_REPLY_PREVIEW_LENGTH)
                ),
                sendStatusMessage = null,
                sendErrorMessage = null
            )
        }
    }

    fun cancelReply() {
        update { copy(pendingReply = null) }
    }

    fun sendMessage(text: String) {
        val state = _uiState.value
        val active = state.activeChannelLogin ?: run {
            update { copy(sendErrorMessage = "Join a channel first.") }
            return
        }

        viewModelScope.launch {
            when (val result = chatRepository.sendMessage(active, text, state.pendingReply?.parentMessageId)) {
                SendMessageResult.Sent -> update {
                    copy(pendingReply = null, sendStatusMessage = null, sendErrorMessage = null)
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

    fun onAutocompleteQuery(
        query: AutocompleteQuery?,
        broadcasterLogin: String? = _uiState.value.activeChannelLogin,
        broadcasterDisplayName: String? = null
    ) {
        if (query == null) {
            if (_autocomplete.value !is EmoteAutocompleteState.Hidden) {
                _autocomplete.value = EmoteAutocompleteState.Hidden
            }
            return
        }

        when (query.kind) {
            AutocompleteKind.Emote -> {
                val channelId = _uiState.value.activeChannelId
                val results = if (query.text.length < MIN_AUTOCOMPLETE_QUERY_LEN) {
                    emptyList()
                } else {
                    emoteRepository.searchByPrefix(query.text, channelId, AUTOCOMPLETE_LIMIT)
                }
                _autocomplete.value = EmoteAutocompleteState.Emotes(query = query.text, results = results)
            }
            AutocompleteKind.User -> {
                val results = searchUserSuggestions(
                    query = query.text,
                    broadcasterLogin = broadcasterLogin,
                    broadcasterDisplayName = broadcasterDisplayName
                )
                _autocomplete.value = EmoteAutocompleteState.Users(query = query.text, results = results)
            }
        }
    }

    fun refreshEmoteCatalog() {
        val channelId = _uiState.value.activeChannelId
        _emoteCatalog.value = emoteRepository.listEmotesForChannel(channelId)
    }

    private fun appendMessage(message: ChatMessage) {
        if (recentMessages.any { it.id == message.id }) return

        val insertIndex = recentMessages.indexOfFirst { it.timestamp > message.timestamp }
            .takeIf { it >= 0 }
            ?: recentMessages.size
        recentMessages.add(insertIndex, message)

        if (recentMessages.size > MAX_RECENT_MESSAGES) {
            recentMessages.removeAt(0)
        }
    }

    private inline fun update(transform: ChatUiState.() -> ChatUiState) {
        _uiState.value = _uiState.value.transform()
    }

    private fun ChatMessage.belongsTo(activeLogin: String, activeId: String?): Boolean =
        channelId == activeLogin || (activeId != null && channelId == activeId)

    private fun List<MessageFragment>.toPlainText(): String =
        joinToString(separator = "") { fragment ->
            when (fragment) {
                is MessageFragment.Text -> fragment.content
                is MessageFragment.Emote -> fragment.name
                is MessageFragment.Mention -> "@${fragment.username}"
            }
        }.trim()

    private fun searchUserSuggestions(
        query: String,
        broadcasterLogin: String?,
        broadcasterDisplayName: String?
    ): List<UserAutocompleteSuggestion> {
        val needle = query.lowercase()
        val byLogin = LinkedHashMap<String, UserAutocompleteSuggestion>()

        fun addSuggestion(login: String?, displayName: String?) {
            val normalizedLogin = login?.takeIf { it.isNotBlank() } ?: return
            val normalizedDisplay = displayName?.takeIf { it.isNotBlank() } ?: normalizedLogin
            byLogin.putIfAbsent(
                normalizedLogin.lowercase(),
                UserAutocompleteSuggestion(
                    login = normalizedLogin,
                    displayName = normalizedDisplay
                )
            )
        }

        addSuggestion(broadcasterLogin, broadcasterDisplayName)
        for (message in recentMessages.asReversed()) {
            addSuggestion(message.author.login, message.author.displayName)
        }

        return byLogin.values
            .asSequence()
            .filter { suggestion ->
                needle.isEmpty() ||
                    suggestion.login.contains(needle, ignoreCase = true) ||
                    suggestion.displayName.contains(needle, ignoreCase = true)
            }
            .sortedWith(
                compareBy<UserAutocompleteSuggestion> { scoreUserSuggestion(it, query, needle) }
                    .thenBy { it.displayName.lowercase() }
            )
            .take(USER_AUTOCOMPLETE_LIMIT)
            .toList()
    }

    private fun scoreUserSuggestion(
        suggestion: UserAutocompleteSuggestion,
        query: String,
        lowerQuery: String
    ): Int {
        if (lowerQuery.isEmpty()) return 0
        val login = suggestion.login
        val display = suggestion.displayName
        val lowerLogin = login.lowercase()
        val lowerDisplay = display.lowercase()
        return when {
            login == query || display == query -> -20
            lowerLogin == lowerQuery || lowerDisplay == lowerQuery -> -10
            login.startsWith(query, ignoreCase = false) ||
                display.startsWith(query, ignoreCase = false) -> 0
            lowerLogin.startsWith(lowerQuery) || lowerDisplay.startsWith(lowerQuery) -> 500
            else -> {
                val loginIndex = lowerLogin.indexOf(lowerQuery).takeIf { it >= 0 } ?: Int.MAX_VALUE / 4
                val displayIndex = lowerDisplay.indexOf(lowerQuery).takeIf { it >= 0 } ?: Int.MAX_VALUE / 4
                10_000 + minOf(loginIndex, displayIndex) * 100 + minOf(login.length, display.length)
            }
        }
    }

    companion object {
        private const val MAX_RECENT_MESSAGES = 1_000
        private const val MESSAGE_UI_BUFFER_CAPACITY = 4_096
        private const val MOD_LOG_TAG = "ChatMod"
        private const val MIN_AUTOCOMPLETE_QUERY_LEN = 1
        private const val AUTOCOMPLETE_LIMIT = 30
        private const val USER_AUTOCOMPLETE_LIMIT = 30
        private const val MAX_REPLY_PREVIEW_LENGTH = 140
    }
}

@Immutable
data class ChatUiState(
    val activeChannelLogin: String? = null,
    val activeChannelId: String? = null,
    val deletedIds: PersistentSet<String> = persistentHashSetOf(),
    val bannedLogins: PersistentSet<String> = persistentHashSetOf(),
    val paintsByUserId: PersistentMap<String, Paint> = persistentHashMapOf(),
    val pendingReply: ReplyMetadata? = null,
    val sendStatusMessage: String? = null,
    val sendErrorMessage: String? = null
)

@Immutable
sealed interface EmoteAutocompleteState {
    data object Hidden : EmoteAutocompleteState
    data class Emotes(val query: String, val results: List<Emote>) : EmoteAutocompleteState
    data class Users(val query: String, val results: List<UserAutocompleteSuggestion>) : EmoteAutocompleteState
}

@Immutable
data class AutocompleteQuery(
    val kind: AutocompleteKind,
    val text: String
)

@Immutable
enum class AutocompleteKind {
    Emote,
    User
}

@Immutable
data class UserAutocompleteSuggestion(
    val login: String,
    val displayName: String
)

fun ReplyMetadata.describeParent(): String =
    parentDisplayName ?: parentUserLogin ?: parentMessageId
