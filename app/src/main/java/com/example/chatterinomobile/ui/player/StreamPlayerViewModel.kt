package com.example.chatterinomobile.ui.player

import android.annotation.SuppressLint
import android.app.Application
import android.net.Uri
import android.view.ViewGroup
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.example.chatterinomobile.data.repository.TwitchPlaybackRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    private val _qualityState = MutableStateFlow(VideoQualityState())
    val qualityState = _qualityState.asStateFlow()

    private val _muted = MutableStateFlow(false)
    val muted = _muted.asStateFlow()

    @SuppressLint("StaticFieldLeak")
    private var cachedWebView: TwitchStreamWebView? = null

    private var nativePlayer: ExoPlayer? = null
    private var hlsMediaSourceFactory: HlsMediaSource.Factory? = null
    private var nativeLoadJob: Job? = null
    private var adInfoJob: Job? = null
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
        adInfoJob?.cancel()
        adInfoJob = null
        clearAdInfo()
        nativePlayer?.pause()
        cachedWebView?.evaluateJavascript(
            "(window.__7tvVideoEl || document.querySelector('video'))?.pause()",
            null
        )
    }

    fun clear() {
        nativeLoadJob?.cancel()
        adInfoJob?.cancel()
        adInfoJob = null
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
        adInfoJob?.cancel()
        nativePlayer?.release()
        nativePlayer = null
        cachedWebView?.destroy()
        cachedWebView = null
        super.onCleared()
    }

    private fun playNative(channelLogin: String) {
        nativeLoadJob?.cancel()
        adInfoJob?.cancel()
        adInfoJob = null
        stopEmbedPlayback()
        _qualityState.value = VideoQualityState()
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
        val mediaItem = MediaItem.Builder()
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
        val factory = hlsMediaSourceFactory
            ?: error("HLS media source factory not initialized")
        player.setMediaSource(factory.createMediaSource(mediaItem))
        player.prepare()
        player.playWhenReady = true
        player.play()
        startAdInfoTicker(player)
        _uiState.value = StreamPlayerUiState(
            backend = StreamPlayerBackend.Native,
            loadState = StreamPlayerLoadState.Ready,
            channelLogin = channelLogin
        )
    }

    private fun playEmbed(channelLogin: String, fallbackReason: String? = null) {
        nativeLoadJob?.cancel()
        adInfoJob?.cancel()
        adInfoJob = null
        clearAdInfo()
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
            "(window.__7tvVideoEl || document.querySelector('video'))?.pause()",
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
            .setAllowCrossProtocolRedirects(true)

        hlsMediaSourceFactory = HlsMediaSource.Factory(dataSourceFactory)
            .setAllowChunklessPreparation(true)
            .setLoadErrorHandlingPolicy(adTolerantLoadErrorPolicy())

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
        player.volume = if (_muted.value) 0f else 1f
        player.addListener(
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    onNativePlaybackError(error)
                }

                override fun onTracksChanged(tracks: Tracks) {
                    refreshQualityState(tracks)
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
                            channelLogin = channelLogin,
                            adInfo = _uiState.value.adInfo
                        )
                    }
                }
            }
        )
        return player
    }

    private fun startAdInfoTicker(player: ExoPlayer) {
        adInfoJob?.cancel()
        adInfoJob = viewModelScope.launch {
            while (true) {
                refreshAdInfo(player)
                delay(AD_INFO_POLL_MS)
            }
        }
    }

    private fun refreshAdInfo(player: ExoPlayer) {
        if (_uiState.value.backend != StreamPlayerBackend.Native || !player.isPlayingAd) {
            clearAdInfo()
            return
        }
        val duration = player.duration.takeUnless { it == C.TIME_UNSET || it <= 0L }
        val remainingMs = duration?.let { (it - player.currentPosition).coerceAtLeast(0L) }
        val adIndex = player.currentAdIndexInAdGroup.takeIf { it >= 0 }?.plus(1)
        _uiState.update {
            it.copy(
                adInfo = AdPlaybackInfo(
                    remainingMs = remainingMs,
                    adIndex = adIndex
                )
            )
        }
    }

    private fun clearAdInfo() {
        if (_uiState.value.adInfo == null) return
        _uiState.update { it.copy(adInfo = null) }
    }

    private fun refreshQualityState(tracks: Tracks) {
        val videoGroup = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO }
        if (videoGroup == null) {
            _qualityState.value = VideoQualityState()
            return
        }
        val mediaGroup = videoGroup.mediaTrackGroup
        val options = mutableListOf<VideoQualityOption>()
        var selectedIndex = -1
        for (i in 0 until mediaGroup.length) {
            val format = mediaGroup.getFormat(i)
            options += VideoQualityOption(
                label = formatQualityLabel(format),
                trackGroupIndex = i,
                height = format.height,
                bitrate = format.peakBitrate.takeIf { it > 0 } ?: format.bitrate
            )
            if (videoGroup.isTrackSelected(i)) selectedIndex = i
        }
        // Sort highest quality first; ties broken by bitrate so 720p60 beats 720p30.
        options.sortWith(compareByDescending<VideoQualityOption> { it.height }.thenByDescending { it.bitrate })
        val player = nativePlayer
        val isAuto = player?.trackSelectionParameters?.overrides?.values?.none {
            it.mediaTrackGroup == mediaGroup
        } ?: true
        _qualityState.value = VideoQualityState(
            options = options,
            selectedTrackIndex = if (isAuto) null else selectedIndex.takeIf { it >= 0 },
            isAuto = isAuto
        )
    }

    fun toggleMute() {
        setMuted(!_muted.value)
    }

    fun setMuted(muted: Boolean) {
        _muted.value = muted
        nativePlayer?.volume = if (muted) 0f else 1f
        cachedWebView?.evaluateJavascript(
            "(window.__7tvVideoEl || document.querySelector('video')) && " +
                "((window.__7tvVideoEl || document.querySelector('video')).muted = $muted)",
            null
        )
    }

    fun selectQuality(option: VideoQualityOption?) {
        val player = nativePlayer ?: return
        val tracks = player.currentTracks
        val videoGroup = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO } ?: return
        val mediaGroup: TrackGroup = videoGroup.mediaTrackGroup
        val builder = player.trackSelectionParameters.buildUpon()
        // Clear any prior video override.
        builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        if (option != null) {
            builder.addOverride(
                TrackSelectionOverride(mediaGroup, listOf(option.trackGroupIndex))
            )
        }
        player.trackSelectionParameters = builder.build()
        refreshQualityState(player.currentTracks)
    }

    private fun formatQualityLabel(format: androidx.media3.common.Format): String {
        // Prefer the m3u8 NAME (parsed by ExoPlayer into Format.label) — this is
        // Twitch's own variant name like "720p60", "480p30", "audio_only", "chunked".
        val rawLabel = format.label?.trim()?.takeIf { it.isNotEmpty() }
        if (rawLabel != null) {
            return when {
                rawLabel.equals("chunked", ignoreCase = true) -> "Source"
                rawLabel.equals("audio_only", ignoreCase = true) -> "Audio only"
                else -> rawLabel
            }
        }
        // Fallback: synthesize "${height}p${fps}" from the format itself.
        val height = format.height
        val fps = format.frameRate
        if (height <= 0) {
            val bitrate = format.peakBitrate.takeIf { it > 0 } ?: format.bitrate
            if (bitrate > 0) return "${bitrate / 1000} kbps"
            return "Audio only"
        }
        val fpsSuffix = if (fps > 0f && fps.toInt() != 30) fps.toInt().toString() else ""
        return "${height}p$fpsSuffix"
    }

    private fun adTolerantLoadErrorPolicy(): LoadErrorHandlingPolicy =
        object : DefaultLoadErrorHandlingPolicy(AD_SEGMENT_RETRY_COUNT) {
            override fun getRetryDelayMsFor(
                loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo
            ): Long {
                val cause = loadErrorInfo.exception
                val isHttp403Or404 = cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException &&
                    (cause.responseCode == 403 || cause.responseCode == 404)
                if (isHttp403Or404) return AD_SEGMENT_SKIP_DELAY_MS
                return super.getRetryDelayMsFor(loadErrorInfo)
            }
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
        const val USER_AGENT = "7TVMobile"
        const val LIVE_TARGET_OFFSET_MS = 2_500L
        const val LIVE_MIN_PLAYBACK_SPEED = 0.97f
        const val LIVE_MAX_PLAYBACK_SPEED = 1.03f
        const val MIN_BUFFER_MS = 15_000
        const val MAX_BUFFER_MS = 50_000
        const val BUFFER_FOR_PLAYBACK_MS = 2_000
        const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 2_000
        const val AD_SEGMENT_RETRY_COUNT = 6
        const val AD_SEGMENT_SKIP_DELAY_MS = 500L
        const val AD_INFO_POLL_MS = 500L
        const val INIT_EMBED_PLAYBACK_SCRIPT = """
            (() => {
              if (window.__7tvInitPlayback) {
                window.__7tvInitPlayback();
                return;
              }

              window.__7tvWaitFor = (selector, timeout = 10000) => new Promise((resolve) => {
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

              // Inject CSS once to hide the Twitch embed thumbnail/poster
              // patch that appears near the play button.
              if (!window.__7tvStyleInjected) {
                window.__7tvStyleInjected = true;
                const style = document.createElement('style');
                style.textContent = [
                  '.preview-card-overlay',
                  '.channel-status-info',
                  '[data-a-target="player-overlay-click-handler"] img',
                  '[data-a-target="player-overlay-click-handler"] .tw-image',
                  '.player-overlay__image',
                  '.tw-transition-group',
                  'img.preview-image',
                  '.preview-image'
                ].join(',') + '{ display: none !important; }';
                document.head.appendChild(style);
              }

              window.__7tvInitPlayback = async () => {
                const video = await window.__7tvWaitFor('video');
                if (!video) return;

                window.__7tvVideoEl = video;
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

              window.__7tvInitPlayback();
            })();
        """
    }
}

data class VideoQualityOption(
    val label: String,
    val trackGroupIndex: Int,
    val height: Int,
    val bitrate: Int
)

data class VideoQualityState(
    val options: List<VideoQualityOption> = emptyList(),
    val selectedTrackIndex: Int? = null,
    val isAuto: Boolean = true
) {
    val currentLabel: String
        get() = when {
            isAuto -> "Auto"
            selectedTrackIndex != null -> options.firstOrNull { it.trackGroupIndex == selectedTrackIndex }?.label ?: "Auto"
            else -> "Auto"
        }
}

data class StreamPlayerUiState(
    val backend: StreamPlayerBackend = StreamPlayerBackend.Native,
    val loadState: StreamPlayerLoadState = StreamPlayerLoadState.Idle,
    val channelLogin: String? = null,
    val message: String? = null,
    val adInfo: AdPlaybackInfo? = null
)

data class AdPlaybackInfo(
    val remainingMs: Long? = null,
    val adIndex: Int? = null
) {
    val label: String
        get() = buildString {
            append("Advertisement playing")
            if (adIndex != null) append(" · Ad $adIndex")
            remainingMs?.let { append(" · ${formatAdTime(it)} left") }
        }
}

private fun formatAdTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    } else {
        "${seconds}s"
    }
}

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
