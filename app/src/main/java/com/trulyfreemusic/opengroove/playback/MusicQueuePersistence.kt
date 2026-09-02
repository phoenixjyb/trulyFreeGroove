package com.trulyfreemusic.opengroove.playback

import android.content.Context
import com.trulyfreemusic.opengroove.model.PlaybackMode
import com.trulyfreemusic.opengroove.model.Track
import org.json.JSONArray
import org.json.JSONObject

internal data class MusicQueueSnapshot(
    val tracks: List<Track>,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffleEnabled: Boolean,
    val repeatMode: Int,
)

internal class MusicQueueStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): MusicQueueSnapshot? = preferences.getString(KEY_SNAPSHOT, null)
        ?.let(::decodeMusicQueueSnapshot)

    fun save(snapshot: MusicQueueSnapshot) {
        val encoded = encodeMusicQueueSnapshot(snapshot)
        if (encoded == null) clear()
        else preferences.edit().putString(KEY_SNAPSHOT, encoded).apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_SNAPSHOT).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "open_groove_music_queue"
        const val KEY_SNAPSHOT = "music_queue_v1"
    }
}

internal fun encodeMusicQueueSnapshot(snapshot: MusicQueueSnapshot): String? {
    val limitedTracks = snapshot.tracks.take(MAX_MUSIC_QUEUE_SIZE)
    val playableTracks = limitedTracks.mapIndexedNotNull { index, track ->
        if (track.isDirectPlaybackAllowed()) index to track else null
    }
    if (playableTracks.isEmpty()) return null
    val requestedIndex = snapshot.currentIndex.coerceIn(limitedTracks.indices)
    val currentIndex = playableTracks.indexOfFirst { it.first == requestedIndex }
        .takeIf { it >= 0 }
        ?: requestedIndex.coerceIn(playableTracks.indices)
    return JSONObject()
        .put("currentIndex", currentIndex)
        .put("positionMs", snapshot.positionMs.coerceAtLeast(0L))
        .put("shuffleEnabled", snapshot.shuffleEnabled)
        .put("repeatMode", snapshot.repeatMode.coerceIn(REPEAT_MODE_OFF, REPEAT_MODE_ALL))
        .put("tracks", JSONArray().apply { playableTracks.forEach { put(it.second.toJson()) } })
        .toString()
}

internal fun decodeMusicQueueSnapshot(payload: String): MusicQueueSnapshot? = runCatching {
    val root = JSONObject(payload)
    val encodedTracks = root.getJSONArray("tracks")
    val decodedTracks = buildList {
        for (index in 0 until minOf(encodedTracks.length(), MAX_MUSIC_QUEUE_SIZE)) {
            encodedTracks.optJSONObject(index)?.toTrackOrNull()?.let { add(index to it) }
        }
    }
    val playableTracks = decodedTracks.filter { it.second.isDirectPlaybackAllowed() }
    if (playableTracks.isEmpty()) return@runCatching null

    val encodedCurrentIndex = root.optInt("currentIndex", 0)
    val currentIndex = playableTracks.indexOfFirst { it.first == encodedCurrentIndex }.takeIf { it >= 0 }
        ?: encodedCurrentIndex.coerceIn(playableTracks.indices)
    MusicQueueSnapshot(
        tracks = playableTracks.map { it.second },
        currentIndex = currentIndex,
        positionMs = root.optLong("positionMs", 0L).coerceAtLeast(0L),
        shuffleEnabled = root.optBoolean("shuffleEnabled", false),
        repeatMode = root.optInt("repeatMode", REPEAT_MODE_OFF).coerceIn(REPEAT_MODE_OFF, REPEAT_MODE_ALL),
    )
}.getOrNull()

private fun Track.toJson() = JSONObject()
    .put("id", id)
    .put("title", title)
    .put("artist", artist)
    .put("album", album)
    .put("durationSeconds", durationSeconds)
    .put("artworkUrl", artworkUrl)
    .put("providerName", providerName)
    .put("streamUrl", streamUrl)
    .put("sourceUrl", sourceUrl)
    .put("licenseUrl", licenseUrl)
    .put("playbackMode", playbackMode.name)

private fun JSONObject.toTrackOrNull(): Track? = runCatching {
    Track(
        id = getString("id"),
        title = getString("title"),
        artist = optString("artist"),
        album = optString("album"),
        durationSeconds = optInt("durationSeconds"),
        artworkUrl = optString("artworkUrl"),
        providerName = getString("providerName"),
        streamUrl = getString("streamUrl"),
        sourceUrl = getString("sourceUrl"),
        licenseUrl = getString("licenseUrl"),
        playbackMode = PlaybackMode.valueOf(getString("playbackMode")),
    )
}.getOrNull()

private const val MAX_MUSIC_QUEUE_SIZE = 500
private const val REPEAT_MODE_OFF = 0
private const val REPEAT_MODE_ALL = 2
