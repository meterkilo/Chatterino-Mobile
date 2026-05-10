package com.example.chatterinomobile.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatterinomobile.ui.chat.AutocompleteKind
import com.example.chatterinomobile.ui.chat.AutocompleteQuery
import com.example.chatterinomobile.ui.chat.UserAutocompleteSuggestion
import com.example.chatterinomobile.ui.theme.Twick

@Composable
fun ChatInputBar(
    enabled: Boolean,
    hint: String,
    message: String?,
    messageIsError: Boolean,
    onSend: (String) -> Unit,
    autocompleteResults: List<CompletionItem>,
    onAutocompleteQueryChanged: (AutocompleteQuery?) -> Unit,
    modifier: Modifier = Modifier,
    onEmotePicker: (() -> Unit)? = null,
    insertEmoteRequest: EmoteInsertion? = null,
    onInsertEmoteRequestConsumed: () -> Unit = {}
) {
    var value by remember { mutableStateOf(TextFieldValue("")) }
    var focused by remember { mutableStateOf(false) }
    val canSend = enabled && value.text.isNotBlank()
    val submitMessage = {
        val toSend = value.text.replace('\n', ' ').trim()
        if (enabled && toSend.isNotEmpty()) {
            onSend(toSend)
            value = TextFieldValue("")
            onAutocompleteQueryChanged(null)
        }
    }

    if (insertEmoteRequest != null) {
        val updated = insertEmoteIntoValue(value, insertEmoteRequest.name).limitToMaxMessageLength()
        value = updated
        onAutocompleteQueryChanged(currentAutocompleteQuery(updated))
        onInsertEmoteRequestConsumed()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Twick.S1)
    ) {
        if (autocompleteResults.isNotEmpty()) {
            EmoteAutocompleteStrip(
                results = autocompleteResults,
                onSelect = { picked ->
                    val updated = when (picked) {
                        is CompletionItem.Emote -> replaceCurrentCompletionToken(
                            value = value,
                            prefix = ':',
                            replacementText = picked.emote.name
                        )
                        is CompletionItem.User -> replaceCurrentCompletionToken(
                            value = value,
                            prefix = '@',
                            replacementText = "@${picked.user.displayName}"
                        )
                    }.limitToMaxMessageLength()
                    value = updated
                    onAutocompleteQueryChanged(currentAutocompleteQuery(updated))
                }
            )
        }
        if (message != null) {
            Text(
                text = message,
                color = if (messageIsError) Color(0xFFFF8A8A) else Twick.Ink3,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            IconCircle(
                enabled = enabled,
                onClick = { onEmotePicker?.invoke() }
            ) {
                Icon(
                    imageVector = Icons.Outlined.SentimentSatisfied,
                    contentDescription = "Emotes",
                    tint = if (enabled) Twick.Ink2 else Twick.Ink4
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 32.dp)
                    .background(Twick.S2, RoundedCornerShape(8.dp))
                    .border(
                        width = 1.dp,
                        color = if (focused) Twick.Accent else Twick.Hairline,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(start = 10.dp, top = 6.dp, end = 10.dp, bottom = 15.dp)
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = { incoming ->
                        if (!enabled) return@BasicTextField
                        val limited = incoming.limitToMaxMessageLength()
                        value = limited
                        onAutocompleteQueryChanged(currentAutocompleteQuery(limited))
                    },
                    enabled = enabled,
                    singleLine = false,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submitMessage() }),
                    cursorBrush = SolidColor(Twick.Accent),
                    textStyle = LocalTextStyle.current.copy(color = Twick.Ink, fontSize = 13.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 52.dp)
                        .onFocusChanged { focused = it.isFocused }
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                event.key == Key.Enter &&
                                !event.isShiftPressed
                            ) {
                                submitMessage()
                                true
                            } else {
                                false
                            }
                        },
                    decorationBox = { inner ->
                        if (value.text.isEmpty()) {
                            Text(
                                text = hint,
                                color = Twick.Ink3,
                                fontSize = 13.sp
                            )
                        }
                        inner()
                    }
                )
                Text(
                    text = "${value.text.length}/$MAX_MESSAGE_LENGTH",
                    color = Twick.Ink4,
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = if (canSend) Twick.Accent else Twick.S2,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable(enabled = canSend) { submitMessage() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (canSend) Color.White else Twick.Ink3
                )
            }
        }
    }
}

data class EmoteInsertion(val name: String, val nonce: Long)

sealed interface CompletionItem {
    data class Emote(val emote: com.example.chatterinomobile.data.model.Emote) : CompletionItem
    data class User(val user: UserAutocompleteSuggestion) : CompletionItem
}

@Composable
private fun EmoteAutocompleteStrip(
    results: List<CompletionItem>,
    onSelect: (CompletionItem) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(Twick.S2)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(items = results, key = { it.key }) { item ->
            AutocompleteChip(item = item, onClick = { onSelect(item) })
        }
    }
}

@Composable
private fun AutocompleteChip(item: CompletionItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .background(Twick.S1, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (item is CompletionItem.Emote) {
            EmoteImage(emote = item.emote, height = 22.dp)
        }
        Text(
            text = item.label,
            color = Twick.Ink2,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun IconCircle(
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}

internal fun currentAutocompleteQuery(value: TextFieldValue): AutocompleteQuery? {
    val cursor = value.selection.start
    if (cursor <= 0) return null
    val text = value.text
    if (cursor > text.length) return null

    var i = cursor
    while (i > 0) {
        val c = text[i - 1]
        if (c.isWhitespace()) return null
        if (c == ':' || c == '@') {
            val tokenStart = i
            val tokenEnd = cursor
            val precededByWhitespace = i - 1 == 0 || text[i - 2].isWhitespace()
            if (!precededByWhitespace) return null
            return AutocompleteQuery(
                kind = if (c == ':') AutocompleteKind.Emote else AutocompleteKind.User,
                text = text.substring(tokenStart, tokenEnd)
            )
        }
        i--
    }
    return null
}

internal fun replaceCurrentCompletionToken(
    value: TextFieldValue,
    prefix: Char,
    replacementText: String
): TextFieldValue {
    val cursor = value.selection.start
    val text = value.text
    if (cursor <= 0 || cursor > text.length) return value

    var tokenIndex = -1
    var i = cursor
    while (i > 0) {
        val c = text[i - 1]
        if (c.isWhitespace()) return value
        if (c == prefix) {
            val precededByWhitespace = i - 1 == 0 || text[i - 2].isWhitespace()
            if (!precededByWhitespace) return value
            tokenIndex = i - 1
            break
        }
        i--
    }
    if (tokenIndex < 0) return value

    val replacement = "$replacementText "
    val newText = text.substring(0, tokenIndex) + replacement + text.substring(cursor)
    val newCursor = tokenIndex + replacement.length
    return value.copy(text = newText, selection = TextRange(newCursor))
}

internal fun insertEmoteIntoValue(value: TextFieldValue, emoteName: String): TextFieldValue {
    val token = currentAutocompleteQuery(value)
    if (token?.kind == AutocompleteKind.Emote) {
        return replaceCurrentCompletionToken(
            value = value,
            prefix = ':',
            replacementText = emoteName
        )
    }

    val cursor = value.selection.start.coerceIn(0, value.text.length)
    val before = value.text.substring(0, cursor)
    val after = value.text.substring(cursor)
    val needsLeadingSpace = before.isNotEmpty() && !before.last().isWhitespace()
    val prefix = if (needsLeadingSpace) " " else ""
    val replacement = "$prefix$emoteName "
    val newText = before + replacement + after
    val newCursor = cursor + replacement.length
    return value.copy(text = newText, selection = TextRange(newCursor))
}

private val CompletionItem.key: String
    get() = when (this) {
        is CompletionItem.Emote -> "emote:${emote.provider}:${emote.id}:${emote.name}"
        is CompletionItem.User -> "user:${user.login.lowercase()}"
    }

private val CompletionItem.label: String
    get() = when (this) {
        is CompletionItem.Emote -> emote.name
        is CompletionItem.User -> "@${user.displayName}"
    }

private fun TextFieldValue.limitToMaxMessageLength(): TextFieldValue {
    if (text.length <= MAX_MESSAGE_LENGTH) return this
    val limited = text.take(MAX_MESSAGE_LENGTH)
    val selectionStart = selection.start.coerceIn(0, limited.length)
    val selectionEnd = selection.end.coerceIn(0, limited.length)
    return copy(text = limited, selection = TextRange(selectionStart, selectionEnd))
}

private const val MAX_MESSAGE_LENGTH = 500
