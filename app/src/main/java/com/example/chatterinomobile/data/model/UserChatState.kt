package com.example.chatterinomobile.data.model

data class UserChatState(
    val userId: String?,
    val login: String?,
    val displayName: String?,
    val color: String?,
    val badges: List<Badge>,
    val emoteSetIds: List<String>,
    val isModerator: Boolean = false,
    val isSubscriber: Boolean = false,
    val channelId: String? = null,
    val channelLogin: String? = null
)
