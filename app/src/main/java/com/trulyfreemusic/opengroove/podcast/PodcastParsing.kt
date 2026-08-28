package com.trulyfreemusic.opengroove.podcast

import java.security.MessageDigest

internal fun stableEpisodeId(feedUrl: String, guid: String, audioUrl: String): String {
    val identity = "$feedUrl\u001F${guid.ifBlank { audioUrl }}"
    return MessageDigest.getInstance("SHA-256")
        .digest(identity.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

internal fun parsePodcastDuration(value: String): Long {
    val clean = value.trim()
    if (clean.isBlank()) return 0L
    val rawParts = clean.split(':')
    if (rawParts.size !in 1..3) return 0L
    val parts = rawParts.map { part ->
        part.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: return 0L
    }
    if (parts.size >= 2 && parts.last() >= 60.0) return 0L
    if (parts.size == 3 && parts[1] >= 60.0) return 0L
    val seconds = when (parts.size) {
        1 -> parts[0]
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> return 0L
    }
    if (!seconds.isFinite() || seconds > Long.MAX_VALUE / 1_000.0) return 0L
    return (seconds * 1_000.0).toLong()
}
