package com.trulyfreemusic.opengroove.data

import com.trulyfreemusic.opengroove.BuildConfig
import com.trulyfreemusic.opengroove.model.PlaybackMode
import com.trulyfreemusic.opengroove.model.Track
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

class JamendoCatalog(private val clientId: String) {
    fun search(query: String, language: SearchLanguage): List<Track> {
        require(clientId.isNotBlank()) { "A Jamendo client ID is required." }

        val parameters = linkedMapOf(
            "client_id" to clientId,
            "format" to "json",
            "limit" to "30",
            "imagesize" to "300",
            "audioformat" to "mp31",
            "type" to "single albumtrack",
        )
        language.jamendoCode?.let { parameters["lang"] = it }
        val catalogQuery = when {
            language == SearchLanguage.CANTONESE -> listOf(query.trim(), language.searchHint)
                .filter(String::isNotBlank)
                .joinToString(" ")
            else -> query.trim()
        }
        if (catalogQuery.isBlank()) {
            parameters["featured"] = "1"
            parameters["groupby"] = "artist_id"
        } else {
            parameters["search"] = catalogQuery
        }

        val encoded = parameters.entries.joinToString("&") { (key, value) ->
            "${key.encode()}=${value.encode()}"
        }
        val connection = URL("https://api.jamendo.com/v3.0/tracks/?$encoded")
            .openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "OpenGroove/${BuildConfig.VERSION_NAME} Android")

        return try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("Jamendo returned HTTP $code")
            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            parse(payload)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(payload: String): List<Track> {
        val root = JSONObject(payload)
        val headers = root.optJSONObject("headers")
        if (headers?.optString("status") == "failed") {
            throw IOException(headers.optString("error_message", "Jamendo request failed"))
        }
        val results = root.optJSONArray("results") ?: return emptyList()
        return buildList {
            for (index in 0 until results.length()) {
                val item = results.getJSONObject(index)
                val audio = item.optString("audio")
                val license = item.optString("license_ccurl")
                if (!audio.startsWith("https://") || license.isBlank()) continue
                add(
                    Track(
                        id = "jamendo:${item.getString("id")}",
                        title = item.optString("name", "Untitled"),
                        artist = item.optString("artist_name", "Unknown artist"),
                        album = item.optString("album_name"),
                        durationSeconds = item.optInt("duration"),
                        artworkUrl = item.optString("image", item.optString("album_image")),
                        providerName = "Jamendo",
                        streamUrl = audio,
                        sourceUrl = item.optString("shareurl", item.optString("shorturl")),
                        licenseUrl = license.replace("http://", "https://"),
                        playbackMode = PlaybackMode.DIRECT_AUTHORIZED,
                    ),
                )
            }
        }
    }

    private fun String.encode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())
}
