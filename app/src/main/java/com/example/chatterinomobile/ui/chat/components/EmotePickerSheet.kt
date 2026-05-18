package com.example.chatterinomobile.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chatterinomobile.data.model.Emote
import com.example.chatterinomobile.data.model.EmoteProvider
import com.example.chatterinomobile.data.repository.EmoteCatalog
import com.example.chatterinomobile.ui.common.rememberSoftHaptic
import com.example.chatterinomobile.ui.theme.Twick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmotePickerSheet(
    catalog: EmoteCatalog,
    onDismiss: () -> Unit,
    onEmoteSelected: (Emote) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchText by remember(catalog) { mutableStateOf("") }

    val tabs = remember(catalog) {
        listOfNotNull(
            tabFor("Channel", catalog.channel.filterNot { it.provider == EmoteProvider.TWITCH }.cachedFirst()),
            tabFor("Subs", catalog.channelByProvider[EmoteProvider.TWITCH].orEmpty().cachedFirst()),
            tabFor("Twitch", catalog.globalByProvider[EmoteProvider.TWITCH].orEmpty().cachedFirst()),
            tabFor("Global", catalog.global.filterNot { it.provider == EmoteProvider.TWITCH }.cachedFirst())
        )
    }

    var selectedIndex by remember(catalog) { mutableStateOf(0) }
    val activeTab = tabs.getOrNull(selectedIndex.coerceAtMost(tabs.lastIndex.coerceAtLeast(0)))
    val searchResults = remember(catalog, searchText) {
        filterEmotes(catalog.allEmotes(), searchText)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GlassSheet,
        scrimColor = Color.Transparent,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 248.dp, max = 392.dp)
                .background(Color.Transparent)
        ) {
            EmotePickerHeader()
            EmoteSearchField(
                value = searchText,
                onValueChange = { searchText = it }
            )

            if (searchText.isNotBlank()) {
                if (searchResults.isEmpty()) {
                    EmptyState("No emotes found")
                } else {
                    EmoteGrid(
                        emotes = searchResults,
                        onEmoteSelected = onEmoteSelected
                    )
                }
            } else if (tabs.isEmpty()) {
                EmptyState("No emotes loaded yet")
            } else {
                EmoteTabBar(
                    tabs = tabs,
                    selectedIndex = selectedIndex,
                    onSelect = { selectedIndex = it }
                )

                if (activeTab == null || activeTab.emotes.isEmpty()) {
                    EmptyState("No ${activeTab?.label ?: ""} emotes available")
                } else {
                    EmoteGrid(
                        emotes = activeTab.emotes,
                        onEmoteSelected = onEmoteSelected
                    )
                }
            }
        }
    }
}

private data class EmotePickerTab(
    val label: String,
    val emotes: List<Emote>
)

private fun tabFor(label: String, emotes: List<Emote>): EmotePickerTab? {
    if (emotes.isEmpty()) return null
    return EmotePickerTab(label = label, emotes = emotes)
}

private fun List<Emote>.cachedFirst(): List<Emote> =
    sortedWith(
        compareByDescending<Emote> { it.aspectRatio != null }
            .thenBy { it.name.lowercase() }
    )

@Composable
private fun EmotePickerHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Emotes",
            color = Twick.Ink,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun EmoteSearchField(
    value: String,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .background(GlassPanel, RoundedCornerShape(8.dp))
            .border(1.dp, GlassStroke, RoundedCornerShape(8.dp))
            .padding(start = 10.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = Twick.Ink3,
            modifier = Modifier.size(18.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 7.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = { onValueChange(it.filterNot(Char::isWhitespace)) },
                singleLine = true,
                cursorBrush = SolidColor(Twick.Accent),
                textStyle = LocalTextStyle.current.copy(color = Twick.Ink, fontSize = 14.sp),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            text = "Search all emotes",
                            color = Twick.Ink3,
                            fontSize = 14.sp
                        )
                    }
                    inner()
                }
            )
        }
        IconButton(
            onClick = { onValueChange("") },
            enabled = value.isNotEmpty(),
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Clear",
                tint = if (value.isNotEmpty()) Twick.Ink2 else Twick.Ink4,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun EmoteTabBar(
    tabs: List<EmotePickerTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassPanel)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEachIndexed { index, tab ->
            EmoteTabChip(
                label = "${tab.label} (${tab.emotes.size})",
                selected = index == selectedIndex,
                onClick = { onSelect(index) }
            )
        }
    }
}

@Composable
private fun EmoteTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val haptic = rememberSoftHaptic()
    val bg = if (selected) GlassAccent else GlassPanel
    val fg = if (selected) Twick.Ink else Twick.Ink2
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(8.dp))
            .border(1.dp, if (selected) Twick.AccentSoft else GlassStroke, RoundedCornerShape(8.dp))
            .clickable {
                if (!selected) haptic()
                onClick()
            }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun EmoteGrid(
    emotes: List<Emote>,
    onEmoteSelected: (Emote) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 62.dp),
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        items(items = emotes, key = { "${it.provider}:${it.id}:${it.name}" }) { emote ->
            EmoteGridCell(emote = emote, onClick = { onEmoteSelected(emote) })
        }
    }
}

@Composable
private fun EmoteGridCell(emote: Emote, onClick: () -> Unit) {
    val haptic = rememberSoftHaptic()
    Column(
        modifier = Modifier
            .size(width = 62.dp, height = 58.dp)
            .background(GlassCell, RoundedCornerShape(8.dp))
            .border(1.dp, GlassStroke, RoundedCornerShape(8.dp))
            .clickable {
                haptic()
                onClick()
            }
            .padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        EmoteImage(emote = emote, height = 30.dp)
        Text(
            text = emote.name,
            color = Twick.Ink3,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message, color = Twick.Ink3, fontSize = 12.sp)
    }
}

private fun EmoteCatalog.allEmotes(): List<Emote> =
    buildList {
        addAll(twitch)
        addAll(sevenTv)
        addAll(bttv)
        addAll(ffz)
    }.distinctBy { "${it.provider}:${it.id}:${it.name}" }

private fun filterEmotes(emotes: List<Emote>, query: String): List<Emote> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return emptyList()
    val lower = trimmed.lowercase()
    return emotes
        .asSequence()
        .filter { it.name.contains(trimmed, ignoreCase = true) }
        .sortedWith(
            compareBy<Emote> { scoreEmote(it.name, trimmed, lower) }
                .thenBy { it.name.lowercase() }
        )
        .take(MAX_SEARCH_RESULTS)
        .toList()
}

private fun scoreEmote(name: String, query: String, lowerQuery: String): Int {
    val lowerName = name.lowercase()
    if (name == query) return -20
    if (name.equals(query, ignoreCase = true)) return -10
    val prefixPenalty = if (lowerName.startsWith(lowerQuery)) 0 else 10_000
    val casePenalty = if (name.startsWith(query, ignoreCase = false)) 0 else 500
    val positionPenalty = lowerName.indexOf(lowerQuery).coerceAtLeast(0) * 100
    val lengthPenalty = name.length - query.length
    return prefixPenalty + casePenalty + positionPenalty + lengthPenalty
}

private const val MAX_SEARCH_RESULTS = 250

private val GlassSheet = Color(0xAA101014)
private val GlassPanel = Color(0x4A1C1C20)
private val GlassCell = Color(0x3D26262C)
private val GlassStroke = Color(0x1FFFFFFF)
private val GlassAccent = Color(0x669146FF)
