package com.trulyfreemusic.opengroove.radio

import com.trulyfreemusic.opengroove.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray

class RadioBrowserCatalog {
    fun search(
        name: String = "",
        countryCode: String? = null,
        tag: String? = null,
        offset: Int = 0,
    ): List<RadioStation> {
        val parameters = linkedMapOf(
            "name" to name.trim(),
            "countrycode" to countryCode.orEmpty(),
            "tag" to tag.orEmpty().lowercase(),
            "hidebroken" to "true",
            "order" to "clickcount",
            "reverse" to "true",
            "limit" to PAGE_SIZE.toString(),
            "offset" to offset.coerceAtLeast(0).toString(),
        ).filterValues(String::isNotBlank)
        return request("/json/stations/search", parameters) { payload ->
            parseStations(JSONArray(payload))
        }
    }

    fun countries(): List<RadioCountry> = request(
        path = "/json/countries",
        parameters = mapOf(
            "order" to "stationcount",
            "reverse" to "true",
            "hidebroken" to "true",
            "limit" to "250",
        ),
    ) { payload ->
        val array = JSONArray(payload)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val code = item.optString("iso_3166_1")
                val name = item.optString("name")
                if (code.length == 2 && name.isNotBlank()) {
                    add(RadioCountry(name, code.uppercase(), item.optInt("stationcount")))
                }
            }
        }
    }

    fun registerClick(stationId: String) {
        if (stationId.isBlank()) return
        request("/json/url/${stationId.encodePath()}", emptyMap()) { Unit }
    }

    internal fun parseStations(array: JSONArray): List<RadioStation> = buildList {
        val seen = mutableSetOf<String>()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val id = item.optString("stationuuid")
            val resolved = item.optString("url_resolved").ifBlank { item.optString("url") }
            val station = RadioStation(
                id = id,
                name = item.optString("name").trim(),
                streamUrl = resolved,
                homepageUrl = item.optString("homepage"),
                faviconUrl = item.optString("favicon"),
                country = item.optString("country"),
                countryCode = item.optString("countrycode"),
                language = item.optString("language"),
                tags = item.optString("tags").split(',').map(String::trim).filter(String::isNotBlank).take(6),
                codec = item.optString("codec"),
                bitrate = item.optInt("bitrate"),
                votes = item.optInt("votes"),
                isOnline = item.optInt("lastcheckok", 1) == 1,
                lastCheckedAt = item.optString("lastchecktime_iso8601"),
                isHls = item.optInt("hls") == 1,
            )
            if (station.isPlayable() && seen.add(station.id)) add(station)
        }
    }

    private fun <T> request(path: String, parameters: Map<String, String>, parse: (String) -> T): T {
        val query = parameters.entries.joinToString("&") { (key, value) ->
            "${key.encode()}=${value.encode()}"
        }
        var lastError: Exception? = null
        for (host in HOSTS) {
            val address = "https://$host$path" + if (query.isBlank()) "" else "?$query"
            try {
                val connection = URL(address).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 18_000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty(
                    "User-Agent",
                    "OpenGroove/${BuildConfig.VERSION_NAME} (personal Android internet radio app)",
                )
                try {
                    val code = connection.responseCode
                    if (code !in 200..299) throw IOException("Radio Browser returned HTTP $code")
                    return parse(connection.inputStream.bufferedReader().use { it.readText() })
                } finally {
                    connection.disconnect()
                }
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw IOException("Could not reach the radio directory", lastError)
    }

    private fun String.encode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())
    private fun String.encodePath(): String = replace(Regex("[^A-Za-z0-9-]"), "")

    companion object {
        const val PAGE_SIZE = 60
        val HOSTS = listOf(
            "all.api.radio-browser.info",
            "de1.api.radio-browser.info",
            "at1.api.radio-browser.info",
        )
    }
}
