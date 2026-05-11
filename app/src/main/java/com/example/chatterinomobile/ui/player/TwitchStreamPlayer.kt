package com.example.chatterinomobile.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VideoLabel
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.doOnAttach
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.chatterinomobile.data.model.Channel
import com.example.chatterinomobile.ui.channels.ActiveChannelState
import com.example.chatterinomobile.ui.theme.Twick
import kotlinx.coroutines.delay

@Composable
fun TwitchStreamStage(
    activeChannel: ActiveChannelState,
    playerViewModel: StreamPlayerViewModel,
    theaterMode: Boolean = false,
    onTheaterToggle: (() -> Unit)? = null,
    videoVisible: Boolean = true,
    onVideoVisibleChange: ((Boolean) -> Unit)? = null,
    fillBounds: Boolean = false,
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

    // Force landscape when theater/fullscreen is active; restore on exit.
    DisposableEffect(fullscreen, theaterMode, activity) {
        val act = activity as? Activity
        if (act != null) {
            act.requestedOrientation = if (fullscreen || theaterMode) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
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
                StreamFullscreenPlaceholder(
                    channelName = channel.displayNameOrLogin(channelLogin),
                    onClick = { fullscreen = false },
                    modifier = modifier
                )
            } else {
                StreamPlayerFrame(
                    channel = channel,
                    channelLogin = channelLogin,
                    playerViewModel = playerViewModel,
                    playerState = playerState,
                    fullscreen = false,
                    theaterMode = theaterMode,
                    onFullscreen = {
                        // If currently in theater mode, exit theater and revert to vertical;
                        // otherwise enter fullscreen.
                        if (theaterMode) {
                            onTheaterToggle?.invoke()
                        } else {
                            fullscreen = true
                        }
                    },
                    onTheaterToggle = onTheaterToggle,
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
                    StreamPlayerFrame(
                        channel = channel,
                        channelLogin = channelLogin,
                        playerViewModel = playerViewModel,
                        playerState = playerState,
                        fullscreen = true,
                        theaterMode = theaterMode,
                        onFullscreen = {
                            // Exiting fullscreen: if we were also in theater mode,
                            // drop out of theater too so we revert to the vertical layout.
                            if (theaterMode) onTheaterToggle?.invoke()
                            fullscreen = false
                        },
                        onTheaterToggle = onTheaterToggle,
                        onClose = { fullscreen = false },
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .systemBarsPadding()
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamPlayerFrame(
    channel: Channel?,
    channelLogin: String,
    playerViewModel: StreamPlayerViewModel,
    playerState: StreamPlayerUiState,
    fullscreen: Boolean,
    theaterMode: Boolean,
    onFullscreen: () -> Unit,
    onTheaterToggle: (() -> Unit)?,
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

    Box(
        modifier = modifier
            .background(Color.Black),
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
            CircularProgressIndicator(
                color = Twick.Accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(28.dp)
            )
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

        if (overlayVisible || isLoading || loadError || qualityMenuOpen) {
            PlayerFocusOverlay(
                channel = channel,
                channelLogin = channelLogin,
                fullscreen = fullscreen,
                theaterMode = theaterMode,
                muted = muted,
                onMuteToggle = {
                    playerViewModel.toggleMute()
                    overlayPulse += 1
                },
                onFullscreen = onFullscreen,
                onTheaterToggle = onTheaterToggle,
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
    channel: Channel?,
    channelLogin: String,
    fullscreen: Boolean,
    theaterMode: Boolean,
    muted: Boolean,
    onMuteToggle: () -> Unit,
    onFullscreen: () -> Unit,
    onTheaterToggle: (() -> Unit)?,
    qualityState: VideoQualityState,
    qualityMenuOpen: Boolean,
    onQualityClick: () -> Unit,
    onQualitySelected: (VideoQualityOption?) -> Unit,
    onDismissQualityMenu: () -> Unit,
    onClose: () -> Unit
) {
    val displayName = channel.displayNameOrLogin(channelLogin)

    // Top gradient — darkens the top edge so the avatar/metadata is legible.
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(96.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.65f),
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

    // Top-left channel info — tight rows, all white text.
    Row(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = 12.dp, top = 8.dp, end = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
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
                    fontSize = 12.sp
                )
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayName,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
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
                    color = Color.White,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if ((channel?.viewerCount ?: 0) > 0) {
                Text(
                    text = "${formatViewerCount(channel?.viewerCount ?: 0)} viewers",
                    color = Color.White,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
    // Order: Settings, Volume, [Theater if fullscreen or theater], Fullscreen.
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
            // Theater button: shown whenever the player is in fullscreen or
            // already in theater mode (so the user can toggle it off).
            if (onTheaterToggle != null && (fullscreen || theaterMode)) {
                StreamIconButton(
                    icon = Icons.Filled.VideoLabel,
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
    Text(
        text = label,
        color = if (selected) Twick.Accent else Color.White,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 12.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
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
                onClick = onAction,
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
