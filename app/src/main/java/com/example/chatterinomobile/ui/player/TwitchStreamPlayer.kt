package com.example.chatterinomobile.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnAttach
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.chatterinomobile.ui.channels.ActiveChannelState
import com.example.chatterinomobile.ui.common.SkeletonBox
import com.example.chatterinomobile.ui.common.rememberSkeletonBrush
import com.example.chatterinomobile.ui.common.rememberSoftHaptic
import com.example.chatterinomobile.ui.theme.Twick
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Compact channel info bar shown underneath the player.
 *
 * Three tight rows: avatar + display name (with optional partner badge), stream
 * title, and viewer count. Used as a persistent strip below the player so the
 * focused-player overlay can stay minimal (PiP / close / controls only).
 */
@Composable
fun ChannelInfoBar(
    activeChannel: ActiveChannelState,
    modifier: Modifier = Modifier
) {
    val channel = activeChannel.channel
    val channelLogin = activeChannel.channelLogin ?: return
    val displayName = channel.displayNameOrLogin(channelLogin)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Twick.Bg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Twick.Accent),
            contentAlignment = Alignment.Center
        ) {
            if (!channel?.profileImageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = channel?.profileImageUrl,
                    contentDescription = displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = displayName.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayName,
                    color = Twick.Ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (channel?.isPartner == true) {
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = "Partner",
                        tint = Twick.Accent,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            channel?.title?.takeIf { it.isNotBlank() }?.let { title ->
                Text(
                    text = title,
                    color = Twick.Ink2,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if ((channel?.viewerCount ?: 0) > 0) {
                Text(
                    text = "${formatViewerCount(channel?.viewerCount ?: 0)} viewers",
                    color = Twick.Ink3,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun TwitchStreamStage(
    activeChannel: ActiveChannelState,
    playerViewModel: StreamPlayerViewModel,
    theaterMode: Boolean = false,
    onTheaterToggle: (() -> Unit)? = null,
    videoVisible: Boolean = true,
    onVideoVisibleChange: ((Boolean) -> Unit)? = null,
    onEnterPip: ((Rect?) -> Unit)? = null,
    onPlayerBoundsChanged: ((Rect?) -> Unit)? = null,
    onFocusOverlayVisibleChange: ((Boolean) -> Unit)? = null,
    fillBounds: Boolean = false,
    pipMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val channelLogin = activeChannel.channelLogin
    val channel = activeChannel.channel
    val isOffline = channel != null && !channel.isLive
    val playerState by playerViewModel.uiState.collectAsState()

    var fullscreen by rememberSaveable(channelLogin) { mutableStateOf(false) }
    val activity = LocalActivity.current

    LaunchedEffect(channelLogin) {
        fullscreen = false
    }

    LaunchedEffect(fullscreen, theaterMode, pipMode, activity) {
        val act = activity as? Activity
        if (act != null) {
            act.requestedOrientation = if (pipMode) {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            } else if (fullscreen || theaterMode) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    DisposableEffect(activity) {
        onDispose {
            (activity as? Activity)?.requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    LaunchedEffect(channelLogin, isOffline, videoVisible) {
        if (channelLogin != null && !isOffline && videoVisible) {
            playerViewModel.playChannel(channelLogin)
        }
    }

    when {
        channelLogin == null -> StreamEmptyState(
            label = "stream",
            detail = "Join a channel to watch"
        )

        isOffline || !videoVisible -> StreamPoster(
            activeChannel = activeChannel,
            actionLabel = if (isOffline) "Offline" else "Show stream",
            actionEnabled = !isOffline,
            onAction = { onVideoVisibleChange?.invoke(true) },
            modifier = modifier
        )

        else -> {
            if (fullscreen) {
                LaunchedEffect(fullscreen) {
                    onFocusOverlayVisibleChange?.invoke(false)
                }
                StreamFullscreenPlaceholder(
                    channelName = channel.displayNameOrLogin(channelLogin),
                    onClick = { fullscreen = false },
                    modifier = modifier
                )
            } else {
                StreamPlayerFrame(
                    channelLogin = channelLogin,
                    playerViewModel = playerViewModel,
                    playerState = playerState,
                    fullscreen = false,
                    theaterMode = theaterMode,
                    onFullscreen = {
                        fullscreen = true
                    },
                    onTheaterToggle = onTheaterToggle,
                    onEnterPip = onEnterPip,
                    onPlayerBoundsChanged = onPlayerBoundsChanged,
                    onFocusOverlayVisibleChange = onFocusOverlayVisibleChange,
                    pipMode = pipMode,
                    onClose = {
                        onVideoVisibleChange?.invoke(false)
                        playerViewModel.stopPlayback()
                    },
                    modifier = if (theaterMode || fillBounds) {
                        modifier.fillMaxSize()
                    } else {
                        modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    }
                )
            }

            if (fullscreen) {
                BackHandler { fullscreen = false }
                Dialog(
                    onDismissRequest = { fullscreen = false },
                    properties = DialogProperties(
                        usePlatformDefaultWidth = false,
                        decorFitsSystemWindows = false
                    )
                ) {
                    ImmersiveFullscreenEffect(enabled = true)
                    StreamPlayerFrame(
                        channelLogin = channelLogin,
                        playerViewModel = playerViewModel,
                        playerState = playerState,
                        fullscreen = true,
                        theaterMode = theaterMode,
                        onFullscreen = {
                            fullscreen = false
                        },
                        onTheaterToggle = onTheaterToggle?.let { toggle ->
                            {
                                toggle()
                                fullscreen = false
                            }
                        },
                        onEnterPip = onEnterPip,
                        onPlayerBoundsChanged = onPlayerBoundsChanged,
                        onFocusOverlayVisibleChange = null,
                        pipMode = pipMode,
                        onClose = { fullscreen = false },
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamPlayerFrame(
    channelLogin: String,
    playerViewModel: StreamPlayerViewModel,
    playerState: StreamPlayerUiState,
    fullscreen: Boolean,
    theaterMode: Boolean,
    onFullscreen: () -> Unit,
    onTheaterToggle: (() -> Unit)?,
    onEnterPip: ((Rect?) -> Unit)?,
    onPlayerBoundsChanged: ((Rect?) -> Unit)?,
    onFocusOverlayVisibleChange: ((Boolean) -> Unit)?,
    pipMode: Boolean = false,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pageLoaded by remember(channelLogin) { mutableStateOf(playerViewModel.hasWebViewBeenAttached) }
    var loadError by remember(channelLogin) { mutableStateOf(false) }
    val showingNative = playerState.backend == StreamPlayerBackend.Native
    val showingEmbed = playerState.backend == StreamPlayerBackend.TwitchEmbed
    val isLoading = if (showingEmbed) {
        !pageLoaded && !loadError
    } else {
        playerState.loadState == StreamPlayerLoadState.Loading
    }
    var overlayVisible by remember(channelLogin) { mutableStateOf(true) }
    var overlayPulse by remember(channelLogin) { mutableStateOf(0) }
    val qualityState by playerViewModel.qualityState.collectAsState()
    val muted by playerViewModel.muted.collectAsState()
    var qualityMenuOpen by remember(channelLogin) { mutableStateOf(false) }
    var playerBounds by remember { mutableStateOf<Rect?>(null) }
    val focusOverlayVisible = overlayVisible || isLoading || loadError || qualityMenuOpen

    LaunchedEffect(channelLogin) {
        overlayVisible = true
        delay(PLAYER_OVERLAY_AUTO_HIDE_MS)
        overlayVisible = false
    }

    LaunchedEffect(overlayPulse) {
        if (overlayPulse > 0) {
            overlayVisible = true
            delay(PLAYER_OVERLAY_AUTO_HIDE_MS)
            overlayVisible = false
        }
    }

    LaunchedEffect(focusOverlayVisible, onFocusOverlayVisibleChange) {
        onFocusOverlayVisibleChange?.invoke(focusOverlayVisible)
    }

    DisposableEffect(onFocusOverlayVisibleChange) {
        onDispose { onFocusOverlayVisibleChange?.invoke(false) }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                val rect = Rect(
                    bounds.left.roundToInt(),
                    bounds.top.roundToInt(),
                    bounds.right.roundToInt(),
                    bounds.bottom.roundToInt()
                )
                playerBounds = rect
                onPlayerBoundsChanged?.invoke(rect)
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            showingNative -> NativePlayerSurface(
                playerViewModel = playerViewModel,
                modifier = Modifier.fillMaxSize()
            )

            showingEmbed -> TwitchWebViewSurface(
                channelLogin = channelLogin,
                playerViewModel = playerViewModel,
                shouldAttach = pageLoaded || playerViewModel.hasWebViewBeenAttached,
                onPageLoaded = {
                    pageLoaded = true
                    loadError = false
                },
                onLoadError = {
                    pageLoaded = false
                    loadError = true
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isLoading && !loadError) {
            StreamLoadingSkeleton(modifier = Modifier.matchParentSize())
        }

        if (loadError) {
            Text(
                text = "Stream failed to load",
                color = Twick.Ink2,
                fontSize = 12.sp
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { overlayPulse += 1 }
        )

        playerState.adInfo?.let { adInfo ->
            AdPlaybackBadge(
                adInfo = adInfo,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = if (overlayVisible || qualityMenuOpen) 54.dp else 8.dp)
            )
        }

        if (focusOverlayVisible && !pipMode) {
            PlayerFocusOverlay(
                fullscreen = fullscreen,
                theaterMode = theaterMode,
                muted = muted,
                onMuteToggle = {
                    playerViewModel.toggleMute()
                    overlayPulse += 1
                },
                onFullscreen = onFullscreen,
                onTheaterToggle = onTheaterToggle,
                onEnterPip = onEnterPip?.let { enterPip -> { enterPip(playerBounds) } },
                qualityState = qualityState,
                qualityMenuOpen = qualityMenuOpen,
                onQualityClick = {
                    qualityMenuOpen = !qualityMenuOpen
                    overlayPulse += 1
                },
                onQualitySelected = { option ->
                    playerViewModel.selectQuality(option)
                    qualityMenuOpen = false
                },
                onDismissQualityMenu = { qualityMenuOpen = false },
                onClose = onClose
            )
        }
    }
}

@Composable
private fun StreamLoadingSkeleton(modifier: Modifier = Modifier) {
    val brush = rememberSkeletonBrush(alpha = 0.72f)
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.82f))
            .padding(14.dp)
    ) {
        SkeletonBox(
            brush = brush,
            modifier = Modifier
                .align(Alignment.TopStart)
                .width(88.dp)
                .height(22.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp)
        )
        SkeletonBox(
            brush = brush,
            modifier = Modifier
                .align(Alignment.Center)
                .size(58.dp),
            shape = CircleShape
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.72f),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            SkeletonBox(
                brush = brush,
                modifier = Modifier
                    .fillMaxWidth(0.74f)
                    .height(12.dp)
            )
            SkeletonBox(
                brush = brush,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(9.dp)
            )
        }
        Row(
            modifier = Modifier.align(Alignment.BottomEnd),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            repeat(3) {
                SkeletonBox(
                    brush = brush,
                    modifier = Modifier.size(28.dp),
                    shape = CircleShape
                )
            }
        }
    }
}

@Composable
@androidx.annotation.OptIn(UnstableApi::class)
private fun NativePlayerSurface(
    playerViewModel: StreamPlayerViewModel,
    modifier: Modifier = Modifier
) {
    val player = remember(playerViewModel) { playerViewModel.getOrCreateNativePlayer() }
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                this.player = player
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { playerView ->
            if (playerView.player !== player) playerView.player = player
        },
        modifier = modifier
    )

    DisposableEffect(player) {
        onDispose { }
    }
}

@Composable
private fun TwitchWebViewSurface(
    channelLogin: String,
    playerViewModel: StreamPlayerViewModel,
    shouldAttach: Boolean,
    onPageLoaded: () -> Unit,
    onLoadError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val webView = remember(playerViewModel) { playerViewModel.getOrCreateWebView() }

    DisposableEffect(channelLogin, webView) {
                webView.webViewClient = GuardedTwitchWebViewClient(
                    onPageFinished = {
                        onPageLoaded()
                        playerViewModel.initializeEmbedPlayback()
                    },
                    onError = onLoadError
                )
        onDispose { }
    }

    if (shouldAttach) {
        AndroidView(
            factory = {
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                if (!playerViewModel.hasWebViewBeenAttached) {
                    playerViewModel.hasWebViewBeenAttached = true
                } else {
                    webView.doOnAttach { view ->
                        view.postDelayed({
                            (view as? WebView)
                                ?.evaluateJavascript("window.__7tvInitPlayback?.()", null)
                        }, RESUME_PLAYBACK_DELAY_MS)
                    }
                }
                webView
            },
            update = {
                webView.webViewClient = GuardedTwitchWebViewClient(
                    onPageFinished = {
                        onPageLoaded()
                        playerViewModel.initializeEmbedPlayback()
                    },
                    onError = onLoadError
                )
                playerViewModel.initializeEmbedPlayback()
            },
            modifier = modifier.graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
        )
    } else {
        Box(modifier = modifier)
    }

    DisposableEffect(Unit) {
        onDispose {
            (webView.parent as? ViewGroup)?.removeView(webView)
        }
    }
}

@Composable
private fun BoxScope.PlayerFocusOverlay(
    fullscreen: Boolean,
    theaterMode: Boolean,
    muted: Boolean,
    onMuteToggle: () -> Unit,
    onFullscreen: () -> Unit,
    onTheaterToggle: (() -> Unit)?,
    onEnterPip: (() -> Unit)?,
    qualityState: VideoQualityState,
    qualityMenuOpen: Boolean,
    onQualityClick: () -> Unit,
    onQualitySelected: (VideoQualityOption?) -> Unit,
    onDismissQualityMenu: () -> Unit,
    onClose: () -> Unit
) {
    // Top gradient — light scrim behind the PiP / close buttons.
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(56.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.55f),
                        Color.Transparent
                    )
                )
            )
    )

    // Bottom gradient — darkens the bottom edge behind the controls row.
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(72.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.65f)
                    )
                )
            )
    )

    // Top-left Picture-in-Picture button.
    if (onEnterPip != null) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 6.dp, top = 6.dp)
        ) {
            StreamIconButton(
                icon = Icons.Filled.PictureInPictureAlt,
                contentDescription = "Picture in picture",
                onClick = onEnterPip
            )
        }
    }

    // Top-right close button.
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(end = 6.dp, top = 6.dp)
    ) {
        StreamIconButton(
            icon = Icons.Filled.Close,
            contentDescription = "Close stream",
            onClick = onClose
        )
    }

    // Outside-tap dismisser when the quality menu is open.
    if (qualityMenuOpen) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissQualityMenu
                )
        )
    }

    // Bottom-end controls — fully transparent toolbar.
    // Order: Settings, Volume, [Theater only in fullscreen], Fullscreen.
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 6.dp, bottom = 6.dp)
    ) {
        Row(
            modifier = Modifier.align(Alignment.BottomEnd),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StreamIconButton(
                icon = Icons.Filled.Settings,
                contentDescription = "Video quality",
                selected = qualityMenuOpen,
                onClick = onQualityClick
            )
            StreamIconButton(
                icon = if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = if (muted) "Unmute" else "Mute",
                onClick = onMuteToggle
            )
            if (onTheaterToggle != null && fullscreen) {
                StreamIconButton(
                    icon = TheaterModeIcon,
                    contentDescription = if (theaterMode) "Exit theater mode" else "Theater mode",
                    selected = theaterMode,
                    onClick = onTheaterToggle
                )
            }
            StreamIconButton(
                icon = if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                contentDescription = if (fullscreen) "Exit fullscreen" else "Fullscreen",
                onClick = onFullscreen
            )
        }

        if (qualityMenuOpen) {
            Box(
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                QualitySelectorPopup(
                    qualityState = qualityState,
                    onQualitySelected = onQualitySelected
                )
            }
        }
    }
}

@Composable
private fun AdPlaybackBadge(
    adInfo: AdPlaybackInfo,
    modifier: Modifier = Modifier
) {
    Text(
        text = adInfo.label,
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.52f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    )
}

@Composable
private fun QualitySelectorPopup(
    qualityState: VideoQualityState,
    onQualitySelected: (VideoQualityOption?) -> Unit
) {
    // Anchored above the gear/controls row by the parent's bottom padding.
    Column(
        modifier = Modifier
            .padding(bottom = 40.dp)
            .background(
                color = Color.Black.copy(alpha = 0.72f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
            .padding(vertical = 6.dp, horizontal = 4.dp)
    ) {
        QualityRow(
            label = "Auto",
            selected = qualityState.isAuto,
            onClick = { onQualitySelected(null) }
        )
        qualityState.options.forEach { option ->
            QualityRow(
                label = option.label,
                selected = !qualityState.isAuto && qualityState.selectedTrackIndex == option.trackGroupIndex,
                onClick = { onQualitySelected(option) }
            )
        }
        if (qualityState.options.isEmpty()) {
            Text(
                text = "No qualities available",
                color = Twick.Ink3,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun QualityRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val haptic = rememberSoftHaptic()
    Text(
        text = label,
        color = if (selected) Twick.Accent else Color.White,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 12.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (!selected) haptic()
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 8.dp)
    )
}

@Composable
private fun StreamFullscreenPlaceholder(
    channelName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$channelName is fullscreen",
            color = Twick.Ink3,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun StreamIconButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val haptic = rememberSoftHaptic()
    IconButton(
        onClick = {
            haptic()
            onClick()
        },
        modifier = Modifier.size(32.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (selected) Twick.Accent else Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ImmersiveFullscreenEffect(enabled: Boolean) {
    val view = LocalView.current
    val activity = LocalActivity.current as? Activity

    DisposableEffect(enabled, view, activity) {
        if (!enabled) {
            onDispose { }
        } else {
            val dialogWindow = (view.parent as? DialogWindowProvider)?.window
            val windows = listOfNotNull(dialogWindow, activity?.window).distinct()
            val controllers = windows.map { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            }

            onDispose {
                controllers.forEach { it.show(WindowInsetsCompat.Type.systemBars()) }
            }
        }
    }
}

private val TheaterModeIcon: ImageVector
    get() {
        if (_theaterModeIcon != null) return _theaterModeIcon!!
        _theaterModeIcon = ImageVector.Builder(
            name = "TheaterMode",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 5f)
                lineTo(15f, 5f)
                lineTo(15f, 7f)
                lineTo(3f, 7f)
                close()
                moveTo(3f, 17f)
                lineTo(15f, 17f)
                lineTo(15f, 19f)
                lineTo(3f, 19f)
                close()
                moveTo(3f, 5f)
                lineTo(5f, 5f)
                lineTo(5f, 19f)
                lineTo(3f, 19f)
                close()
                moveTo(13f, 5f)
                lineTo(15f, 5f)
                lineTo(15f, 19f)
                lineTo(13f, 19f)
                close()
                moveTo(17f, 8f)
                lineTo(22f, 8f)
                lineTo(22f, 10f)
                lineTo(17f, 10f)
                close()
                moveTo(17f, 14f)
                lineTo(22f, 14f)
                lineTo(22f, 16f)
                lineTo(17f, 16f)
                close()
                moveTo(17f, 8f)
                lineTo(19f, 8f)
                lineTo(19f, 16f)
                lineTo(17f, 16f)
                close()
                moveTo(20f, 8f)
                lineTo(22f, 8f)
                lineTo(22f, 16f)
                lineTo(20f, 16f)
                close()
            }
        }.build()
        return _theaterModeIcon!!
    }

private var _theaterModeIcon: ImageVector? = null

private fun com.example.chatterinomobile.data.model.Channel?.displayNameOrLogin(login: String): String =
    this?.displayName?.takeIf { it.isNotBlank() } ?: login

private const val RESUME_PLAYBACK_DELAY_MS = 100L
private const val PLAYER_OVERLAY_AUTO_HIDE_MS = 4_000L

@Composable
private fun StreamPoster(
    activeChannel: ActiveChannelState,
    actionLabel: String,
    actionEnabled: Boolean,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val channel = activeChannel.channel
    val haptic = rememberSoftHaptic()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val thumbnailUrl = channel?.thumbnailUrl?.replace("{width}", "1280")?.replace("{height}", "720")
        if (!thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = channel.displayName,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.20f),
                                Color.Black.copy(alpha = 0.78f)
                            )
                        )
                    )
            )
        }

        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                enabled = actionEnabled,
                onClick = {
                    haptic()
                    onAction()
                },
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = if (actionEnabled) Twick.Accent else Twick.S3,
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = actionLabel,
                    tint = if (actionEnabled) Color.White else Twick.Ink4,
                    modifier = Modifier.size(26.dp)
                )
            }
            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = channel?.displayName ?: activeChannel.channelLogin ?: "channel",
                    color = Twick.Ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = actionLabel,
                    color = if (actionEnabled) Twick.Ink2 else Twick.Ink3,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatViewerCount(count: Int): String =
    when {
        count >= 1_000_000 -> "${count / 1_000_000}.${(count % 1_000_000) / 100_000}M"
        count >= 1_000 -> "${count / 1_000}.${(count % 1_000) / 100}K"
        else -> count.toString()
    }

@Composable
private fun StreamEmptyState(
    label: String,
    detail: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF2A1F3D), Color(0xFF120A1F))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.2f),
                fontSize = 11.sp
            )
            Text(
                text = detail,
                color = Color.White.copy(alpha = 0.34f),
                fontSize = 10.sp
            )
        }
    }
}
