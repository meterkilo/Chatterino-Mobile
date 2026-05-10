package com.example.chatterinomobile.ui.player

import android.annotation.SuppressLint
import android.app.Application
import android.net.Uri
import android.view.ViewGroup
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.chatterinomobile.data.repository.TwitchPlaybackRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StreamPlayerViewModel(
    application: Application,
    private val playbackRepository: TwitchPlaybackRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(StreamPlayerUiState())
    val uiState = _uiState.asStateFlow()

    @SuppressLint("StaticFieldLeak")
    private var cachedWebView: TwitchStreamWebView? = null

    private var nativePlayer: ExoPlayer? = null
    private var nativeLoadJob: Job? = null
    private var activeChannelLogin: String? = null
    private var activeBackend: StreamPlayerBackend? = null
    private var loadedEmbedChannelLogin: String? = null
    private var lastEmbedMuted: Boolean = false

    var hasWebViewBeenAttached: Boolean = false

    fun playChannel(
        channelLogin: String,
        preferredBackend: StreamPlayerBackend = StreamPlayerBackend.Native,
        forceReload: Boolean = false
    ) {
        val normalizedLogin = channelLogin.lowercase().trim()
        if (normalizedLogin.isBlank()) return

        val current = _uiState.value
        if (
            !forceReload &&
            activeChannelLogin == normalizedLogin &&
            activeBackend == preferredBackend &&
            current.loadState != StreamPlayerLoadState.Error
        ) {
            return
        }

        activeChannelLogin = normalizedLogin
        activeBackend = preferredBackend

        when (preferredBackend) {
            StreamPlayerBackend.Native -> playNative(normalizedLogin)
            StreamPlayerBackend.TwitchEmbed -> playEmbed(normalizedLogin)
        }
    }

    fun refreshCurrent() {
        val channelLogin = activeChannelLogin ?: return
        when (_uiState.value.backend) {
            StreamPlayerBackend.Native -> playChannel(
                channelLogin = channelLogin,
                preferredBackend = StreamPlayerBackend.Native,
                forceReload = true
            )
            StreamPlayerBackend.TwitchEmbed -> {
                hasWebViewBeenAttached = false
                cachedWebView?.reload()
            }
        }
    }

    fun getOrCreateNativePlayer(): ExoPlayer =
        nativePlayer ?: buildNativePlayer().also { nativePlayer = it }

    fun getOrCreateWebView(): TwitchStreamWebView =
        cachedWebView ?: TwitchStreamWebView(getApplication()).also { cachedWebView = it }

    fun initializeEmbedPlayback() {
        cachedWebView?.evaluateJavascript(INIT_EMBED_PLAYBACK_SCRIPT, null)
    }

    fun stopPlayback() {
        nativeLoadJob?.cancel()
        nativePlayer?.pause()
        cachedWebView?.evaluateJavascript(
            "(window.__chatterinoVideoEl || document.querySelector('video'))?.pause()",
            null
        )
    }

    fun clear() {
        nativeLoadJob?.cancel()
        nativePlayer?.clearMediaItems()
        cachedWebView?.let { webView ->
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.loadUrl("about:blank")
        }
        activeChannelLogin = null
        activeBackend = null
        loadedEmbedChannelLogin = null
        hasWebViewBeenAttached = false
        _uiState.value = StreamPlayerUiState()
    }

    override fun onCleared() {
        nativeLoadJob?.cancel()
        nativePlayer?.release()
        nativePlayer = null
        cachedWebView?.destroy()
        cachedWebView = null
        super.onCleared()
    }

    private fun playNative(channelLogin: String) {
        nativeLoadJob?.cancel()
        stopEmbedPlayback()
        _uiState.value = StreamPlayerUiState(
            backend = StreamPlayerBackend.Native,
            loadState = StreamPlayerLoadState.Loading,
            channelLogin = channelLogin
        )

        nativeLoadJob = viewModelScope.launch {
            runCatching { playbackRepository.getLiveHlsPlaylistUrl(channelLogin) }
                .onSuccess { playlistUrl ->
                    if (activeChannelLogin != channelLogin) return@onSuccess
                    prepareNativePlayer(channelLogin, playlistUrl)
                }
                .onFailure { error ->
                    if (activeChannelLogin != channelLogin) return@onFailure
                    playEmbed(
                        channelLogin = channelLogin,
                        fallbackReason = error.message ?: "Native player failed to load."
                    )
                }
        }
    }

    private fun prepareNativePlayer(channelLogin: String, playlistUrl: String) {
        val player = getOrCreateNativePlayer()
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(Uri.parse(playlistUrl))
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setTargetOffsetMs(LIVE_TARGET_OFFSET_MS)
                        .setMinPlaybackSpeed(LIVE_MIN_PLAYBACK_SPEED)
                        .setMaxPlaybackSpeed(LIVE_MAX_PLAYBACK_SPEED)
                        .build()
                )
                .build()
        )
        player.prepare()
        player.playWhenReady = true
        player.play()
        _uiState.value = StreamPlayerUiState(
            backend = StreamPlayerBackend.Native,
            loadState = StreamPlayerLoadState.Ready,
            channelLogin = channelLogin
        )
    }

    private fun playEmbed(channelLogin: String, fallbackReason: String? = null) {
        nativeLoadJob?.cancel()
        nativePlayer?.pause()
        activeBackend = StreamPlayerBackend.TwitchEmbed
        _uiState.value = StreamPlayerUiState(
            backend = StreamPlayerBackend.TwitchEmbed,
            loadState = StreamPlayerLoadState.Loading,
            channelLogin = channelLogin,
            message = fallbackReason
        )
        loadEmbedChannel(channelLogin)
    }

    private fun loadEmbedChannel(channelLogin: String, muted: Boolean = false) {
        val webView = getOrCreateWebView()
        if (loadedEmbedChannelLogin == channelLogin && lastEmbedMuted == muted && webView.url != null) return

        loadedEmbedChannelLogin = channelLogin
        lastEmbedMuted = muted
        hasWebViewBeenAttached = false
        webView.stopLoading()
        webView.loadUrl(twitchPlayerUrl(channelLogin, muted))
    }

    private fun stopEmbedPlayback() {
        cachedWebView?.evaluateJavascript(
            "(window.__chatterinoVideoEl || document.querySelector('video'))?.pause()",
            null
        )
    }

    private fun onNativePlaybackError(error: PlaybackException) {
        val channelLogin = activeChannelLogin ?: return
        playEmbed(
            channelLogin = channelLogin,
            fallbackReason = error.message ?: "Native player failed during playback."
        )
    }

    private fun buildNativePlayer(): ExoPlayer {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to "https://player.twitch.tv",
                    "Origin" to "https://player.twitch.tv"
                )
            )

        val player = ExoPlayer.Builder(
            getApplication<Application>(),
            DefaultMediaSourceFactory(dataSourceFactory)
        )
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        MIN_BUFFER_MS,
                        MAX_BUFFER_MS,
                        BUFFER_FOR_PLAYBACK_MS,
                        BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
                    )
                    .build()
            )
            .build()

        player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
        player.addListener(
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    onNativePlaybackError(error)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    val channelLogin = activeChannelLogin ?: return
                    if (_uiState.value.backend != StreamPlayerBackend.Native) return
                    when (playbackState) {
                        Player.STATE_BUFFERING -> _uiState.update {
                            it.copy(loadState = StreamPlayerLoadState.Loading)
                        }
                        Player.STATE_READY -> _uiState.value = StreamPlayerUiState(
                            backend = StreamPlayerBackend.Native,
                            loadState = StreamPlayerLoadState.Ready,
                            channelLogin = channelLogin
                        )
                    }
                }
            }
        )
        return player
    }

    private fun twitchPlayerUrl(channelLogin: String, muted: Boolean): String =
        Uri.parse("https://player.twitch.tv/")
            .buildUpon()
            .appendQueryParameter("channel", channelLogin)
            .appendQueryParameter("parent", TWITCH_EMBED_PARENT)
            .appendQueryParameter("autoplay", "true")
            .appendQueryParameter("muted", muted.toString())
            .appendQueryParameter("enableExtensions", "true")
            .build()
            .toString()

    private companion object {
        const val TWITCH_EMBED_PARENT = "twitch.tv"
        const val USER_AGENT = "ChatterinoMobile"
        const val LIVE_TARGET_OFFSET_MS = 2_500L
        const val LIVE_MIN_PLAYBACK_SPEED = 0.97f
        const val LIVE_MAX_PLAYBACK_SPEED = 1.03f
        const val MIN_BUFFER_MS = 1_000
        const val MAX_BUFFER_MS = 5_000
        const val BUFFER_FOR_PLAYBACK_MS = 500
        const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 1_000
        const val INIT_EMBED_PLAYBACK_SCRIPT = """
            (() => {
              if (window.__chatterinoInitPlayback) {
                window.__chatterinoInitPlayback();
                return;
              }

              window.__chatterinoWaitFor = (selector, timeout = 10000) => new Promise((resolve) => {
                const existing = document.querySelector(selector);
                if (existing) {
                  resolve(existing);
                  return;
                }

                let timeoutId;
                const observer = new MutationObserver(() => {
                  const element = document.querySelector(selector);
                  if (element) {
                    observer.disconnect();
                    clearTimeout(timeoutId);
                    resolve(element);
                  }
                });

                observer.observe(document.body, { childList: true, subtree: true });
                timeoutId = setTimeout(() => {
                  observer.disconnect();
                  resolve(null);
                }, timeout);
              });

              window.__chatterinoInitPlayback = async () => {
                const video = await window.__chatterinoWaitFor('video');
                if (!video) return;

                window.__chatterinoVideoEl = video;
                video.setAttribute('playsinline', '');
                video.muted = false;
                video.volume = 1.0;

                if (video.textTracks && video.textTracks.length > 0) {
                  video.textTracks[0].mode = 'hidden';
                }

                try {
                  await video.play();
                } catch (e) {
                  video.muted = true;
                  try { await video.play(); } catch (_) {}
                }
              };

              window.__chatterinoInitPlayback();
            })();
        """
    }
}

data class StreamPlayerUiState(
    val backend: StreamPlayerBackend = StreamPlayerBackend.Native,
    val loadState: StreamPlayerLoadState = StreamPlayerLoadState.Idle,
    val channelLogin: String? = null,
    val message: String? = null
)

enum class StreamPlayerBackend {
    Native,
    TwitchEmbed
}

enum class StreamPlayerLoadState {
    Idle,
    Loading,
    Ready,
    Error
}
