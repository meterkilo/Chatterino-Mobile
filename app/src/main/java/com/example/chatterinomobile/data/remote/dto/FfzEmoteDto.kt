package com.example.chatterinomobile.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FfzGlobalResponseDto(
    @SerialName("default_sets") val defaultSets: List<Int>,
    val sets: Map<String, FfzEmoteSetDto>
)

@Serializable
data class FfzEmoteSetDto(
    val id: Int,
    val emoticons: List<FfzEmoteDto>
)

@Serializable
data class FfzRoomResponseDto(
    val room: FfzRoomDto,
    val sets: Map<String, FfzEmoteSetDto>
)

@Serializable
data class FfzRoomDto(
    val set: Int? = null,
    @SerialName("user_badge_ids") val userBadgeIds: Map<String, List<Long>> = emptyMap()
)

@Serializable
data class FfzEmoteDto(
    val id: Int,
    val name: String,
    val animated: Map<String, String>? = null,
    val urls: Map<String, String>
)

@Serializable
data class FfzBadgesResponseDto(
    val badges: List<FfzBadgeDto> = emptyList(),
    val users: Map<String, List<Long>> = emptyMap()
)

@Serializable
data class FfzBadgeDto(
    val id: Int,
    val name: String = "",
    val title: String = "",
    val color: String? = null,
    val urls: Map<String, String> = emptyMap(),
    val width: Int = 18,
    val height: Int = 18
)
