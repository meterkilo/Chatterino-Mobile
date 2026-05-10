package com.example.chatterinomobile.ui.player

import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.example.chatterinomobile.ui.channels.ActiveChannelState
import com.example.chatterinomobile.ui.theme.Twick

@Composable
fun TwitchStreamStage(
    activeChannel: ActiveChannelState,
    playerViewModel: StreamPlayerViewModel,
    modifier: Modifier = Modifier
) {
    val channelLogin = activeChannel.channelLogin
    val channel = activeChannel.channel
    val isOffline = channel != null && !channel.isLive
    val playerState by playerViewModel.uiState.collectAsState()

    var videoEnabled by rememberSaveable(channelLogin) { mutableStateOf(true) }
    var fullscreen by rememberSaveable(channelLogin) { mutableStateOf(false) }

    LaunchedEffect(channelLogin) {
        videoEnabled = true
        fullscreen = false
    }

    LaunchedEffect(channelLogin, isOffline, videoEnabled) {
        if (channelLogin != null && !isOffline && videoEnabled) {
            playerViewModel.playChannel(channelLogin)
        }
    }

    when {
        channelLogin == null -> StreamEmptyState(
            label = "stream",
            detail = "Join a channel to watch"
        )

        isOffline || !videoEnabled -> StreamPoster(
            activeChannel = activeChannel,
            actionLabel = if (isOffline) "Offline" else "Show stream",
            actionEnabled = !isOffline,
            onAction = { videoEnabled = true },
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
                    channelLogin = channelLogin,
                    playerViewModel = playerViewModel,
                    playerState = playerState,
                    fullscreen = false,
                    onFullscreen = { fullscreen = true },
                    onClose = {
                        videoEnabled = false
                        playerViewModel.stopPlayback()
                    },
                    modifier = modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
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
                        channelLogin = channelLogin,
                        playerViewModel = playerViewModel,
                        playerState = playerState,
                        fullscreen = true,
                        onFullscreen = { fullscreen = false },
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
    channelLogin: String,
    playerViewModel: StreamPlayerViewModel,
    playerState: StreamPlayerUiState,
    fullscreen: Boolean,
    onFullscreen: () -> Unit,
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

    Box(
        modifier = modifier.background(Color.Black),
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

        PlayerControls(
            fullscreen = fullscreen,
            onFullscreen = onFullscreen,
            onRefresh = {
                pageLoaded = false
                loadError = false
                playerViewModel.refreshCurrent()
            },
            onClose = onClose
        )
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
                                ?.evaluateJavascript("window.__chatterinoInitPlayback?.()", null)
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
private fun BoxScope.PlayerControls(
    fullscreen: Boolean,
    onFullscreen: () -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(6.dp)
            .background(Color.Black.copy(alpha = 0.50f)),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StreamIconButton(
            icon = Icons.Filled.Refresh,
            contentDescription = "Refresh stream",
            onClick = onRefresh
        )
        StreamIconButton(
            icon = if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
            contentDescription = if (fullscreen) "Exit fullscreen" else "Fullscreen",
            onClick = onFullscreen
        )
        StreamIconButton(
            icon = Icons.Filled.Close,
            contentDescription = "Close stream",
            onClick = onClose
        )
    }
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
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun com.example.chatterinomobile.data.model.Channel?.displayNameOrLogin(login: String): String =
    this?.displayName?.takeIf { it.isNotBlank() } ?: login

private const val RESUME_PLAYBACK_DELAY_MS = 100L

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
