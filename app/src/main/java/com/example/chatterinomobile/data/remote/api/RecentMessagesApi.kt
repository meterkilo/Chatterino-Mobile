package com.example.chatterinomobile.data.remote.api

import com.example.chatterinomobile.data.remote.irc.IrcMessage
import com.example.chatterinomobile.data.remote.irc.IrcParser
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import java.net.URLEncoder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class RecentMessagesApi(
    private val httpClient: HttpClient
) {

    suspend fun getRecentMessages(channelLogin: String, limit: Int = DEFAULT_LIMIT): List<IrcMessage> {
        val normalized = channelLogin.lowercase().removePrefix("#").trim()
        if (normalized.isBlank()) return emptyList()

        val response: RecentMessagesResponseDto =
            httpClient.get("$BASE_URL/${normalized.urlPathSegment()}") {
                parameter("limit", limit.coerceIn(1, MAX_LIMIT))
            }.body()

        return response.messages.mapNotNull { raw ->
            IrcParser.parse(raw.unescapeZeroWidthJoiner())
        }
    }

    private fun String.urlPathSegment(): String =
        URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")

    private fun String.unescapeZeroWidthJoiner(): String =
        replace(ESCAPED_ZERO_WIDTH_JOINER, ZERO_WIDTH_JOINER)

    @Serializable
    private data class RecentMessagesResponseDto(
        val messages: List<String> = emptyList(),
        val error: String? = null,
        @SerialName("error_code") val errorCode: String? = null
    )

    private companion object {
        private const val BASE_URL = "https://recent-messages.robotty.de/api/v2/recent-messages"
        private const val DEFAULT_LIMIT = 50
        private const val MAX_LIMIT = 100
        private const val ESCAPED_ZERO_WIDTH_JOINER = "\uDB40\uDC02"
        private const val ZERO_WIDTH_JOINER = "\u200D"
    }
}
