package com.example.chatterinomobile.data.remote.mapper

import com.example.chatterinomobile.data.model.Badge
import com.example.chatterinomobile.data.model.BadgeProvider
import com.example.chatterinomobile.data.remote.dto.FfzBadgeDto

fun FfzBadgeDto.toDomain(): Badge {
    val imageUrl = normalizeUrl(urls["4"] ?: urls["2"] ?: urls["1"].orEmpty())
    return Badge(
        id = "ffz:$id",
        imageURL = imageUrl,
        description = title.ifBlank { name },
        provider = BadgeProvider.FFZ,
        backgroundColor = color?.toArgbLong()
    )
}

private fun normalizeUrl(url: String): String {
    val trimmed = url.trim()
    return when {
        trimmed.isBlank() -> ""
        trimmed.startsWith("//") -> "https:$trimmed"
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        else -> "https://$trimmed"
    }
}

private fun String.toArgbLong(): Long? {
    val normalized = trim().removePrefix("#")
    if (normalized.length != 6 && normalized.length != 8) return null
    return runCatching {
        val value = normalized.toLong(radix = 16)
        if (normalized.length == 6) 0xFF000000 or value else value
    }.getOrNull()
}
