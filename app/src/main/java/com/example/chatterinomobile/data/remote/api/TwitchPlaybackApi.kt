package com.example.chatterinomobile.data.remote.api

import com.example.chatterinomobile.data.remote.dto.TwitchGqlExtensionsDto
import com.example.chatterinomobile.data.remote.dto.TwitchPersistedQueryDto
import com.example.chatterinomobile.data.remote.dto.TwitchPlaybackAccessTokenDto
import com.example.chatterinomobile.data.remote.dto.TwitchPlaybackAccessTokenRequestDto
import com.example.chatterinomobile.data.remote.dto.TwitchPlaybackAccessTokenResponseDto
import com.example.chatterinomobile.data.remote.dto.TwitchPlaybackAccessTokenVariablesDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class TwitchPlaybackApi(
    private val httpClient: HttpClient
) {
    suspend fun getStreamPlaybackAccessToken(
        channelLogin: String,
        playerType: String = DEFAULT_PLAYER_TYPE
    ): TwitchPlaybackAccessTokenDto {
        val response: List<TwitchPlaybackAccessTokenResponseDto> = httpClient.post(GQL_URL) {
            header("Client-ID", TWITCH_WEB_CLIENT_ID)
            header("Origin", "https://www.twitch.tv")
            header("Referer", "https://www.twitch.tv/")
            contentType(ContentType.Application.Json)
            setBody(
                listOf(
                    TwitchPlaybackAccessTokenRequestDto(
                        operationName = PLAYBACK_ACCESS_TOKEN_OPERATION,
                        variables = TwitchPlaybackAccessTokenVariablesDto(
                            isLive = true,
                            isVod = false,
                            login = channelLogin,
                            vodID = "",
                            playerType = playerType
                        ),
                        extensions = TwitchGqlExtensionsDto(
                            persistedQuery = TwitchPersistedQueryDto(
                                version = 1,
                                sha256Hash = PLAYBACK_ACCESS_TOKEN_HASH
                            )
                        )
                    )
                )
            )
        }.body()

        return response.firstOrNull()
            ?.data
            ?.streamPlaybackAccessToken
            ?: error("Twitch did not return a stream playback token.")
    }

    private companion object {
        const val GQL_URL = "https://gql.twitch.tv/gql"
        const val TWITCH_WEB_CLIENT_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko"
        const val DEFAULT_PLAYER_TYPE = "site"
        const val PLAYBACK_ACCESS_TOKEN_OPERATION = "PlaybackAccessToken"
        const val PLAYBACK_ACCESS_TOKEN_HASH = "0828119ded1c13477966434e15800ff57ddacf13ba1911c129dc2200705b0712"
    }
}

