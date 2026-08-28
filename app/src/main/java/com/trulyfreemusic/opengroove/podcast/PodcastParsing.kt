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
    val parts = clean.split(':').mapNotNull(String::toLongOrNull)
    val seconds = when (parts.size) {
        1 -> parts[0]
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> return 0L
    }
    return seconds.coerceAtLeast(0L) * 1_000L
}
