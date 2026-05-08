package com.example.chatterinomobile.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatterinomobile.data.local.PinnedChannelsStore
import com.example.chatterinomobile.data.model.ChannelHydrationState
import com.example.chatterinomobile.data.model.RoomState
import com.example.chatterinomobile.data.model.UserChatState
import com.example.chatterinomobile.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChannelTabsViewModel(
    private val chatRepository: ChatRepository,
    private val pinnedChannelsStore: PinnedChannelsStore
) : ViewModel() {

    private val _activeChannelLogin = MutableStateFlow<String?>(null)
    val activeChannelLogin: StateFlow<String?> = _activeChannelLogin.asStateFlow()

    private val _joinedChannels = MutableStateFlow<List<String>>(pinnedChannelsStore.read())
    val joinedChannels: StateFlow<List<String>> = _joinedChannels.asStateFlow()

    val activeChannel: StateFlow<ActiveChannelState> = combine(
        _activeChannelLogin,
        chatRepository.channelHydrationStates,
        chatRepository.roomStates,
        chatRepository.channelUserStates
    ) { login, hydration, rooms, users ->
        if (login == null) {
            ActiveChannelState()
        } else {
            val h = hydration[login]
            val channelId = h?.channelId
            ActiveChannelState(
                channelLogin = login,
                channel = h?.channel,
                hydration = h,
                roomState = channelId?.let(rooms::get) ?: rooms[login],
                userState = channelId?.let(users::get) ?: users[login]
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ActiveChannelState())

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {

        viewModelScope.launch {
            runCatching { chatRepository.connect() }
                .onFailure { _errorMessage.value = it.message ?: "Failed to connect chat" }
        }
    }

    fun joinChannel(channelLogin: String) {
        val normalized = channelLogin.lowercase().removePrefix("#").trim()
        if (normalized.isBlank()) return

        if (normalized !in _joinedChannels.value) {
            updateJoined(_joinedChannels.value + normalized)
        }
        _activeChannelLogin.value = normalized
        _errorMessage.value = null

        viewModelScope.launch {
            runCatching { chatRepository.joinChannel(normalized) }
                .onFailure {
                    _errorMessage.value = it.message ?: "Failed to join channel"

                    updateJoined(_joinedChannels.value - normalized)
                    if (_activeChannelLogin.value == normalized) {
                        _activeChannelLogin.value = _joinedChannels.value.lastOrNull()
                    }
                }
        }
    }

    private fun updateJoined(next: List<String>) {
        _joinedChannels.value = next
        pinnedChannelsStore.write(next)
    }

    fun selectChannel(channelLogin: String) {
        if (channelLogin in _joinedChannels.value) {
            _activeChannelLogin.value = channelLogin
        }
    }

    fun stopActiveChannel() {
        val active = _activeChannelLogin.value ?: return
        _activeChannelLogin.value = null

        viewModelScope.launch {
            runCatching { chatRepository.leaveChannel(active) }
                .onFailure { _errorMessage.value = it.message ?: "Failed to stop chat" }
        }
    }

    fun leaveChannel(channelLogin: String) {
        val normalized = channelLogin.lowercase().removePrefix("#").trim()
        if (normalized.isBlank()) return

        val remaining = _joinedChannels.value - normalized
        updateJoined(remaining)
        if (_activeChannelLogin.value == normalized) {
            _activeChannelLogin.value = remaining.lastOrNull()
        }

        viewModelScope.launch {
            runCatching { chatRepository.leaveChannel(normalized) }
                .onFailure { _errorMessage.value = it.message ?: "Failed to leave channel" }
        }
    }

    fun consumeError() {
        _errorMessage.value = null
    }
}

data class ActiveChannelState(
    val channelLogin: String? = null,
    val channel: com.example.chatterinomobile.data.model.Channel? = null,
    val hydration: ChannelHydrationState? = null,
    val roomState: RoomState? = null,
    val userState: UserChatState? = null
)
