package com.trulyfreemusic.opengroove.radio

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

class SavedStationStore(context: Context) {
    private val preferences = context.getSharedPreferences("open_groove_radio", Context.MODE_PRIVATE)
    private val mutableStations = MutableStateFlow(load())
    val stations: StateFlow<List<RadioStation>> = mutableStations

    fun toggle(station: RadioStation) {
        val current = mutableStations.value
        val next = if (current.any { it.id == station.id }) {
            current.filterNot { it.id == station.id }
        } else {
            listOf(station) + current
        }
        mutableStations.value = next
        preferences.edit().putString(KEY, encode(next).toString()).apply()
    }

    private fun load(): List<RadioStation> = runCatching {
        val payload = preferences.getString(KEY, null) ?: return@runCatching emptyList()
        decode(JSONArray(payload))
    }.getOrDefault(emptyList())

    private fun encode(stations: List<RadioStation>) = JSONArray().apply {
        stations.forEach { station ->
            put(JSONObject().apply {
                put("id", station.id)
                put("name", station.name)
                put("streamUrl", station.streamUrl)
                put("homepageUrl", station.homepageUrl)
                put("faviconUrl", station.faviconUrl)
                put("country", station.country)
                put("countryCode", station.countryCode)
                put("language", station.language)
                put("tags", JSONArray(station.tags))
                put("codec", station.codec)
                put("bitrate", station.bitrate)
                put("votes", station.votes)
            })
        }
    }

    private fun decode(array: JSONArray): List<RadioStation> = buildList {
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val tagsArray = item.optJSONArray("tags") ?: JSONArray()
            val tags = buildList {
                for (tagIndex in 0 until tagsArray.length()) add(tagsArray.getString(tagIndex))
            }
            val station = RadioStation(
                id = item.getString("id"),
                name = item.getString("name"),
                streamUrl = item.getString("streamUrl"),
                homepageUrl = item.optString("homepageUrl"),
                faviconUrl = item.optString("faviconUrl"),
                country = item.optString("country"),
                countryCode = item.optString("countryCode"),
                language = item.optString("language"),
                tags = tags,
                codec = item.optString("codec"),
                bitrate = item.optInt("bitrate"),
                votes = item.optInt("votes"),
            )
            if (station.isPlayable()) add(station)
        }
    }

    private companion object {
        const val KEY = "saved_stations_v1"
    }
}
