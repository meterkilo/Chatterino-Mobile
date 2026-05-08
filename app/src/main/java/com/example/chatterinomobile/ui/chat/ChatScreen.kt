package com.example.chatterinomobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatterinomobile.data.model.RoomState
import com.example.chatterinomobile.ui.channels.ActiveChannelState
import com.example.chatterinomobile.ui.channels.ChannelTabsViewModel
import com.example.chatterinomobile.ui.chat.components.ChatInputBar
import com.example.chatterinomobile.ui.chat.components.ChatList
import com.example.chatterinomobile.ui.theme.Twick
import coil.compose.AsyncImage

@Composable
fun ChatRoute(
    chatViewModel: ChatViewModel,
    tabsViewModel: ChannelTabsViewModel,
    onBack: () -> Unit
) {
    val chatState by chatViewModel.uiState.collectAsState()
    val activeChannel by tabsViewModel.activeChannel.collectAsState()

    ChatScreen(
        state = chatState,
        messages = chatViewModel.recentMessages,
        activeChannel = activeChannel,
        onSend = chatViewModel::sendMessage,
        onBack = {
            chatViewModel.stopActiveChannel()
            tabsViewModel.stopActiveChannel()
            onBack()
        }
    )

    DisposableEffect(Unit) {
        onDispose {
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
    onSend: (String) -> Unit,
    onBack: () -> Unit
) {
    val canSend = activeChannel.channelLogin != null && activeChannel.userState?.userId != null
    val hint = composeHint(activeChannel)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Twick.Bg)
    ) {
        VideoPlaceholder()
        StreamerMetaRow(activeChannel = activeChannel, onBack = onBack)

        Box(modifier = Modifier.weight(1f)) {
            ChatList(
                messages = messages,
                deletedIds = state.deletedIds,
                paintsByUserId = state.paintsByUserId,
                showTimestamp = false,
                modifier = Modifier.fillMaxSize(),
                currentUserLogin = activeChannel.userState?.login
            )
        }

        ChatInputBar(
            enabled = canSend,
            hint = hint,
            onSend = onSend
        )
    }
}

@Composable
private fun VideoPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF2A1F3D), Color(0xFF120A1F))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "stream",
            color = Color.White.copy(alpha = 0.2f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
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

private fun composeHint(active: ActiveChannelState): String {
    val login = active.channelLogin ?: return "Join a channel"
    val constraint = describeRoomConstraint(active.roomState)
    if (constraint != null) return constraint
    if (active.userState?.userId == null) return "Sign in to chat in $login"
    return "Send a message"
}

private fun describeRoomConstraint(room: RoomState?): String? {
    if (room == null) return null
    if (room.subscribersOnly) return "Subscribers only"
    if (room.emoteOnly) return "Emotes only"
    if (room.followersOnlyMinutes != null) return "Followers only"
    if (room.slowModeSeconds > 0) return "Slow mode (${room.slowModeSeconds}s)"
    return null
}
