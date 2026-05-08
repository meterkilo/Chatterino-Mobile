package com.example.chatterinomobile.ui.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatterinomobile.data.model.ChatMessage
import com.example.chatterinomobile.data.model.MessageFragment
import com.example.chatterinomobile.data.model.MessageType
import com.example.chatterinomobile.data.model.Paint
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.coroutines.launch

@Composable
fun ChatList(
    messages: List<ChatMessage>,
    deletedIds: PersistentSet<String>,
    showTimestamp: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = 6.dp),
    currentUserLogin: String? = null,
    paintsByUserId: PersistentMap<String, Paint> = persistentHashMapOf()
) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val bottomAnchorThresholdPx = remember(density) {
        with(density) { BottomAnchorThreshold.toPx() }
    }
    var followingLatest by remember { mutableStateOf(true) }
    var hasMessagesBelow by remember { mutableStateOf(false) }

    val atLatest by remember(bottomAnchorThresholdPx) {
        derivedStateOf {
            val layout = state.layoutInfo
            val total = layout.totalItemsCount
            if (total == 0) return@derivedStateOf true
            val last = layout.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            if (last.index < total - 1) return@derivedStateOf false

            val distanceToBottom = last.offset + last.size - layout.viewportEndOffset
            distanceToBottom <= bottomAnchorThresholdPx
        }
    }

    LaunchedEffect(state.isScrollInProgress, atLatest) {
        if (atLatest) {
            followingLatest = true
            hasMessagesBelow = false
        } else if (state.isScrollInProgress) {
            followingLatest = false
            hasMessagesBelow = true
        }
    }

    val latestMessageId = messages.lastOrNull()?.id
    LaunchedEffect(latestMessageId, followingLatest) {
        if (messages.isEmpty()) {
            hasMessagesBelow = false
            return@LaunchedEffect
        }
        if (followingLatest) {
            state.scrollToItem(messages.lastIndex)
            hasMessagesBelow = false
        } else if (!hasMessagesBelow) {
            hasMessagesBelow = true
        }
    }

    Box(modifier = modifier) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.Bottom
        ) {
            items(
                messages,
                key = { it.id },
                contentType = { it.contentTypeKey() }
            ) { message ->
                val deleted = message.id in deletedIds
                val highlight = currentUserLogin != null &&
                    message.fragment.any { it is MessageFragment.Mention &&
                        it.username.equals(currentUserLogin, ignoreCase = true) }
                ChatMessageRow(
                    message = message,
                    showTimestamp = showTimestamp,
                    deleted = deleted,
                    highlight = highlight,
                    paintOverride = paintsByUserId[message.author.id]
                )
            }
        }

        AnimatedVisibility(
            visible = hasMessagesBelow,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        ) {
            ResumePill(onClick = {
                scope.launch {
                    followingLatest = true
                    state.scrollToItem(messages.lastIndex.coerceAtLeast(0))
                    hasMessagesBelow = false
                }
            })
        }
    }
}

@Composable
private fun ResumePill(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.padding(end = 6.dp)
            )
            Text(
                text = "More messages below",
                color = Color.White,
                fontSize = 12.sp
            )
        }
    }
}

private val BottomAnchorThreshold = 96.dp

private fun ChatMessage.contentTypeKey(): Int {
    val typeBucket = when (Type) {
        is MessageType.System -> 0
        is MessageType.Action -> 1
        is MessageType.Reply -> 2
        is MessageType.Highlighted -> 3
        is MessageType.Regular -> 4
    }
    return if (reply != null) typeBucket or REPLY_BIT else typeBucket
}

private const val REPLY_BIT = 1 shl 4
