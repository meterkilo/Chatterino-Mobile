package com.example.chatterinomobile.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HelixListResponse<T>(
    val data: List<T> = emptyList()
)

@Serializable
data class HelixUserDto(
    val id: String,
    val login: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("profile_image_url") val profileImageUrl: String? = null,
    @SerialName("broadcaster_type") val broadcasterType: String? = null,
    val description: String? = null
)

@Serializable
data class HelixBadgeSetDto(
    @SerialName("set_id") val setId: String,
    val versions: List<HelixBadgeVersionDto>
)

@Serializable
data class HelixBadgeVersionDto(
    val id: String,
    @SerialName("image_url_1x") val imageUrl1x: String,
    @SerialName("image_url_2x") val imageUrl2x: String,
    @SerialName("image_url_4x") val imageUrl4x: String,
    val title: String,
    val description: String,
    @SerialName("click_action") val clickAction: String? = null,
    @SerialName("click_url") val clickUrl: String? = null
)

@Serializable
data class HelixStreamDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("user_login") val userLogin: String,
    @SerialName("user_name") val userName: String,
    @SerialName("game_id") val gameId: String? = null,
    @SerialName("game_name") val gameName: String? = null,
    val type: String,
    val title: String,
    @SerialName("viewer_count") val viewerCount: Int,
    @SerialName("started_at") val startedAt: String,
    val language: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    val tags: List<String>? = null
)

@Serializable
data class HelixListResponseWithPagination<T>(
    val data: List<T> = emptyList(),
    val pagination: HelixPagination? = null,
    val total: Int? = null
)

@Serializable
data class HelixTotalResponse(
    val total: Int = 0
)

@Serializable
data class HelixPagination(
    val cursor: String? = null
)

@Serializable
data class HelixFollowedChannelDto(
    @SerialName("broadcaster_id") val broadcasterId: String,
    @SerialName("broadcaster_login") val broadcasterLogin: String,
    @SerialName("broadcaster_name") val broadcasterName: String,
    @SerialName("followed_at") val followedAt: String
)

@Serializable
data class HelixGameDto(
    val id: String,
    val name: String,
    @SerialName("box_art_url") val boxArtUrl: String? = null,
    @SerialName("igdb_id") val igdbId: String? = null
)

@Serializable
data class HelixSearchChannelDto(
    val id: String,
    @SerialName("broadcaster_login") val broadcasterLogin: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("is_live") val isLive: Boolean = false,
    @SerialName("game_name") val gameName: String? = null,
    val title: String? = null
)

@Serializable
data class HelixEmoteListResponse(
    val data: List<HelixEmoteDto> = emptyList(),
    val template: String? = null
)

@Serializable
data class HelixEmoteDto(
    val id: String,
    val name: String,
    val images: HelixEmoteImagesDto,
    val format: List<String> = emptyList(),
    val scale: List<String> = emptyList(),
    @SerialName("theme_mode") val themeMode: List<String> = emptyList(),
    val tier: String? = null,
    @SerialName("emote_type") val emoteType: String? = null,
    @SerialName("emote_set_id") val emoteSetId: String? = null
)

@Serializable
data class HelixEmoteImagesDto(
    @SerialName("url_1x") val url1x: String,
    @SerialName("url_2x") val url2x: String,
    @SerialName("url_4x") val url4x: String
)

@Serializable
data class HelixSendChatMessageRequestDto(
    @SerialName("broadcaster_id") val broadcasterId: String,
    @SerialName("sender_id") val senderId: String,
    val message: String,
    @SerialName("reply_parent_message_id") val replyParentMessageId: String? = null
)

@Serializable
data class HelixSendChatMessageResponseDto(
    val data: List<HelixSentChatMessageDto> = emptyList()
)

@Serializable
data class HelixSentChatMessageDto(
    @SerialName("message_id") val messageId: String? = null,
    @SerialName("is_sent") val isSent: Boolean = false,
    @SerialName("drop_reason") val dropReason: HelixChatDropReasonDto? = null
)

@Serializable
data class HelixChatDropReasonDto(
    val code: String? = null,
    val message: String? = null
)

@Serializable
data class HelixErrorDto(
    val error: String? = null,
    val status: Int? = null,
    val message: String? = null
)
