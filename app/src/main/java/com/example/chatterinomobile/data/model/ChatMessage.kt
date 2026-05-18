package com.example.chatterinomobile.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val id: String,
    val channelId: String,
    val author : ChatUser,
    val reply: ReplyMetadata? = null,
    val fragment: List<MessageFragment>,
    val badges: List<Badge> = emptyList(),
    val timestamp: Long,
    val isHistorical: Boolean = false,
    val Type: MessageType = MessageType.Regular
)

@Serializable
sealed class MessageType {

    @Serializable
    data object Regular : MessageType()

    @Serializable
    data object Action: MessageType()

    @Serializable
    data object Highlighted: MessageType()

    @Serializable
    data class Reply(val parentId : String) : MessageType()

    @Serializable
    data class System(val text: String) : MessageType()
}
