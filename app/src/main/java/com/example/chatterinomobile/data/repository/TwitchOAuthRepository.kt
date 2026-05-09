package com.example.chatterinomobile.data.repository

import android.net.Uri
import com.example.chatterinomobile.BuildConfig
import com.example.chatterinomobile.data.local.TokenStore
import com.example.chatterinomobile.data.model.TwitchImplicitAuthResult
import com.example.chatterinomobile.data.remote.api.TwitchOAuthApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TwitchOAuthRepository(
    private val oauthApi: TwitchOAuthApi,
    private val tokenStore: TokenStore
) : AuthRepository {

    private val sessionMutex = Mutex()

    override suspend fun getAccessToken(): String? =
        sessionMutex.withLock {
            val current = tokenStore.read() ?: return null
            val now = System.currentTimeMillis()

            if (current.expiresAtEpochMillis <= now + EXPIRY_SKEW_MILLIS) {
                tokenStore.clear()
                return null
            }

            if (current.lastValidatedAtEpochMillis <= now - VALIDATE_INTERVAL_MILLIS) {
                val validated = validateLocked(current) ?: return null
                validated.accessToken
            } else {
                current.accessToken
            }
        }

    override suspend fun getUserId(): String? =
        sessionMutex.withLock { tokenStore.read()?.userId }

    override suspend fun getLogin(): String? =
        sessionMutex.withLock { tokenStore.read()?.login }

    override suspend fun getScopes(): List<String> =
        sessionMutex.withLock { tokenStore.read()?.scopes.orEmpty() }

    override fun getClientId(): String = BuildConfig.TWITCH_CLIENT_ID

    override fun buildAuthorizeUrl(scopes: List<String>): String? {
        val clientId = getClientId().trim()
        if (clientId.isBlank()) return null

        return Uri.parse("$AUTHORIZE_URL").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", AuthRepository.OAUTH_REDIRECT_URL)
            .appendQueryParameter("response_type", "token")
            .appendQueryParameter("scope", scopes.joinToString(" "))
            .appendQueryParameter("force_verify", "true")
            .build()
            .toString()
    }

    override suspend fun completeImplicitFlow(redirectUrl: String): TwitchImplicitAuthResult {
        val parsed = parseRedirect(redirectUrl)
            ?: return TwitchImplicitAuthResult.Failed("Could not read Twitch redirect")

        parsed.error?.let { error ->
            return if (error == "access_denied") {
                TwitchImplicitAuthResult.Denied
            } else {
                TwitchImplicitAuthResult.Failed(parsed.errorDescription ?: error)
            }
        }

        val accessToken = parsed.accessToken
            ?: return TwitchImplicitAuthResult.Failed("No access token in Twitch redirect")

        val now = System.currentTimeMillis()
        val expiresInSeconds = parsed.expiresInSeconds ?: DEFAULT_TOKEN_LIFETIME_SECONDS
        val scopesFromUrl = parsed.scopes

        val validated = when (val validation = oauthApi.validateAccessToken(accessToken)) {
            is TwitchOAuthApi.ValidateTokenResult.Valid -> validation.token
            TwitchOAuthApi.ValidateTokenResult.Invalid ->
                return TwitchImplicitAuthResult.Failed("Twitch returned an invalid access token")
            is TwitchOAuthApi.ValidateTokenResult.Failed ->
                return TwitchImplicitAuthResult.Failed(validation.message)
        }

        val resolvedScopes = if (validated.scopes.isNotEmpty()) validated.scopes else scopesFromUrl

        val stored = TokenStore.StoredTwitchSession(
            accessToken = accessToken,
            refreshToken = null,
            userId = validated.userId,
            login = validated.login,
            expiresAtEpochMillis = now + (expiresInSeconds * 1000L),
            scopes = resolvedScopes,
            lastValidatedAtEpochMillis = now
        )

        sessionMutex.withLock {
            tokenStore.write(stored)
        }

        return TwitchImplicitAuthResult.Authorized(
            userId = stored.userId,
            login = stored.login,
            accessToken = stored.accessToken,
            scopes = stored.scopes,
            expiresAtEpochMillis = stored.expiresAtEpochMillis
        )
    }

    override suspend fun clearSession() {
        sessionMutex.withLock {
            tokenStore.clear()
        }
    }

    private suspend fun validateLocked(
        current: TokenStore.StoredTwitchSession
    ): TokenStore.StoredTwitchSession? =
        when (val result = oauthApi.validateAccessToken(current.accessToken)) {
            is TwitchOAuthApi.ValidateTokenResult.Valid -> {
                val now = System.currentTimeMillis()
                val updated = current.copy(
                    userId = result.token.userId ?: current.userId,
                    login = result.token.login ?: current.login,
                    expiresAtEpochMillis = now + (result.token.expiresIn * 1000L),
                    scopes = if (result.token.scopes.isNotEmpty()) {
                        result.token.scopes
                    } else {
                        current.scopes
                    },
                    lastValidatedAtEpochMillis = now
                )
                tokenStore.write(updated)
                updated
            }
            TwitchOAuthApi.ValidateTokenResult.Invalid -> {
                tokenStore.clear()
                null
            }
            is TwitchOAuthApi.ValidateTokenResult.Failed -> current
        }

    private data class ParsedRedirect(
        val accessToken: String?,
        val expiresInSeconds: Long?,
        val scopes: List<String>,
        val error: String?,
        val errorDescription: String?
    )

    private fun parseRedirect(url: String): ParsedRedirect? {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null

        val fragmentParams = uri.fragment
            ?.split("&")
            .orEmpty()
            .mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx <= 0) null else pair.substring(0, idx) to Uri.decode(pair.substring(idx + 1))
            }
            .toMap()

        val accessToken = fragmentParams["access_token"]
        val expiresIn = fragmentParams["expires_in"]?.toLongOrNull()
        val scopes = fragmentParams["scope"]?.split(" ")?.filter { it.isNotBlank() }.orEmpty()

        val error = fragmentParams["error"] ?: uri.getQueryParameter("error")
        val errorDescription = fragmentParams["error_description"]
            ?: uri.getQueryParameter("error_description")

        if (accessToken == null && error == null) return null

        return ParsedRedirect(
            accessToken = accessToken,
            expiresInSeconds = expiresIn,
            scopes = scopes,
            error = error,
            errorDescription = errorDescription
        )
    }

    companion object {
        private const val AUTHORIZE_URL = "https://id.twitch.tv/oauth2/authorize"
        private const val EXPIRY_SKEW_MILLIS = 60_000L
        private const val VALIDATE_INTERVAL_MILLIS = 60L * 60L * 1000L
        private const val DEFAULT_TOKEN_LIFETIME_SECONDS = 4L * 60L * 60L
    }
}
