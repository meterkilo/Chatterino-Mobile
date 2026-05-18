package com.example.chatterinomobile.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatterinomobile.data.model.ChatMessage
import com.example.chatterinomobile.data.model.MessageFragment
import com.example.chatterinomobile.data.model.MessageType
import com.example.chatterinomobile.data.model.Paint
import com.example.chatterinomobile.data.model.ReplyMetadata
import com.example.chatterinomobile.ui.common.rememberSoftHaptic
import com.example.chatterinomobile.ui.theme.PublicSansFontFamily
import com.example.chatterinomobile.ui.theme.Twick
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

private const val USERNAME_ID = "username"

private val MessageBodyStyle: TextStyle
    get() = TextStyle(
        color = Twick.Ink,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )

private val TimestampStyle: TextStyle
    get() = TextStyle(
        color = Twick.Ink4,
        fontSize = 11.sp,
        fontFamily = PublicSansFontFamily,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )

private val ReplyStyle: TextStyle
    get() = TextStyle(
        color = Twick.Ink3,
        fontSize = 11.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )

private fun badgeId(index: Int): String = "badge_$index"

private fun emoteId(index: Int): String = "emote_$index"

private fun formatTime(epochMillis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMillis))

private fun parseHexColor(hex: String): Color? {
    val normalized = hex.trim().removePrefix("#")
    if (normalized.length != 6 && normalized.length != 8) return null

    return try {
        val colorValue = normalized.toLong(radix = 16)
        if (normalized.length == 6) {
            Color(0xFF000000 or colorValue)
        } else {
            Color(colorValue)
        }
    } catch (_: NumberFormatException) {
        null
    }
}

private fun deterministicColor(login: String): Color {
    val palette = longArrayOf(
        0xFFFF4A80, 0xFFFFB13D, 0xFFFFD24A, 0xFF65E085,
        0xFF53D7D7, 0xFF6CA8FF, 0xFFB888FF, 0xFFFF87BA
    )
    val index = (login.hashCode() and Int.MAX_VALUE) % palette.size
    return Color(palette[index])
}

private fun readableUsernameColor(color: Color, background: Color = Twick.Bg): Color {
    var candidate = color
    repeat(MAX_READABILITY_STEPS) {
        if (contrastRatio(candidate, background) >= MIN_USERNAME_CONTRAST) {
            return candidate
        }
        candidate = lerp(candidate, Color.White, 0.18f)
    }
    return candidate
}

private fun contrastRatio(foreground: Color, background: Color): Float {
    val lighter = max(relativeLuminance(foreground), relativeLuminance(background))
    val darker = minOf(relativeLuminance(foreground), relativeLuminance(background))
    return (lighter + 0.05f) / (darker + 0.05f)
}

private fun relativeLuminance(color: Color): Float {
    fun channel(value: Float): Float =
        if (value <= 0.03928f) value / 12.92f
        else ((value + 0.055f) / 1.055f).pow(2.4f)

    return 0.2126f * channel(color.red) +
        0.7152f * channel(color.green) +
        0.0722f * channel(color.blue)
}

private fun lerp(start: Color, stop: Color, fraction: Float): Color =
    Color(
        red = start.red + (stop.red - start.red) * fraction,
        green = start.green + (stop.green - start.green) * fraction,
        blue = start.blue + (stop.blue - start.blue) * fraction,
        alpha = start.alpha + (stop.alpha - start.alpha) * fraction
    )

private const val MIN_USERNAME_CONTRAST = 3.5f
private const val MAX_READABILITY_STEPS = 8
private val SwipeReplyThreshold = 64.dp
private val SwipeReplyMaxOffset = 96.dp
private const val HistoricalMessageAlpha = 0.58f

@Composable
fun ChatMessageRow(
    message: ChatMessage,
    showTimestamp: Boolean,
    deleted: Boolean,
    highlight: Boolean,
    modifier: Modifier = Modifier,
    paintOverride: Paint? = null,
    onReply: (() -> Unit)? = null
) {
    val type = message.Type
    if (type is MessageType.System) {
        SystemMessageRow(
            text = type.text,
            timestamp = message.timestamp,
            showTimestamp = showTimestamp,
            historical = message.isHistorical,
            modifier = modifier
        )
        return
    }

    val bg = if (highlight) Twick.AccentSoft else Color.Transparent
    val leftBorder = if (highlight) Twick.Accent else Color.Transparent

    SwipeReplyContainer(
        enabled = onReply != null && !deleted,
        onReply = { onReply?.invoke() },
        modifier = modifier
    ) { swipeModifier ->
        Row(
            modifier = swipeModifier
                .fillMaxWidth()
                .background(bg)
                .padding(start = if (highlight) 0.dp else 10.dp, end = 10.dp, top = 3.dp, bottom = 3.dp)
                .alpha(
                    when {
                        deleted -> 0.45f
                        message.isHistorical -> HistoricalMessageAlpha
                        else -> 1f
                    }
                ),
            verticalAlignment = Alignment.Top
        ) {
            if (highlight) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(20.dp)
                        .background(leftBorder)
                )
                Spacer(Modifier.width(8.dp))
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                val reply = message.reply
                if (reply != null) {
                    ReplyPreviewRow(reply)
                }
                MessageBody(
                    message = message,
                    showTimestamp = showTimestamp,
                    deleted = deleted,
                    paintOverride = paintOverride
                )
            }
        }
    }
}

@Composable
private fun SwipeReplyContainer(
    enabled: Boolean,
    onReply: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit
) {
    if (!enabled) {
        content(modifier)
        return
    }

    val density = LocalDensity.current
    val haptic = rememberSoftHaptic()
    val maxOffsetPx = with(density) { SwipeReplyMaxOffset.toPx() }
    val thresholdPx = with(density) { SwipeReplyThreshold.toPx() }
    var offsetPx by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(onReply) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetPx >= thresholdPx) {
                            haptic()
                            onReply()
                        }
                        offsetPx = 0f
                    },
                    onDragCancel = {
                        offsetPx = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        offsetPx = (offsetPx + dragAmount).coerceIn(0f, maxOffsetPx)
                    }
                )
            }
    ) {
        if (offsetPx > 0f) {
            Text(
                text = "Reply",
                color = Twick.Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
                    .alpha((offsetPx / thresholdPx).coerceIn(0.35f, 1f))
            )
        }
        content(
            Modifier.offset {
                IntOffset(offsetPx.roundToInt(), 0)
            }
        )
    }
}

@Composable
private fun MessageBody(
    message: ChatMessage,
    showTimestamp: Boolean,
    deleted: Boolean,
    paintOverride: Paint?
) {
    val author = message.author
    val authorColor = remember(author.color, author.login) {
        val parsedColor = if (author.color != null) parseHexColor(author.color) else null
        readableUsernameColor(parsedColor ?: deterministicColor(author.login))
    }
    val isAction = message.Type is MessageType.Action
    val resolvedPaint = paintOverride ?: author.paint

    val text = remember(message.id, deleted, showTimestamp, authorColor) {
        buildAnnotatedString {
            if (showTimestamp) {
                withStyle(TimestampStyle.toSpanStyle()) {
                    append(formatTime(message.timestamp))
                }
                append("  ")
            }

            message.badges.forEachIndexed { index, badge ->
                if (badge.imageURL.isBlank()) return@forEachIndexed
                appendInlineContent(badgeId(index), "·")
                append(" ")
            }

        appendInlineContent(USERNAME_ID, author.displayName)
        if (!isAction) {
            append(" ")
        }

            if (deleted) {
                withStyle(SpanStyle(color = Twick.Ink3, textDecoration = TextDecoration.LineThrough)) {
                    renderFragments(
                        fragments = message.fragment,
                        actionColor = if (message.Type is MessageType.Action) authorColor else null,
                        deleted = true
                    )
                }
            } else {
                renderFragments(
                    fragments = message.fragment,
                    actionColor = if (message.Type is MessageType.Action) authorColor else null,
                    deleted = false
                )
            }
        }
    }

    val inline = buildInlineContent(
        messageId = message.id,
        badges = message.badges,
        displayName = author.displayName,
        paint = resolvedPaint,
        authorColor = authorColor,
        fragments = message.fragment
    )

    Text(
        text = text,
        style = MessageBodyStyle,
        inlineContent = inline,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ReplyPreviewRow(reply: ReplyMetadata) {
    val parent = reply.parentDisplayName ?: reply.parentUserLogin ?: "—"
    val body = reply.parentBody ?: ""
    Text(
        text = "↪ @$parent: $body",
        style = ReplyStyle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, bottom = 2.dp)
    )
}

@Composable
private fun SystemMessageRow(
    text: String,
    timestamp: Long,
    showTimestamp: Boolean,
    historical: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .alpha(if (historical) HistoricalMessageAlpha else 1f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showTimestamp) {
            Text(formatTime(timestamp), style = TimestampStyle)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            "·",
            style = MaterialTheme.typography.bodySmall,
            color = Twick.Accent,
            modifier = Modifier.padding(end = 6.dp)
        )
        Text(
            text = text,
            color = Twick.Ink3,
            style = MessageBodyStyle.copy(fontSize = 12.sp)
        )
    }
}

private fun AnnotatedString.Builder.renderFragments(
    fragments: List<MessageFragment>,
    actionColor: Color?,
    deleted: Boolean
) {
    fragments.forEachIndexed { index, fragment ->
        when (fragment) {
            is MessageFragment.Text -> {
                if (actionColor != null && !deleted) {
                    withStyle(SpanStyle(color = actionColor, fontStyle = FontStyle.Italic)) {
                        append(fragment.content)
                    }
                } else if (actionColor != null) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(fragment.content)
                    }
                } else {
                    append(fragment.content)
                }
            }
            is MessageFragment.Emote -> {
                appendInlineContent(emoteId(index), fragment.name)
            }
            is MessageFragment.Mention -> {
                if (deleted) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append("@${fragment.username}")
                    }
                } else {
                    withStyle(
                        SpanStyle(
                            color = Twick.Accent,
                            fontWeight = FontWeight.SemiBold
                        )
                    ) {
                        append("@${fragment.username}")
                    }
                }
            }
        }
    }
}
