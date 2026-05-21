package com.example.chatterinomobile.data.remote.api

import com.example.chatterinomobile.data.remote.dto.HelixBadgeSetDto
import com.example.chatterinomobile.data.remote.dto.HelixEmoteListResponse
import com.example.chatterinomobile.data.remote.dto.HelixErrorDto
import com.example.chatterinomobile.data.remote.dto.HelixFollowedChannelDto
import com.example.chatterinomobile.data.remote.dto.HelixGameDto
import com.example.chatterinomobile.data.remote.dto.HelixListResponse
import com.example.chatterinomobile.data.remote.dto.HelixListResponseWithPagination
import com.example.chatterinomobile.data.remote.dto.HelixSearchChannelDto
import com.example.chatterinomobile.data.remote.dto.HelixSendChatMessageRequestDto
import com.example.chatterinomobile.data.remote.dto.HelixSendChatMessageResponseDto
import com.example.chatterinomobile.data.remote.dto.HelixStreamDto
import com.example.chatterinomobile.data.remote.dto.HelixTotalResponse
import com.example.chatterinomobile.data.remote.dto.HelixUserDto
import com.example.chatterinomobile.data.repository.AuthRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TwitchHelixApi(
    private val httpClient: HttpClient,
    private val authRepository: AuthRepository
) {

    suspend fun getUsersByLogin(logins: List<String>): List<HelixUserDto> {
        if (logins.isEmpty()) return emptyList()
        val token = authRepository.getAccessToken()
        if (token == null) return getPublicUsersByLogin(logins)
        val clientId = authRepository.getClientId()
        val response: HelixListResponse<HelixUserDto> = httpClient.get("$BASE_URL/users") {
            applyAuth(clientId, token)
            logins.forEach { parameter("login", it) }
        }.body()
        return response.data
    }

    suspend fun getStreamsByLogin(logins: List<String>): List<HelixStreamDto> {
        if (logins.isEmpty()) return emptyList()
        val token = authRepository.getAccessToken()
        if (token == null) return getPublicStreamsByLogin(logins)
        val clientId = authRepository.getClientId()
        val response: HelixListResponse<HelixStreamDto> = httpClient.get("$BASE_URL/streams") {
            applyAuth(clientId, token)
            logins.forEach { parameter("user_login", it) }
        }.body()
        return response.data
    }

    suspend fun getGlobalBadges(): List<HelixBadgeSetDto> {
        val token = authRepository.getAccessToken()
        val clientId = authRepository.getClientId()
        val response: HelixListResponse<HelixBadgeSetDto> =
            httpClient.get("$BASE_URL/chat/badges/global") {
                applyAuth(clientId, token)
            }.body()
        return response.data
    }


    data class FollowPage(val logins: List<String>, val nextCursor: String?)
    data class StreamPage(val streams: List<HelixStreamDto>, val nextCursor: String?)

    suspend fun getFollowedChannelsPaged(userId: String, after: String? = null): FollowPage {
        val token = authRepository.getAccessToken()
        val clientId = authRepository.getClientId()
        val response: HelixListResponseWithPagination<HelixFollowedChannelDto> =
            httpClient.get("$BASE_URL/channels/followed") {
                applyAuth(clientId, token)
                parameter("user_id", userId)
                parameter("first", 100)
                if (after != null) parameter("after", after)
            }.body()
        return FollowPage(
            logins = response.data.map { it.broadcasterLogin },
            nextCursor = response.pagination?.cursor?.takeIf { it.isNotBlank() }
        )
    }


    suspend fun getTopGames(limit: Int = 30): List<HelixGameDto> {
        val token = authRepository.getAccessToken()
        if (token == null) return getPublicTopGames(limit)
        val clientId = authRepository.getClientId()
        val response: HelixListResponseWithPagination<HelixGameDto> =
            httpClient.get("$BASE_URL/games/top") {
                applyAuth(clientId, token)
                parameter("first", limit.coerceIn(1, 100))
            }.body()
        return response.data
    }

    suspend fun getStreamsByGameIdPaged(
        gameId: String,
        limit: Int = 30,
        after: String? = null
    ): StreamPage {
        val token = authRepository.getAccessToken()
        if (token == null) return getPublicStreamsByGameId(gameId, limit)
        val clientId = authRepository.getClientId()
        val response: HelixListResponseWithPagination<HelixStreamDto> =
            httpClient.get("$BASE_URL/streams") {
                applyAuth(clientId, token)
                parameter("game_id", gameId)
                parameter("first", limit.coerceIn(1, 100))
                if (after != null) parameter("after", after)
            }.body()
        return StreamPage(
            streams = response.data,
            nextCursor = response.pagination?.cursor?.takeIf { it.isNotBlank() }
        )
    }

    suspend fun getStreamsByGameId(gameId: String, limit: Int = 30): List<HelixStreamDto> {
        return getStreamsByGameIdPaged(gameId = gameId, limit = limit).streams
    }

    suspend fun getTopStreams(limit: Int = 20): List<HelixStreamDto> {
        val token = authRepository.getAccessToken()
        if (token == null) return getPublicTopStreams(limit)
        val clientId = authRepository.getClientId()
        val response: HelixListResponseWithPagination<HelixStreamDto> =
            httpClient.get("$BASE_URL/streams") {
                applyAuth(clientId, token)
                parameter("first", limit.coerceIn(1, 100))
            }.body()
        return response.data
    }

    suspend fun searchChannels(query: String, limit: Int = 20): List<HelixSearchChannelDto> {
        if (query.isBlank()) return emptyList()
        val token = authRepository.getAccessToken()
        if (token == null) return searchPublicChannels(query, limit)
        val clientId = authRepository.getClientId()
        val response: HelixListResponseWithPagination<HelixSearchChannelDto> =
            httpClient.get("$BASE_URL/search/channels") {
                applyAuth(clientId, token)
                parameter("query", query)
                parameter("first", limit.coerceIn(1, 100))
                parameter("live_only", false)
            }.body()
        return response.data
    }

    suspend fun getChannelFollowerCount(broadcasterId: String): Int {
        val token = authRepository.getAccessToken()
        val clientId = authRepository.getClientId()
        val response: HelixTotalResponse =
            httpClient.get("$BASE_URL/channels/followers") {
                applyAuth(clientId, token)
                parameter("broadcaster_id", broadcasterId)
                parameter("first", 1)
            }.body()
        return response.total
    }

    suspend fun getChannelBadges(broadcasterId: String): List<HelixBadgeSetDto> {
        val token = authRepository.getAccessToken()
        val clientId = authRepository.getClientId()
        val response: HelixListResponse<HelixBadgeSetDto> =
            httpClient.get("$BASE_URL/chat/badges") {
                applyAuth(clientId, token)
                parameter("broadcaster_id", broadcasterId)
            }.body()
        return response.data
    }

    suspend fun getGlobalEmotes(): HelixEmoteListResponse {
        val token = authRepository.getAccessToken()
        val clientId = authRepository.getClientId()
        return httpClient.get("$BASE_URL/chat/emotes/global") {
            applyAuth(clientId, token)
        }.body()
    }

    suspend fun getChannelEmotes(broadcasterId: String): HelixEmoteListResponse {
        val token = authRepository.getAccessToken()
        val clientId = authRepository.getClientId()
        return httpClient.get("$BASE_URL/chat/emotes") {
            applyAuth(clientId, token)
            parameter("broadcaster_id", broadcasterId)
        }.body()
    }

    suspend fun sendChatMessage(
        broadcasterId: String,
        senderId: String,
        message: String,
        replyParentMessageId: String? = null
    ): SendChatMessageResult {
        val token = authRepository.getAccessToken()
            ?: return SendChatMessageResult.Failed("Sign in to send chat messages.")
        val clientId = authRepository.getClientId()
        val response = httpClient.post("$BASE_URL/chat/messages") {
            applyAuth(clientId, token)
            contentType(ContentType.Application.Json)
            setBody(
                HelixSendChatMessageRequestDto(
                    broadcasterId = broadcasterId,
                    senderId = senderId,
                    message = message,
                    replyParentMessageId = replyParentMessageId
                )
            )
        }

        if (response.status == HttpStatusCode.OK) {
            val sent = response.body<HelixSendChatMessageResponseDto>().data.firstOrNull()
            return if (sent?.isSent == true) {
                SendChatMessageResult.Sent
            } else {
                SendChatMessageResult.NotSent(
                    sent?.dropReason?.message ?: "Your message was not sent."
                )
            }
        }

        val error = runCatching { response.body<HelixErrorDto>() }.getOrNull()
        return SendChatMessageResult.Failed(
            error?.message?.takeIf { it.isNotBlank() }
                ?: "Failed to send message (${response.status.value})"
        )
    }

    private suspend fun getPublicTopStreams(limit: Int): List<HelixStreamDto> {
        val response: List<GqlResponse<GqlStreamsData>> = postPublicGql(
            operationName = "GuestTopStreams",
            variables = buildJsonObject {
                put("limit", limit.coerceIn(1, 100))
            },
            query = """
                query GuestTopStreams(${'$'}limit: Int!) {
                  streams(first: ${'$'}limit, options: {sort: VIEWER_COUNT}) {
                    edges {
                      node {
                        id
                        title
                        viewersCount
                        previewImageURL(width: 440, height: 248)
                        broadcaster {
                          id
                          login
                          displayName
                          profileImageURL(width: 70)
                          roles { isPartner }
                        }
                        game { id name }
                      }
                    }
                  }
                }
            """.trimIndent()
        )
        return response.firstOrNull()?.data?.streams?.edges.orEmpty()
            .mapNotNull { it.node?.toHelixStream() }
    }

    private suspend fun getPublicTopGames(limit: Int): List<HelixGameDto> {
        val response: List<GqlResponse<GqlGamesData>> = postPublicGql(
            operationName = "GuestTopGames",
            variables = buildJsonObject {
                put("limit", limit.coerceIn(1, 100))
            },
            query = """
                query GuestTopGames(${'$'}limit: Int!) {
                  games(first: ${'$'}limit, options: {sort: VIEWER_COUNT}) {
                    edges {
                      node {
                        id
                        name
                        viewersCount
                        boxArtURL(width: 285, height: 380)
                      }
                    }
                  }
                }
            """.trimIndent()
        )
        return response.firstOrNull()?.data?.games?.edges.orEmpty()
            .mapNotNull { edge ->
                val node = edge.node ?: return@mapNotNull null
                HelixGameDto(
                    id = node.id,
                    name = node.name,
                    boxArtUrl = node.boxArtURL
                )
            }
    }

    private suspend fun getPublicStreamsByGameId(gameId: String, limit: Int): StreamPage {
        val response: List<GqlResponse<GqlGameStreamsData>> = postPublicGql(
            operationName = "GuestGameStreams",
            variables = buildJsonObject {
                put("id", gameId)
                put("limit", limit.coerceIn(1, 100))
            },
            query = """
                query GuestGameStreams(${'$'}id: ID!, ${'$'}limit: Int!) {
                  game(id: ${'$'}id) {
                    streams(first: ${'$'}limit, options: {sort: VIEWER_COUNT}) {
                      edges {
                        node {
                          id
                          title
                          viewersCount
                          previewImageURL(width: 440, height: 248)
                          broadcaster {
                            id
                            login
                            displayName
                            profileImageURL(width: 70)
                            roles { isPartner }
                          }
                          game { id name }
                        }
                      }
                    }
                  }
                }
            """.trimIndent()
        )
        val streams = response.firstOrNull()?.data?.game?.streams?.edges.orEmpty()
            .mapNotNull { it.node?.toHelixStream() }
        return StreamPage(streams = streams, nextCursor = null)
    }

    private suspend fun getPublicUsersByLogin(logins: List<String>): List<HelixUserDto> {
        val response: List<GqlResponse<GqlUsersData>> = postPublicGql(
            operationName = "GuestUsers",
            variables = buildJsonObject {
                put("logins", JsonArray(logins.map { JsonPrimitive(it) }))
            },
            query = """
                query GuestUsers(${'$'}logins: [String!]!) {
                  users(logins: ${'$'}logins) {
                    id
                    login
                    displayName
                    profileImageURL(width: 70)
                    roles { isPartner }
                    stream {
                      id
                      title
                      viewersCount
                      previewImageURL(width: 440, height: 248)
                      game { id name }
                    }
                  }
                }
            """.trimIndent()
        )
        return response.firstOrNull()?.data?.users.orEmpty()
            .map { it.toHelixUser() }
    }

    private suspend fun getPublicStreamsByLogin(logins: List<String>): List<HelixStreamDto> {
        val response: List<GqlResponse<GqlUsersData>> = postPublicGql(
            operationName = "GuestUsers",
            variables = buildJsonObject {
                put("logins", JsonArray(logins.map { JsonPrimitive(it) }))
            },
            query = """
                query GuestUsers(${'$'}logins: [String!]!) {
                  users(logins: ${'$'}logins) {
                    id
                    login
                    displayName
                    profileImageURL(width: 70)
                    roles { isPartner }
                    stream {
                      id
                      title
                      viewersCount
                      previewImageURL(width: 440, height: 248)
                      game { id name }
                    }
                  }
                }
            """.trimIndent()
        )
        return response.firstOrNull()?.data?.users.orEmpty()
            .mapNotNull { user -> user.stream?.toHelixStream(user) }
    }

    private suspend fun searchPublicChannels(queryText: String, limit: Int): List<HelixSearchChannelDto> {
        val response: List<GqlResponse<GqlSearchData>> = postPublicGql(
            operationName = "GuestSearchChannels",
            variables = buildJsonObject {
                put("query", queryText)
            },
            query = """
                query GuestSearchChannels(${'$'}query: String!) {
                  searchFor(userQuery: ${'$'}query, platform: "web") {
                    channels {
                      edges {
                        item {
                          ... on User {
                            id
                            login
                            displayName
                            profileImageURL(width: 70)
                            roles { isPartner }
                            stream {
                              id
                              title
                              viewersCount
                              previewImageURL(width: 440, height: 248)
                              game { id name }
                            }
                          }
                        }
                      }
                    }
                  }
                }
            """.trimIndent()
        )
        return response.firstOrNull()?.data?.searchFor?.channels?.edges.orEmpty()
            .mapNotNull { it.item?.toHelixSearchChannel() }
            .take(limit.coerceIn(1, 100))
    }

    private suspend inline fun <reified T> postPublicGql(
        operationName: String,
        variables: JsonObject,
        query: String
    ): T = httpClient.post(GQL_URL) {
        header("Client-ID", TWITCH_WEB_CLIENT_ID)
        header("Origin", "https://www.twitch.tv")
        header("Referer", "https://www.twitch.tv/")
        contentType(ContentType.Application.Json)
        setBody(listOf(GqlRequest(operationName, variables, query)))
    }.body()

    private fun GqlStreamNode.toHelixStream(): HelixStreamDto? {
        val user = broadcaster ?: return null
        return toHelixStream(user)
    }

    private fun GqlStreamNode.toHelixStream(user: GqlUser): HelixStreamDto =
        HelixStreamDto(
            id = id,
            userId = user.id,
            userLogin = user.login,
            userName = user.displayName,
            gameId = game?.id,
            gameName = game?.name,
            type = "live",
            title = title.orEmpty(),
            viewerCount = viewersCount ?: 0,
            startedAt = "",
            thumbnailUrl = previewImageURL
        )

    private fun GqlUser.toHelixUser(): HelixUserDto =
        HelixUserDto(
            id = id,
            login = login,
            displayName = displayName,
            profileImageUrl = profileImageURL,
            broadcasterType = if (roles?.isPartner == true) "partner" else null
        )

    private fun GqlUser.toHelixSearchChannel(): HelixSearchChannelDto =
        HelixSearchChannelDto(
            id = id,
            broadcasterLogin = login,
            displayName = displayName,
            thumbnailUrl = stream?.previewImageURL ?: profileImageURL,
            isLive = stream != null,
            gameName = stream?.game?.name,
            title = stream?.title
        )

    private fun HttpRequestBuilder.applyAuth(clientId: String, token: String?) {
        header("Client-Id", clientId)
        if (token != null) header("Authorization", "Bearer $token")
    }

    sealed interface SendChatMessageResult {
        data object Sent : SendChatMessageResult
        data class NotSent(val message: String) : SendChatMessageResult
        data class Failed(val message: String) : SendChatMessageResult
    }

    companion object {
        private const val BASE_URL = "https://api.twitch.tv/helix"
        private const val GQL_URL = "https://gql.twitch.tv/gql"
        private const val TWITCH_WEB_CLIENT_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko"
    }
}

@Serializable
private data class GqlRequest(
    val operationName: String,
    val variables: JsonObject,
    val query: String
)

@Serializable
private data class GqlResponse<T>(
    val data: T? = null
)

@Serializable
private data class GqlStreamsData(
    val streams: GqlStreamConnection? = null
)

@Serializable
private data class GqlGameStreamsData(
    val game: GqlGameWithStreams? = null
)

@Serializable
private data class GqlGamesData(
    val games: GqlGameConnection? = null
)

@Serializable
private data class GqlUsersData(
    val users: List<GqlUser> = emptyList()
)

@Serializable
private data class GqlSearchData(
    val searchFor: GqlSearchFor? = null
)

@Serializable
private data class GqlSearchFor(
    val channels: GqlSearchChannelConnection? = null
)

@Serializable
private data class GqlStreamConnection(
    val edges: List<GqlStreamEdge> = emptyList()
)

@Serializable
private data class GqlStreamEdge(
    val node: GqlStreamNode? = null
)

@Serializable
private data class GqlGameConnection(
    val edges: List<GqlGameEdge> = emptyList()
)

@Serializable
private data class GqlGameEdge(
    val node: GqlGameNode? = null
)

@Serializable
private data class GqlSearchChannelConnection(
    val edges: List<GqlSearchChannelEdge> = emptyList()
)

@Serializable
private data class GqlSearchChannelEdge(
    val item: GqlUser? = null
)

@Serializable
private data class GqlGameWithStreams(
    val streams: GqlStreamConnection? = null
)

@Serializable
private data class GqlStreamNode(
    val id: String,
    val title: String? = null,
    val viewersCount: Int? = null,
    @SerialName("previewImageURL") val previewImageURL: String? = null,
    val broadcaster: GqlUser? = null,
    val game: GqlGame? = null
)

@Serializable
private data class GqlUser(
    val id: String,
    val login: String,
    val displayName: String,
    @SerialName("profileImageURL") val profileImageURL: String? = null,
    val roles: GqlRoles? = null,
    val stream: GqlStreamNode? = null
)

@Serializable
private data class GqlRoles(
    val isPartner: Boolean = false
)

@Serializable
private data class GqlGame(
    val id: String,
    val name: String
)

@Serializable
private data class GqlGameNode(
    val id: String,
    val name: String,
    val viewersCount: Int? = null,
    @SerialName("boxArtURL") val boxArtURL: String? = null
)
