package com.example.chatterinomobile.ui.chat

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Verified
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatterinomobile.data.model.RoomState
import com.example.chatterinomobile.data.repository.EmoteCatalog
import com.example.chatterinomobile.ui.channels.ActiveChannelState
import com.example.chatterinomobile.ui.channels.ChannelTabsViewModel
import com.example.chatterinomobile.ui.chat.components.ChatInputBar
import com.example.chatterinomobile.ui.chat.components.ChatList
import com.example.chatterinomobile.ui.chat.components.CompletionItem
import com.example.chatterinomobile.ui.chat.components.EmoteInsertion
import com.example.chatterinomobile.ui.chat.components.EmotePickerSheet
import com.example.chatterinomobile.MainActivity
import com.example.chatterinomobile.ui.player.StreamPlayerViewModel
import com.example.chatterinomobile.ui.player.TwitchStreamStage
import androidx.activity.compose.LocalActivity
import com.example.chatterinomobile.ui.common.SkeletonBox
import com.example.chatterinomobile.ui.common.rememberSkeletonBrush
import com.example.chatterinomobile.ui.theme.Twick
import coil.compose.AsyncImage

@Composable
fun ChatRoute(
    chatViewModel: ChatViewModel,
    tabsViewModel: ChannelTabsViewModel,
    streamPlayerViewModel: StreamPlayerViewModel,
    isLoggedIn: Boolean,
    authUserId: String?,
    authLogin: String?,
    onBack: () -> Unit
) {
    val chatState by chatViewModel.uiState.collectAsState()
    val activeChannel by tabsViewModel.activeChannel.collectAsState()
    val autocompleteState by chatViewModel.autocomplete.collectAsState()
    val emoteCatalog by chatViewModel.emoteCatalog.collectAsState()

    ChatScreen(
        state = chatState,
        messages = chatViewModel.recentMessages,
        activeChannel = activeChannel,
        isLoggedIn = isLoggedIn,
        authUserId = authUserId,
        authLogin = authLogin,
        autocompleteState = autocompleteState,
        emoteCatalog = emoteCatalog,
        streamPlayerViewModel = streamPlayerViewModel,
        onAutocompleteQueryChanged = { query ->
            chatViewModel.onAutocompleteQuery(
                query = query,
                broadcasterLogin = activeChannel.channelLogin,
                broadcasterDisplayName = activeChannel.channel?.displayName
            )
        },
        onEmotePickerOpen = chatViewModel::refreshEmoteCatalog,
        onSend = chatViewModel::sendMessage,
        onCancelReply = chatViewModel::cancelReply,
        onReplyToMessage = chatViewModel::beginReply,
        onBack = {
            streamPlayerViewModel.clear()
            chatViewModel.stopActiveChannel()
            tabsViewModel.stopActiveChannel()
            onBack()
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            streamPlayerViewModel.clear()
            chatViewModel.stopActiveChannel()
            tabsViewModel.stopActiveChannel()
        }
    }
}

@Composable
@SuppressLint("UnusedBoxWithConstraintsScope")
fun ChatScreen(
    state: ChatUiState,
    messages: List<com.example.chatterinomobile.data.model.ChatMessage>,
    activeChannel: ActiveChannelState,
    isLoggedIn: Boolean,
    authUserId: String?,
    authLogin: String?,
    autocompleteState: EmoteAutocompleteState,
    emoteCatalog: EmoteCatalog,
    streamPlayerViewModel: StreamPlayerViewModel,
    onAutocompleteQueryChanged: (AutocompleteQuery?) -> Unit,
    onEmotePickerOpen: () -> Unit,
    onSend: (String) -> Unit,
    onCancelReply: () -> Unit,
    onReplyToMessage: (com.example.chatterinomobile.data.model.ChatMessage) -> Unit,
    onBack: () -> Unit
) {
    val canSend = activeChannel.channelLogin != null && isLoggedIn
    val hint = composeHint(activeChannel, isLoggedIn)
    val chatIsLoading = activeChannel.channelLogin != null &&
        messages.isEmpty() &&
        activeChannel.hydration?.isReady != true &&
        activeChannel.hydration?.errorMessage == null
    val inputEnabled = canSend && !chatIsLoading
    val inputHint = if (chatIsLoading) "Loading chat" else hint
    val focusManager = LocalFocusManager.current

    var pickerOpen by rememberSaveable(activeChannel.channelLogin) { mutableStateOf(false) }
    var pendingInsertion by remember { mutableStateOf<EmoteInsertion?>(null) }
    var theaterMode by rememberSaveable(activeChannel.channelLogin) { mutableStateOf(false) }
    var videoVisible by rememberSaveable(activeChannel.channelLogin) { mutableStateOf(true) }
    var chatFullscreen by rememberSaveable(activeChannel.channelLogin) { mutableStateOf(false) }
    var verticalPlayerFraction by rememberSaveable(activeChannel.channelLogin) {
        mutableStateOf(DEFAULT_VERTICAL_PLAYER_FRACTION)
    }
    var horizontalPlayerFraction by rememberSaveable(activeChannel.channelLogin) {
        mutableStateOf(DEFAULT_HORIZONTAL_PLAYER_FRACTION)
    }
    var playerChromeVisible by remember(activeChannel.channelLogin) { mutableStateOf(false) }
    val channelIsKnownOffline = activeChannel.channel?.isLive == false

    LaunchedEffect(activeChannel.channelLogin, channelIsKnownOffline) {
        if (channelIsKnownOffline) {
            theaterMode = false
            chatFullscreen = false
            streamPlayerViewModel.stopPlayback()
        }
    }

    fun enterChatFullscreen() {
        videoVisible = false
        chatFullscreen = true
        theaterMode = false
        streamPlayerViewModel.stopPlayback()
    }

    fun restoreDefaultPlayer() {
        videoVisible = true
        chatFullscreen = false
        theaterMode = false
        verticalPlayerFraction = DEFAULT_VERTICAL_PLAYER_FRACTION
        horizontalPlayerFraction = DEFAULT_HORIZONTAL_PLAYER_FRACTION
        if (!channelIsKnownOffline) {
            activeChannel.channelLogin?.let(streamPlayerViewModel::playChannel)
        }
    }

    BackHandler(enabled = chatFullscreen) {
        restoreDefaultPlayer()
    }

    val activity = LocalActivity.current
    var playerPipSourceRect by remember { mutableStateOf<Rect?>(null) }
    val onEnterPip: ((Rect?) -> Unit)? = remember(activity) {
        (activity as? MainActivity)?.let { mainActivity ->
            { sourceRectHint: Rect? ->
                mainActivity.enterPlayerPip(sourceRectHint ?: playerPipSourceRect)
            }
        }
    }

    val autocompleteResults: List<CompletionItem> = when (autocompleteState) {
        is EmoteAutocompleteState.Emotes -> autocompleteState.results.map(CompletionItem::Emote)
        is EmoteAutocompleteState.Users -> autocompleteState.results.map(CompletionItem::User)
        EmoteAutocompleteState.Hidden -> emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Twick.Bg)
    ) {
        if (chatFullscreen || !videoVisible || channelIsKnownOffline) {
            // Player is removed: show the diagonal "restore player" arrow when
            // we can bring the player back (live channel, currently removed).
            // Offline channels can't restore — fall back to a plain back arrow.
            val canRestorePlayer = !channelIsKnownOffline &&
                (chatFullscreen || !videoVisible) &&
                activeChannel.channelLogin != null
            StreamerMetaRow(
                activeChannel = activeChannel,
                onBack = if (canRestorePlayer) ({ restoreDefaultPlayer() }) else onBack,
                restorePlayerMode = canRestorePlayer
            )
            ChatMessagePane(
                state = state,
                messages = messages,
                activeChannel = activeChannel,
                authLogin = authLogin,
                focusManager = focusManager,
                onReplyToMessage = onReplyToMessage,
                isLoading = chatIsLoading,
                modifier = Modifier.weight(1f)
            )
            ChatInputBar(
                enabled = inputEnabled,
                hint = if (chatFullscreen && !chatIsLoading) "$inputHint - press Back to restore video" else inputHint,
                message = state.sendErrorMessage,
                messageIsError = state.sendErrorMessage != null,
                onSend = onSend,
                pendingReply = state.pendingReply,
                onCancelReply = onCancelReply,
                autocompleteResults = autocompleteResults,
                onAutocompleteQueryChanged = onAutocompleteQueryChanged,
                onEmotePicker = {
                    onEmotePickerOpen()
                    pickerOpen = true
                },
                insertEmoteRequest = pendingInsertion,
                onInsertEmoteRequestConsumed = { pendingInsertion = null }
            )
        } else if (theaterMode && activeChannel.channelLogin != null) {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val playerWidth = maxWidth * horizontalPlayerFraction
                val chatWidth = maxWidth - playerWidth - THEATER_RESIZE_HANDLE_WIDTH
                val availableWidth = maxWidth.value.coerceAtLeast(1f)
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .width(playerWidth.coerceAtLeast(0.dp))
                            .fillMaxHeight()
                    ) {
                        TwitchStreamStage(
                            activeChannel = activeChannel,
                            playerViewModel = streamPlayerViewModel,
                            theaterMode = true,
                            onTheaterToggle = { theaterMode = false },
                            videoVisible = videoVisible,
                            onVideoVisibleChange = { visible ->
                                if (visible) videoVisible = true else enterChatFullscreen()
                            },
                            onEnterPip = onEnterPip,
                            onPlayerBoundsChanged = { playerPipSourceRect = it },
                            onFocusOverlayVisibleChange = { playerChromeVisible = it },
                            fillBounds = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                        )
                    }
                    ResizeHandle(
                        orientation = ResizeHandleOrientation.Vertical,
                        modifier = Modifier
                            .width(THEATER_RESIZE_HANDLE_WIDTH)
                            .fillMaxHeight(),
                        onDrag = { drag ->
                            val next = horizontalPlayerFraction + drag / availableWidth
                            if (next <= THEATER_HIDE_PLAYER_FRACTION) {
                                enterChatFullscreen()
                            } else {
                                horizontalPlayerFraction = next.coerceIn(
                                    MIN_THEATER_PLAYER_FRACTION,
                                    MAX_THEATER_PLAYER_FRACTION
                                )
                            }
                        }
                    )
                    Column(
                        modifier = Modifier
                            .width(chatWidth.coerceAtLeast(0.dp))
                            .fillMaxHeight()
                    ) {
                        if (!playerChromeVisible) {
                            ChatBackBar(onBack = onBack)
                        }
                        ChatMessagePane(
                            state = state,
                            messages = messages,
                            activeChannel = activeChannel,
                            authLogin = authLogin,
                            focusManager = focusManager,
                            onReplyToMessage = onReplyToMessage,
                            isLoading = chatIsLoading,
                            modifier = Modifier.weight(1f)
                        )
                        ChatInputBar(
                            enabled = inputEnabled,
                            hint = inputHint,
                            message = state.sendErrorMessage,
                            messageIsError = state.sendErrorMessage != null,
                            onSend = onSend,
                            pendingReply = state.pendingReply,
                            onCancelReply = onCancelReply,
                            autocompleteResults = autocompleteResults,
                            onAutocompleteQueryChanged = onAutocompleteQueryChanged,
                            onEmotePicker = {
                                onEmotePickerOpen()
                                pickerOpen = true
                            },
                            insertEmoteRequest = pendingInsertion,
                            onInsertEmoteRequestConsumed = { pendingInsertion = null }
                        )
                    }
                }
                if (playerChromeVisible) {
                    ChatPlayerMetaOverlay(
                        activeChannel = activeChannel,
                        onBack = onBack,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = playerWidth + THEATER_RESIZE_HANDLE_WIDTH + 8.dp, y = 8.dp)
                            .width((chatWidth - 16.dp).coerceAtLeast(0.dp))
                    )
                }
            }
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val playerHeight = maxHeight * verticalPlayerFraction
                val availableHeight = maxHeight.value.coerceAtLeast(1f)
                Column(modifier = Modifier.fillMaxSize()) {
                    TwitchStreamStage(
                        activeChannel = activeChannel,
                        playerViewModel = streamPlayerViewModel,
                        theaterMode = false,
                        onTheaterToggle = {
                            if (activeChannel.channelLogin != null) theaterMode = true
                        },
                        videoVisible = videoVisible,
                        onVideoVisibleChange = { visible ->
                            if (visible) videoVisible = true else enterChatFullscreen()
                        },
                        onEnterPip = onEnterPip,
                        onPlayerBoundsChanged = { playerPipSourceRect = it },
                        onFocusOverlayVisibleChange = { playerChromeVisible = it },
                        fillBounds = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(playerHeight.coerceAtLeast(0.dp))
                    )
                    ResizeHandle(
                        orientation = ResizeHandleOrientation.Horizontal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(VERTICAL_RESIZE_HANDLE_HEIGHT),
                        onDrag = { drag ->
                            val next = verticalPlayerFraction + drag / availableHeight
                            if (next <= VERTICAL_HIDE_PLAYER_FRACTION) {
                                enterChatFullscreen()
                            } else {
                                verticalPlayerFraction = next.coerceIn(
                                    MIN_VERTICAL_PLAYER_FRACTION,
                                    MAX_VERTICAL_PLAYER_FRACTION
                                )
                            }
                        }
                    )
                    if (!playerChromeVisible) {
                        ChatBackBar(onBack = onBack)
                    }
                    ChatMessagePane(
                        state = state,
                        messages = messages,
                        activeChannel = activeChannel,
                        authLogin = authLogin,
                        focusManager = focusManager,
                        onReplyToMessage = onReplyToMessage,
                        isLoading = chatIsLoading,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (playerChromeVisible) {
                    ChatPlayerMetaOverlay(
                        activeChannel = activeChannel,
                        onBack = onBack,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(y = playerHeight - 2.dp)
                            .padding(horizontal = 10.dp)
                            .fillMaxWidth()
                    )
                }
            }
            ChatInputBar(
                enabled = inputEnabled,
                hint = inputHint,
                message = state.sendErrorMessage,
                messageIsError = state.sendErrorMessage != null,
                onSend = onSend,
                pendingReply = state.pendingReply,
                onCancelReply = onCancelReply,
                autocompleteResults = autocompleteResults,
                onAutocompleteQueryChanged = onAutocompleteQueryChanged,
                onEmotePicker = {
                    onEmotePickerOpen()
                    pickerOpen = true
                },
                insertEmoteRequest = pendingInsertion,
                onInsertEmoteRequestConsumed = { pendingInsertion = null }
            )
        }
    }

    if (pickerOpen) {
        EmotePickerSheet(
            catalog = emoteCatalog,
            onDismiss = { pickerOpen = false },
            onEmoteSelected = { emote ->
                pendingInsertion = EmoteInsertion(
                    name = emote.name,
                    nonce = System.nanoTime()
                )
                pickerOpen = false
            }
        )
    }
}

@Composable
private fun ChatPlayerMetaOverlay(
    activeChannel: ActiveChannelState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val channel = activeChannel.channel
    val channelLogin = activeChannel.channelLogin ?: return
    val displayName = channel?.displayName?.takeIf { it.isNotBlank() } ?: channelLogin
    val streamTitle = channel?.title?.takeIf { it.isNotBlank() }
    val category = channel?.gameName?.takeIf { it.isNotBlank() }
    val viewers = channel?.viewerCount
        ?.takeIf { it > 0 }
        ?.let { "${formatViewerCount(it)} viewers" }
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.46f), RoundedCornerShape(10.dp))
            .padding(start = 2.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(30.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(24.dp)
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
                    fontSize = 11.sp
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
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (channel?.isPartner == true) {
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = "Partner",
                        tint = Twick.Accent,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            if (streamTitle != null) {
                Text(
                    text = streamTitle,
                    color = Color.White.copy(alpha = 0.84f),
                    fontSize = 10.sp,
                    lineHeight = 12.sp
                )
            }
            if (category != null) {
                Text(
                    text = category,
                    color = Color.White.copy(alpha = 0.66f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (viewers != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PulsingLiveDot()
                Text(
                    text = viewers,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun PulsingLiveDot(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "chatLiveDot")
    val alpha by transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 820),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chatLiveDotAlpha"
    )
    Box(
        modifier = modifier
            .size(7.dp)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = 0.86f + alpha * 0.14f
                scaleY = 0.86f + alpha * 0.14f
            }
            .background(Color(0xFFFF3B30), CircleShape)
    )
}

private enum class ResizeHandleOrientation {
    Horizontal,
    Vertical
}

@Composable
private fun ResizeHandle(
    orientation: ResizeHandleOrientation,
    onDrag: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Twick.Bg)
            .pointerInput(orientation) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val delta = when (orientation) {
                        ResizeHandleOrientation.Horizontal -> dragAmount.y
                        ResizeHandleOrientation.Vertical -> dragAmount.x
                    }
                    onDrag(delta)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = when (orientation) {
                ResizeHandleOrientation.Horizontal -> Modifier
                    .width(54.dp)
                    .height(3.dp)
                ResizeHandleOrientation.Vertical -> Modifier
                    .width(3.dp)
                    .height(54.dp)
            }
                .background(Twick.Ink4.copy(alpha = 0.62f), RoundedCornerShape(999.dp))
        )
    }
}

@Composable
private fun ChatMessagePane(
    state: ChatUiState,
    messages: List<com.example.chatterinomobile.data.model.ChatMessage>,
    activeChannel: ActiveChannelState,
    authLogin: String?,
    focusManager: FocusManager,
    onReplyToMessage: (com.example.chatterinomobile.data.model.ChatMessage) -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clearComposerFocusOnTap(focusManager)
    ) {
        if (isLoading) {
            ChatMessagesLoading()
        } else {
            ChatList(
                messages = messages,
                deletedIds = state.deletedIds,
                paintsByUserId = state.paintsByUserId,
                showTimestamp = false,
                modifier = Modifier.fillMaxSize(),
                currentUserLogin = activeChannel.userState?.login ?: authLogin,
                onReplyToMessage = onReplyToMessage
            )
        }
    }
}

@Composable
private fun ChatMessagesLoading(modifier: Modifier = Modifier) {
    val brush = rememberSkeletonBrush()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        repeat(13) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SkeletonBox(
                    brush = brush,
                    modifier = Modifier.size(24.dp),
                    shape = CircleShape
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        SkeletonBox(
                            brush = brush,
                            modifier = Modifier
                                .width((58 + index % 4 * 12).dp)
                                .height(10.dp)
                        )
                        SkeletonBox(
                            brush = brush,
                            modifier = Modifier
                                .width((34 + index % 3 * 8).dp)
                                .height(8.dp)
                        )
                    }
                    SkeletonBox(
                        brush = brush,
                        modifier = Modifier
                            .fillMaxWidth(0.54f + (index % 5) * 0.08f)
                            .height(11.dp)
                    )
                    if (index % 3 == 1) {
                        SkeletonBox(
                            brush = brush,
                            modifier = Modifier
                                .fillMaxWidth(0.36f + (index % 2) * 0.18f)
                                .height(11.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBackBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Twick.Ink2
            )
        }
    }
}

@Composable
private fun StreamerMetaRow(
    activeChannel: ActiveChannelState,
    onBack: () -> Unit,
    restorePlayerMode: Boolean = false
) {
    val login = activeChannel.channel?.displayName ?: activeChannel.channelLogin
    val profileImageUrl = activeChannel.channel?.profileImageUrl
    val channel = activeChannel.channel
    val streamTitle = channel?.title?.takeIf { it.isNotBlank() }
    val category = channel?.gameName?.takeIf { it.isNotBlank() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = if (restorePlayerMode) {
                    Icons.Filled.NorthWest
                } else {
                    Icons.AutoMirrored.Filled.ArrowBack
                },
                contentDescription = if (restorePlayerMode) "Restore player" else "Back",
                tint = Twick.Ink2
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Twick.Accent),
            contentAlignment = Alignment.Center
        ) {
            if (!profileImageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = profileImageUrl,
                    contentDescription = login,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = login?.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = login ?: "channel",
                    color = Twick.Ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (channel?.isPartner == true) {
                    PartnerBadge()
                }
            }
            if (streamTitle != null) {
                Text(
                    text = streamTitle,
                    color = Twick.Ink3,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (category != null) {
                Text(
                    text = category,
                    color = Twick.Ink3,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun PartnerBadge(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Filled.Verified,
        contentDescription = "Partner",
        tint = Twick.Accent,
        modifier = modifier.size(14.dp)
    )
}

private fun Modifier.clearComposerFocusOnTap(focusManager: FocusManager): Modifier =
    pointerInput(focusManager) {
        detectTapGestures(onTap = { focusManager.clearFocus(force = true) })
    }

private fun composeHint(active: ActiveChannelState, isLoggedIn: Boolean): String {
    val login = active.channelLogin ?: return "Join a channel"
    if (!isLoggedIn) return "Sign in to chat in $login"
    val constraint = describeRoomConstraint(active.roomState, active.userState)
    return if (constraint == null) "Send a message" else "Send a message ($constraint)"
}

private fun formatViewerCount(count: Int): String =
    when {
        count >= 1_000_000 -> "${count / 1_000_000}.${(count % 1_000_000) / 100_000}M"
        count >= 1_000 -> "${count / 1_000}.${(count % 1_000) / 100}K"
        else -> count.toString()
    }

private fun describeRoomConstraint(
    room: RoomState?,
    userState: com.example.chatterinomobile.data.model.UserChatState?
): String? {
    if (room == null) return null
    if (room.subscribersOnly && userState?.canBypassSubscriberOnly() != true) {
        return "Subscribers only"
    }
    if (room.emoteOnly) return "Emotes only"
    if (room.followersOnlyMinutes != null && userState == null) return "Followers only"
    if (room.slowModeSeconds > 0) return "Slow mode (${room.slowModeSeconds}s)"
    return null
}

private fun com.example.chatterinomobile.data.model.UserChatState.canBypassSubscriberOnly(): Boolean =
    isSubscriber || isModerator ||
    badges.any { badge ->
        val id = badge.id.substringBefore('/')
        id == "subscriber" ||
            id == "founder" ||
            id == "moderator" ||
            id == "broadcaster" ||
            id == "vip"
    }

private const val DEFAULT_VERTICAL_PLAYER_FRACTION = 0.32f
private const val MIN_VERTICAL_PLAYER_FRACTION = 0.16f
private const val MAX_VERTICAL_PLAYER_FRACTION = 0.72f
private const val VERTICAL_HIDE_PLAYER_FRACTION = 0.08f
private val VERTICAL_RESIZE_HANDLE_HEIGHT = 18.dp

private const val DEFAULT_HORIZONTAL_PLAYER_FRACTION = 0.58f
private const val MIN_THEATER_PLAYER_FRACTION = 0.24f
private const val MAX_THEATER_PLAYER_FRACTION = 0.78f
private const val THEATER_HIDE_PLAYER_FRACTION = 0.10f
private val THEATER_RESIZE_HANDLE_WIDTH = 18.dp
