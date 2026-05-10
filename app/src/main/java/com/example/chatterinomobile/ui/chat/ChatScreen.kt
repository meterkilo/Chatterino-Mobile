package com.example.chatterinomobile.ui.chat

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import com.example.chatterinomobile.ui.player.StreamPlayerViewModel
import com.example.chatterinomobile.ui.player.TwitchStreamStage
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
    onBack: () -> Unit
) {
    val canSend = activeChannel.channelLogin != null && isLoggedIn
    val hint = composeHint(activeChannel, isLoggedIn)
    val focusManager = LocalFocusManager.current

    var pickerOpen by remember { mutableStateOf(false) }
    var pendingInsertion by remember { mutableStateOf<EmoteInsertion?>(null) }
    var theaterMode by remember(activeChannel.channelLogin) { mutableStateOf(false) }
    var videoVisible by remember(activeChannel.channelLogin) { mutableStateOf(true) }
    var chatFullscreen by remember(activeChannel.channelLogin) { mutableStateOf(false) }
    var verticalPlayerFraction by remember(activeChannel.channelLogin) {
        mutableStateOf(DEFAULT_VERTICAL_PLAYER_FRACTION)
    }
    var horizontalPlayerFraction by remember(activeChannel.channelLogin) {
        mutableStateOf(DEFAULT_HORIZONTAL_PLAYER_FRACTION)
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
        activeChannel.channelLogin?.let(streamPlayerViewModel::playChannel)
    }

    BackHandler(enabled = chatFullscreen) {
        restoreDefaultPlayer()
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
        if (chatFullscreen || !videoVisible) {
            StreamerMetaRow(activeChannel = activeChannel, onBack = onBack)
            ChatMessagePane(
                state = state,
                messages = messages,
                activeChannel = activeChannel,
                authLogin = authLogin,
                focusManager = focusManager,
                modifier = Modifier.weight(1f)
            )
            ChatInputBar(
                enabled = canSend,
                hint = if (chatFullscreen) "$hint - press Back to restore video" else hint,
                message = state.sendErrorMessage,
                messageIsError = state.sendErrorMessage != null,
                onSend = onSend,
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
            StreamerMetaRow(activeChannel = activeChannel, onBack = onBack)
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val playerWidth = maxWidth * horizontalPlayerFraction
                val chatWidth = maxWidth - playerWidth - THEATER_RESIZE_HANDLE_WIDTH
                val availableWidth = maxWidth.value.coerceAtLeast(1f)
                Row(modifier = Modifier.fillMaxSize()) {
                    TwitchStreamStage(
                        activeChannel = activeChannel,
                        playerViewModel = streamPlayerViewModel,
                        theaterMode = true,
                        onTheaterToggle = { theaterMode = false },
                        videoVisible = videoVisible,
                        onVideoVisibleChange = { visible ->
                            if (visible) videoVisible = true else enterChatFullscreen()
                        },
                        fillBounds = true,
                        modifier = Modifier
                            .width(playerWidth.coerceAtLeast(0.dp))
                            .fillMaxHeight()
                    )
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
                        ChatMessagePane(
                            state = state,
                            messages = messages,
                            activeChannel = activeChannel,
                            authLogin = authLogin,
                            focusManager = focusManager,
                            modifier = Modifier.weight(1f)
                        )
                        ChatInputBar(
                            enabled = canSend,
                            hint = hint,
                            message = state.sendErrorMessage,
                            messageIsError = state.sendErrorMessage != null,
                            onSend = onSend,
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
                        fillBounds = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(playerHeight.coerceAtLeast(0.dp))
                    )
                    StreamerMetaRow(activeChannel = activeChannel, onBack = onBack)
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
                    ChatMessagePane(
                        state = state,
                        messages = messages,
                        activeChannel = activeChannel,
                        authLogin = authLogin,
                        focusManager = focusManager,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            ChatInputBar(
                enabled = canSend,
                hint = hint,
                message = state.sendErrorMessage,
                messageIsError = state.sendErrorMessage != null,
                onSend = onSend,
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
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clearComposerFocusOnTap(focusManager)
    ) {
        ChatList(
            messages = messages,
            deletedIds = state.deletedIds,
            paintsByUserId = state.paintsByUserId,
            showTimestamp = false,
            modifier = Modifier.fillMaxSize(),
            currentUserLogin = activeChannel.userState?.login ?: authLogin
        )
    }
}

@Composable
private fun StreamerMetaRow(
    activeChannel: ActiveChannelState,
    onBack: () -> Unit
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
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
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
