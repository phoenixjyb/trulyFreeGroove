package com.trulyfreemusic.opengroove.data

import com.trulyfreemusic.opengroove.BuildConfig
import android.text.Html
import com.trulyfreemusic.opengroove.model.PlaybackMode
import com.trulyfreemusic.opengroove.model.Track
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

class WikimediaCatalog {
    fun search(query: String, language: SearchLanguage): List<Track> {
        val languageHint = when (language) {
            SearchLanguage.ALL -> "music"
            SearchLanguage.ENGLISH -> "English music"
            SearchLanguage.CHINESE -> "Chinese music"
            SearchLanguage.CANTONESE -> "Cantonese music"
        }
        val terms = if (query.isBlank()) languageHint else when (language) {
            SearchLanguage.ALL -> query.trim()
            SearchLanguage.ENGLISH -> "${query.trim()} English"
            SearchLanguage.CHINESE -> "${query.trim()} Chinese"
            SearchLanguage.CANTONESE -> "${query.trim()} Cantonese 粤语"
        }
        val parameters = linkedMapOf(
            "action" to "query",
            "format" to "json",
            "generator" to "search",
            "gsrsearch" to "$terms filetype:audio",
            "gsrnamespace" to "6",
            "gsrlimit" to "20",
            "prop" to "imageinfo|info",
            "iiprop" to "url|mime|extmetadata",
            "inprop" to "url",
        )
        val encoded = parameters.entries.joinToString("&") { (key, value) ->
            "${key.encode()}=${value.encode()}"
        }
        val connection = URL("https://commons.wikimedia.org/w/api.php?$encoded")
            .openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty(
            "User-Agent",
            "OpenGroove/${BuildConfig.VERSION_NAME} (personal Android music discovery app)",
        )

        return try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("Wikimedia Commons returned HTTP $code")
            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            parse(payload)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(payload: String): List<Track> {
        val root = JSONObject(payload)
        root.optJSONObject("error")?.let {
            throw IOException(it.optString("info", "Wikimedia Commons request failed"))
        }
        val pages = root.optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
        return buildList {
            pages.keys().forEach { pageId ->
                val page = pages.getJSONObject(pageId)
                val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: return@forEach
                val mime = info.optString("mime")
                if (!mime.startsWith("audio/") && mime != "application/ogg") return@forEach
                val streamUrl = info.optString("url")
                val sourceUrl = page.optString("canonicalurl", info.optString("descriptionurl"))
                val metadata = info.optJSONObject("extmetadata") ?: JSONObject()
                val licenseName = metadata.metadataValue("LicenseShortName").ifBlank {
                    metadata.metadataValue("UsageTerms")
                }
                if (!streamUrl.startsWith("https://") || sourceUrl.isBlank() || licenseName.isBlank()) {
                    return@forEach
                }
                val rawTitle = metadata.metadataValue("ObjectName").ifBlank { page.optString("title") }
                add(
                    Track(
                        id = "commons:$pageId",
                        title = rawTitle.removePrefix("File:").substringBeforeLast('.'),
                        artist = metadata.metadataValue("Artist").stripHtml().ifBlank { "Unknown creator" },
                        album = licenseName,
                        durationSeconds = 0,
                        artworkUrl = "",
                        providerName = "Wikimedia Commons",
                        streamUrl = streamUrl,
                        sourceUrl = sourceUrl,
                        licenseUrl = metadata.metadataValue("LicenseUrl").ifBlank { sourceUrl },
                        playbackMode = PlaybackMode.DIRECT_AUTHORIZED,
                    ),
                )
            }
        }
    }

    private fun JSONObject.metadataValue(name: String): String =
        optJSONObject(name)?.optString("value").orEmpty()

    private fun String.stripHtml(): String =
        Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY).toString().trim()

    private fun String.encode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())
}
