package com.trulyfreemusic.opengroove.data

import android.content.Context
import com.trulyfreemusic.opengroove.model.PlaybackMode
import com.trulyfreemusic.opengroove.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

class PlaylistStore(context: Context) {
    private val preferences = context.getSharedPreferences("open_groove_playlists", Context.MODE_PRIVATE)
    private val mutablePlaylists = MutableStateFlow(load())
    val playlists: StateFlow<Map<String, List<Track>>> = mutablePlaylists

    fun create(name: String): Boolean {
        val cleanName = name.trim()
        if (cleanName.isBlank() || cleanName.length > 50 || mutablePlaylists.value.containsKey(cleanName)) {
            return false
        }
        update(mutablePlaylists.value + (cleanName to emptyList()))
        return true
    }

    fun add(playlistName: String, track: Track) {
        val tracks = mutablePlaylists.value[playlistName] ?: return
        if (tracks.any { it.id == track.id }) return
        update(mutablePlaylists.value + (playlistName to tracks + track))
    }

    fun remove(playlistName: String, trackId: String) {
        val tracks = mutablePlaylists.value[playlistName] ?: return
        update(mutablePlaylists.value + (playlistName to tracks.filterNot { it.id == trackId }))
    }

    private fun update(next: Map<String, List<Track>>) {
        mutablePlaylists.value = next
        preferences.edit().putString(KEY, encode(next).toString()).apply()
    }

    private fun load(): Map<String, List<Track>> = runCatching {
        val json = preferences.getString(KEY, null) ?: return@runCatching emptyMap()
        decode(JSONObject(json))
    }.getOrDefault(emptyMap())

    private fun encode(playlists: Map<String, List<Track>>): JSONObject = JSONObject().apply {
        playlists.forEach { (name, tracks) ->
            put(name, JSONArray().apply { tracks.forEach { put(it.toJson()) } })
        }
    }

    private fun decode(root: JSONObject): Map<String, List<Track>> = buildMap {
        root.keys().forEach { name ->
            val tracks = root.optJSONArray(name) ?: JSONArray()
            put(name, buildList {
                for (index in 0 until tracks.length()) add(tracks.getJSONObject(index).toTrack())
            })
        }
    }

    private fun Track.toJson() = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("artist", artist)
        put("album", album)
        put("durationSeconds", durationSeconds)
        put("artworkUrl", artworkUrl)
        put("providerName", providerName)
        put("streamUrl", streamUrl)
        put("sourceUrl", sourceUrl)
        put("licenseUrl", licenseUrl)
        put("playbackMode", playbackMode.name)
    }

    private fun JSONObject.toTrack() = Track(
        id = getString("id"),
        title = getString("title"),
        artist = getString("artist"),
        album = optString("album"),
        durationSeconds = optInt("durationSeconds"),
        artworkUrl = optString("artworkUrl"),
        providerName = getString("providerName"),
        streamUrl = optString("streamUrl"),
        sourceUrl = optString("sourceUrl"),
        licenseUrl = optString("licenseUrl"),
        playbackMode = runCatching { PlaybackMode.valueOf(getString("playbackMode")) }
            .getOrDefault(PlaybackMode.EXTERNAL_ONLY),
    )

    private companion object {
        const val KEY = "playlists_v1"
    }
}
