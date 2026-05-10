package com.example.chatterinomobile.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TwitchPlaybackAccessTokenRequestDto(
    val operationName: String,
    val variables: TwitchPlaybackAccessTokenVariablesDto,
    val extensions: TwitchGqlExtensionsDto
)

@Serializable
data class TwitchPlaybackAccessTokenVariablesDto(
    val isLive: Boolean,
    val isVod: Boolean,
    val login: String,
    val vodID: String,
    val playerType: String
)

@Serializable
data class TwitchGqlExtensionsDto(
    val persistedQuery: TwitchPersistedQueryDto
)

@Serializable
data class TwitchPersistedQueryDto(
    val version: Int,
    val sha256Hash: String
)

@Serializable
data class TwitchPlaybackAccessTokenResponseDto(
    val data: TwitchPlaybackAccessTokenDataDto? = null
)

@Serializable
data class TwitchPlaybackAccessTokenDataDto(
    @SerialName("streamPlaybackAccessToken")
    val streamPlaybackAccessToken: TwitchPlaybackAccessTokenDto? = null
)

@Serializable
data class TwitchPlaybackAccessTokenDto(
    val value: String,
    val signature: String
)

