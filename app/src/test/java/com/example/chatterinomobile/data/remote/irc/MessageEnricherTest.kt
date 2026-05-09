package com.example.chatterinomobile.data.remote.irc

import com.example.chatterinomobile.data.model.ChatMessage
import com.example.chatterinomobile.data.model.ChatUser
import com.example.chatterinomobile.data.model.Badge
import com.example.chatterinomobile.data.model.BadgeProvider
import com.example.chatterinomobile.data.model.ColorStop
import com.example.chatterinomobile.data.model.Emote
import com.example.chatterinomobile.data.model.EmoteProvider
import com.example.chatterinomobile.data.model.EmoteUrls
import com.example.chatterinomobile.data.model.GradientFunction
import com.example.chatterinomobile.data.model.MessageFragment
import com.example.chatterinomobile.data.model.Paint
import com.example.chatterinomobile.data.repository.BadgeRepository
import com.example.chatterinomobile.data.repository.EmoteRepository
import com.example.chatterinomobile.data.repository.PaintAssignment
import com.example.chatterinomobile.data.repository.PaintRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageEnricherTest {

    @Test
    fun enrichRequestsPaintLoadAndPassesThroughWhenCacheEmpty() = runBlocking {
        val paintRepo = FakePaintRepository(emptyMap())
        val enricher = MessageEnricher(
            badgeRepository = FakeBadgeRepository(emptyMap()),
            emoteRepository = FakeEmoteRepository(emptyMap()),
            paintRepository = paintRepo
        )

        val original = message(listOf(MessageFragment.Text("hi")))
        val enriched = enricher.enrich(original)

        assertSame(original, enriched)
        assertTrue("expected paint load to be requested", paintRepo.requestedUserIds.contains("user-id"))
    }

    @Test
    fun enrichReplacesThirdPartyEmoteWordsAndAppliesPaint() = runBlocking {
        val emote = Emote(
            id = "wide-emote",
            name = "wideHappy",
            urls = EmoteUrls(
                x1 = "https://example.test/1.webp",
                x2 = "https://example.test/2.webp",
                x3 = "https://example.test/3.webp",
                x4 = "https://example.test/4.webp"
            ),
            isAnimated = false,
            isZeroWidth = true,
            provider = EmoteProvider.SEVENTV,
            aspectRatio = 2.25f
        )
        val paint = Paint.Gradient(
            id = "paint-id",
            function = GradientFunction.LINEAR,
            angle = 45,
            stops = listOf(
                ColorStop(0f, 0xFFFF0000),
                ColorStop(1f, 0xFF00FF00)
            )
        )
        val badge = Badge(
            id = "7tv-badge",
            imageURL = "https://example.test/badge.webp",
            description = "7TV Badge",
            provider = BadgeProvider.SEVENTV
        )
        val enricher = MessageEnricher(
            badgeRepository = FakeBadgeRepository(mapOf("user-id" to listOf(badge))),
            emoteRepository = FakeEmoteRepository(mapOf("channel-id" to mapOf("wideHappy" to emote))),
            paintRepository = FakePaintRepository(mapOf("user-id" to paint))
        )

        val enriched = enricher.enrich(message(listOf(MessageFragment.Text("hello wideHappy chat"))))

        assertSame(paint, enriched.author.paint)
        assertEquals(listOf(badge), enriched.badges)
        assertEquals(
            listOf(
                MessageFragment.Text("hello "),
                MessageFragment.Emote(
                    id = "wide-emote",
                    name = "wideHappy",
                    url = "https://example.test/3.webp",
                    aspectRatio = 2.25f,
                    isZeroWidth = true
                ),
                MessageFragment.Text(" chat")
            ),
            enriched.fragment
        )
    }

    private fun message(fragments: List<MessageFragment>): ChatMessage =
        ChatMessage(
            id = "message-id",
            channelId = "channel-id",
            author = ChatUser(
                id = "user-id",
                login = "tester",
                displayName = "Tester",
                color = null,
                paint = null
            ),
            fragment = fragments,
            timestamp = 1L
        )

    private class FakeEmoteRepository(
        private val channelEmotes: Map<String, Map<String, Emote>>
    ) : EmoteRepository {
        override suspend fun loadEmotesForChannel(channelId: String?) = Unit

        override fun findEmote(name: String, channelId: String?): Emote? =
            channelId?.let { channelEmotes[it]?.get(name) }

        override fun listEmotesForChannel(channelId: String?): com.example.chatterinomobile.data.repository.EmoteCatalog =
            com.example.chatterinomobile.data.repository.EmoteCatalog.EMPTY

        override fun searchByPrefix(query: String, channelId: String?, limit: Int): List<Emote> = emptyList()

        override fun recordDimensions(emoteId: String, channelId: String?, width: Int, height: Int) = Unit

        override fun clearCache(channelId: String?) = Unit
    }

    private class FakeBadgeRepository(
        private val userBadges: Map<String, List<Badge>>
    ) : BadgeRepository {
        override suspend fun loadGlobalBadges() = Unit

        override suspend fun loadChannelBadges(channelId: String) = Unit

        override suspend fun loadThirdPartyBadgesForUser(twitchUserId: String) = Unit

        override fun findTwitchBadge(setId: String, version: String, channelId: String?): Badge? = null

        override fun findThirdPartyBadges(twitchUserId: String): List<Badge> =
            userBadges[twitchUserId].orEmpty()

        override fun clearCache(channelId: String?) = Unit
    }

    private class FakePaintRepository(
        private val paints: Map<String, Paint>
    ) : PaintRepository {
        val requestedUserIds = mutableListOf<String>()
        private val flow = MutableSharedFlow<PaintAssignment>(extraBufferCapacity = 16)

        override val paintAssignments: SharedFlow<PaintAssignment> = flow.asSharedFlow()

        override fun requestPaintForUser(twitchUserId: String) {
            requestedUserIds += twitchUserId
        }

        override fun findPaintForUser(twitchUserId: String): Paint? =
            paints[twitchUserId]

        override fun snapshot(): Map<String, Paint> = paints
    }
}
