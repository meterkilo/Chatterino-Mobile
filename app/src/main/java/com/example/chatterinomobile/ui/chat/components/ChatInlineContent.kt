package com.example.chatterinomobile.ui.chat.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.chatterinomobile.data.model.Badge
import com.example.chatterinomobile.data.model.MessageFragment
import com.example.chatterinomobile.data.model.Paint as PaintModel
import kotlin.math.ceil

@Composable
internal fun buildInlineContent(
    messageId: String,
    badges: List<Badge>,
    displayName: String,
    paint: PaintModel?,
    authorColor: Color,
    fragments: List<MessageFragment>
): Map<String, InlineTextContent> {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val paintPaddingPx = remember(paint) { paint.shadowPaddingPx() }
    val usernameWidth = remember(displayName, density, paintPaddingPx) {
        val usernameSize = textMeasurer.measure(displayName, UsernameStyle).size.width
        val shapingSlack = ceil(
            displayName.codePointCount(0, displayName.length).toFloat() * USERNAME_GLYPH_SLACK_PX
        )
        with(density) { (usernameSize.toFloat() + shapingSlack + paintPaddingPx * 2f).toSp() }
    }
    val usernameHeight = remember(density, paintPaddingPx) {
        with(density) { (UsernameStyle.lineHeight.toPx() + paintPaddingPx * 2f).toSp() }
    }
    val paintPadding = with(density) { paintPaddingPx.toDp() }

    return remember(messageId, paint, authorColor, usernameWidth, usernameHeight, paintPadding) {
        val map = mutableMapOf<String, InlineTextContent>()

        badges.forEachIndexed { index, badge ->
            if (badge.imageURL.isBlank()) return@forEachIndexed

            map["badge_$index"] = InlineTextContent(
                placeholder = Placeholder(
                    width = 1.1.em,
                    height = 1.1.em,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                )
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BadgeChip(badge = badge, size = 14.dp)
                }
            }
        }

        map["username"] = InlineTextContent(
            placeholder = Placeholder(
                width = usernameWidth,
                height = usernameHeight,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
            )
        ) {
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                PaintedUsername(
                    name = displayName,
                    fallbackColor = authorColor,
                    paint = paint,
                    style = UsernameStyle,
                    shadowPadding = paintPadding
                )
            }
        }

        fragments.forEachIndexed { index, fragment ->
            if (fragment is MessageFragment.Emote) {
                val ratio = fragment.aspectRatio
                    ?.takeIf { it.isFinite() && it > 0f }
                    ?.coerceIn(0.4f, 4f)
                    ?: 1.0f
                map["emote_$index"] = InlineTextContent(
                    placeholder = Placeholder(
                        width = (1.6f * ratio).em,
                        height = 1.6.em,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                    )
                ) {
                    FragmentEmoteSlot(fragment)
                }
            }
        }

        map
    }
}

private val UsernameStyle = TextStyle(
    fontSize = 14.sp,
    fontWeight = FontWeight.Bold,
    lineHeight = 18.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false)
)

private const val USERNAME_GLYPH_SLACK_PX = 1.5f

@Composable
private fun FragmentEmoteSlot(fragment: MessageFragment.Emote) {
    coil.compose.AsyncImage(
        model = fragment.url,
        contentDescription = fragment.name,
        modifier = Modifier.fillMaxSize()
    )
}
