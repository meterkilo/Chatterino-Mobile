package com.example.chatterinomobile.data.remote.irc

import com.example.chatterinomobile.data.model.ChatMessage
import com.example.chatterinomobile.data.model.MessageFragment
import com.example.chatterinomobile.data.repository.BadgeRepository
import com.example.chatterinomobile.data.repository.EmoteRepository
import com.example.chatterinomobile.data.repository.PaintRepository

class MessageEnricher(
    private val badgeRepository: BadgeRepository,
    private val emoteRepository: EmoteRepository,
    private val paintRepository: PaintRepository
) {

    suspend fun enrich(message: ChatMessage): ChatMessage {
        badgeRepository.loadThirdPartyBadgesForUser(message.author.id)
        paintRepository.requestPaintForUser(message.author.id)

        return enrichFromCache(message)
    }

    fun requestCosmetics(message: ChatMessage) {
        paintRepository.requestPaintForUser(message.author.id)
    }

    suspend fun loadThirdPartyBadges(message: ChatMessage) {
        badgeRepository.loadThirdPartyBadgesForUser(message.author.id)
    }

    fun enrichFromCache(message: ChatMessage): ChatMessage {
        val paint = paintRepository.findPaintForUser(message.author.id)
        val badges = enrichBadges(message)
        val swapped = swapThirdPartyEmotes(
            fragments = message.fragment,
            channelId = message.channelId
        )

        if (paint == null && swapped === message.fragment && badges === message.badges) return message

        val author = if (paint != null) message.author.copy(paint = paint) else message.author
        return message.copy(
            author = author,
            fragment = swapped,
            badges = badges
        )
    }

    private fun enrichBadges(message: ChatMessage) =
        badgeRepository.findThirdPartyBadges(message.author.id)
            .filterNot { thirdParty -> message.badges.any { it.id == thirdParty.id } }
            .takeIf { it.isNotEmpty() }
            ?.let { message.badges + it }
            ?: message.badges

    private fun swapThirdPartyEmotes(
        fragments: List<MessageFragment>,
        channelId: String
    ): List<MessageFragment> {

        if (fragments.none { it is MessageFragment.Text && it.content.containsEmoteCandidate() }) {
            return fragments
        }

        var foundEmote = false
        val out = ArrayList<MessageFragment>(fragments.size)
        for (fragment in fragments) {
            if (fragment !is MessageFragment.Text) {
                out.add(fragment)
                continue
            }
            foundEmote = expandTextFragment(fragment.content, channelId, out) || foundEmote
        }
        return if (foundEmote) out else fragments
    }

    private fun expandTextFragment(
        text: String,
        channelId: String,
        out: MutableList<MessageFragment>
    ): Boolean {
        if (text.isEmpty()) return false
        val buffer = StringBuilder()
        var foundEmote = false
        var i = 0
        val n = text.length
        while (i < n) {

            val wsStart = i
            while (i < n && text[i].isWhitespace()) i++
            if (i > wsStart) buffer.append(text, wsStart, i)
            if (i >= n) break

            val wordStart = i
            while (i < n && !text[i].isWhitespace()) i++
            val word = text.substring(wordStart, i)

            val emote = emoteRepository.findEmote(word, channelId)
            if (emote != null) {
                foundEmote = true
                if (buffer.isNotEmpty()) {
                    out.add(MessageFragment.Text(buffer.toString()))
                    buffer.clear()
                }
                out.add(
                    MessageFragment.Emote(
                        id = emote.id,
                        name = emote.name,
                        url = emote.urls.x3,
                        aspectRatio = emote.aspectRatio,
                        isZeroWidth = emote.isZeroWidth
                    )
                )
            } else {
                buffer.append(word)
            }
        }
        if (buffer.isNotEmpty()) {
            out.add(MessageFragment.Text(buffer.toString()))
        }
        return foundEmote
    }

    private fun String.containsEmoteCandidate(): Boolean {
        for (c in this) if (!c.isWhitespace()) return true
        return false
    }
}
