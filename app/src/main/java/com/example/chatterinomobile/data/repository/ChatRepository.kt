package com.example.chatterinomobile.data.repository

import com.example.chatterinomobile.data.model.ChannelHydrationState
import com.example.chatterinomobile.data.model.ChatMessage
import com.example.chatterinomobile.data.model.ModerationEvent
import com.example.chatterinomobile.data.model.RoomState
import com.example.chatterinomobile.data.model.SendMessageResult
import com.example.chatterinomobile.data.model.UserChatState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ChatRepository {

    val messages: Flow<ChatMessage>

    val moderationEvents: Flow<ModerationEvent>

    val roomStates: StateFlow<Map<String, RoomState>>

    val globalUserState: StateFlow<UserChatState?>

    val channelUserStates: StateFlow<Map<String, UserChatState>>

    val channelHydrationStates: StateFlow<Map<String, ChannelHydrationState>>

    suspend fun connect()

    suspend fun joinChannel(channelLogin: String)

    suspend fun leaveChannel(channelLogin: String)

    suspend fun sendMessage(channelLogin: String, text: String, replyParentMessageId: String? = null): SendMessageResult

    suspend fun disconnect()
}
