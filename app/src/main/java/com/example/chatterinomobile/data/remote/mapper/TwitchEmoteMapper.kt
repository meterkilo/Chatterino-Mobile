package com.example.chatterinomobile.data.remote.mapper

import com.example.chatterinomobile.data.model.Emote
import com.example.chatterinomobile.data.model.EmoteProvider
import com.example.chatterinomobile.data.model.EmoteUrls
import com.example.chatterinomobile.data.remote.dto.HelixEmoteDto

fun HelixEmoteDto.toDomain(template: String?): Emote {
    val animated = format.any { it.equals("animated", ignoreCase = true) }
    val themeMode = if (themeMode.contains("dark")) "dark" else themeMode.firstOrNull() ?: "light"
    val animation = if (animated) "animated" else "static"

    val resolvedTemplate = template?.takeIf { it.isNotBlank() }
    val urls = if (resolvedTemplate != null) {
        EmoteUrls(
            x1 = expandTemplate(resolvedTemplate, id, animation, themeMode, "1.0"),
            x2 = expandTemplate(resolvedTemplate, id, animation, themeMode, "2.0"),
            x3 = expandTemplate(resolvedTemplate, id, animation, themeMode, "3.0"),
            x4 = expandTemplate(resolvedTemplate, id, animation, themeMode, "3.0")
        )
    } else {
        EmoteUrls(
            x1 = images.url1x,
            x2 = images.url2x,
            x3 = images.url4x,
            x4 = images.url4x
        )
    }

    return Emote(
        id = id,
        name = name,
        urls = urls,
        isAnimated = animated,
        isZeroWidth = false,
        provider = EmoteProvider.TWITCH,
        aspectRatio = 1f
    )
}

private fun expandTemplate(
    template: String,
    id: String,
    animation: String,
    themeMode: String,
    scale: String
): String = template
    .replace("{{id}}", id)
    .replace("{{format}}", animation)
    .replace("{{theme_mode}}", themeMode)
    .replace("{{scale}}", scale)
