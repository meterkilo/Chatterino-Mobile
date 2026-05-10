package com.example.chatterinomobile.data.remote.irc

import com.example.chatterinomobile.data.model.UserChatState
import com.example.chatterinomobile.data.repository.BadgeRepository

class UserStateMapper(
    private val badgeRepository: BadgeRepository
) {

    fun map(raw: IrcMessage): UserChatState? = when (raw.command) {
        "GLOBALUSERSTATE" -> mapState(raw, includeChannel = false)
        "USERSTATE" -> mapState(raw, includeChannel = true)
        else -> null
    }

    private fun mapState(raw: IrcMessage, includeChannel: Boolean): UserChatState? {
        val channelLogin = raw.channel
        val channelId = raw.tags["room-id"] ?: channelLogin
        val userId = raw.tags["user-id"]
        val badges = parseBadgeTag(raw.tags["badges"]).mapNotNull { (setId, version) ->
            badgeRepository.findTwitchBadge(
                setId = setId,
                version = version,
                channelId = if (includeChannel) channelId else null
            )
        } + userId.orEmpty().let { id ->
            if (id.isBlank()) emptyList() else badgeRepository.findThirdPartyBadges(id)
        }

        return UserChatState(
            userId = userId,
            login = raw.tags["login"],
            displayName = raw.tags["display-name"]?.takeUnless { it.isBlank() },
            color = raw.tags["color"]?.takeUnless { it.isBlank() },
            badges = badges,
            emoteSetIds = raw.tags["emote-sets"]
                .orEmpty()
                .split(',')
                .mapNotNull { it.takeUnless(String::isBlank) },
            isModerator = raw.tags["mod"] == "1",
            isSubscriber = raw.tags["subscriber"] == "1",
            channelId = if (includeChannel) channelId else null,
            channelLogin = if (includeChannel) channelLogin else null
        )
    }

    private fun parseBadgeTag(tag: String?): List<Pair<String, String>> {
        if (tag.isNullOrBlank()) return emptyList()
        return tag.split(',').mapNotNull { part ->
            val slash = part.indexOf('/')
            if (slash <= 0 || slash == part.length - 1) null
            else part.substring(0, slash) to part.substring(slash + 1)
        }
    }
}
