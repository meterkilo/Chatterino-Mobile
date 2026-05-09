package com.example.chatterinomobile.data.repository

import com.example.chatterinomobile.data.model.TwitchImplicitAuthResult

interface AuthRepository {

    suspend fun getAccessToken(): String?

    suspend fun getUserId(): String?

    suspend fun getLogin(): String?

    suspend fun getScopes(): List<String>

    fun getClientId(): String

    fun buildAuthorizeUrl(scopes: List<String> = DEFAULT_TWITCH_SCOPES): String?

    suspend fun completeImplicitFlow(redirectUrl: String): TwitchImplicitAuthResult

    suspend fun clearSession()

    companion object {
        const val REDIRECT_URI = "chatterinomobile://oauth/twitch"
        const val OAUTH_REDIRECT_URL = "https://meterkilo.github.io/"

        val DEFAULT_TWITCH_SCOPES = listOf(
            "chat:read",
            "chat:edit",
            "user:write:chat",
            "user:read:follows",
            "user:read:emotes"
        )
    }
}
