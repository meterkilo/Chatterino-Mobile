package com.example.chatterinomobile.data.repository

import com.example.chatterinomobile.data.model.Emote
import com.example.chatterinomobile.data.model.EmoteProvider

interface EmoteRepository {

    suspend fun loadEmotesForChannel(channelId: String?)

    fun findEmote(name: String, channelId: String? = null): Emote?

    fun listEmotesForChannel(channelId: String?): EmoteCatalog

    fun searchByPrefix(query: String, channelId: String?, limit: Int = 50): List<Emote>

    fun recordDimensions(emoteId: String, channelId: String?, width: Int, height: Int)

    fun clearCache(channelId: String? = null)
}

data class EmoteCatalog(
    val byProvider: Map<EmoteProvider, List<Emote>>,
    val globalByProvider: Map<EmoteProvider, List<Emote>> = emptyMap(),
    val channelByProvider: Map<EmoteProvider, List<Emote>> = emptyMap()
) {
    val twitch: List<Emote> get() = byProvider[EmoteProvider.TWITCH].orEmpty()
    val sevenTv: List<Emote> get() = byProvider[EmoteProvider.SEVENTV].orEmpty()
    val bttv: List<Emote> get() = byProvider[EmoteProvider.BTTV].orEmpty()
    val ffz: List<Emote> get() = byProvider[EmoteProvider.FFZ].orEmpty()
    val global: List<Emote> get() = globalByProvider.orderedFlatten()
    val channel: List<Emote> get() = channelByProvider.orderedFlatten()
    val isEmpty: Boolean get() = byProvider.values.all { it.isEmpty() }

    companion object {
        val EMPTY = EmoteCatalog(emptyMap())
    }
}

private fun Map<EmoteProvider, List<Emote>>.orderedFlatten(): List<Emote> =
    buildList {
        addAll(this@orderedFlatten[EmoteProvider.TWITCH].orEmpty())
        addAll(this@orderedFlatten[EmoteProvider.SEVENTV].orEmpty())
        addAll(this@orderedFlatten[EmoteProvider.BTTV].orEmpty())
        addAll(this@orderedFlatten[EmoteProvider.FFZ].orEmpty())
    }
