package com.example.chatterinomobile.data.repository

import android.net.Uri
import com.example.chatterinomobile.data.remote.api.TwitchPlaybackApi
import kotlin.random.Random

class TwitchPlaybackRepository(
    private val playbackApi: TwitchPlaybackApi
) {
    suspend fun getLiveHlsPlaylistUrl(channelLogin: String): String {
        val normalizedLogin = channelLogin.lowercase().trim()
        val token = playbackApi.getStreamPlaybackAccessToken(normalizedLogin)

        return Uri.parse("https://usher.ttvnw.net/api/channel/hls/$normalizedLogin.m3u8")
            .buildUpon()
            .appendQueryParameter("player", "twitchweb")
            .appendQueryParameter("token", token.value)
            .appendQueryParameter("sig", token.signature)
            .appendQueryParameter("allow_audio_only", "true")
            .appendQueryParameter("allow_source", "true")
            .appendQueryParameter("type", "any")
            .appendQueryParameter("fast_bread", "true")
            .appendQueryParameter("supported_codecs", "avc1")
            .appendQueryParameter("p", Random.nextInt(1, 999999).toString())
            .build()
            .toString()
    }
}

