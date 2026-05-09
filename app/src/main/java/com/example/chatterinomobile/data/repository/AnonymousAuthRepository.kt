package com.example.chatterinomobile.data.repository

import com.example.chatterinomobile.data.model.TwitchImplicitAuthResult

class AnonymousAuthRepository(
    private val clientId: String = ""
) : AuthRepository {

    override suspend fun getAccessToken(): String? = null

    override suspend fun getUserId(): String? = null

    override suspend fun getLogin(): String? = null

    override suspend fun getScopes(): List<String> = emptyList()

    override fun getClientId(): String = clientId

    override fun buildAuthorizeUrl(scopes: List<String>): String? = null

    override suspend fun completeImplicitFlow(redirectUrl: String): TwitchImplicitAuthResult =
        TwitchImplicitAuthResult.Failed("Twitch OAuth is not configured")

    override suspend fun clearSession() = Unit
}
