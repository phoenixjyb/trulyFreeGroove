package com.trulyfreemusic.opengroove.podcast

import com.trulyfreemusic.opengroove.BuildConfig
import com.trulyfreemusic.opengroove.data.SearchLanguage
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

class ApplePodcastCatalog {
    fun search(query: String, language: SearchLanguage, limit: Int = 30): List<PodcastShow> {
        val cleanQuery = query.trim()
        require(cleanQuery.isNotBlank()) { "Enter a podcast, host, or topic." }
        val country = when (language) {
            SearchLanguage.CHINESE -> "CN"
            SearchLanguage.CANTONESE -> "HK"
            else -> "US"
        }
        val encodedQuery = URLEncoder.encode(cleanQuery, Charsets.UTF_8.name())
        val endpoint = URL(
            "https://itunes.apple.com/search?media=podcast&entity=podcast" +
                "&term=$encodedQuery&country=$country&limit=${limit.coerceIn(1, 50)}&explicit=No",
        )
        val connection = endpoint.openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "OpenGroove/${BuildConfig.VERSION_NAME}")
            val status = connection.responseCode
            if (status !in 200..299) error("Podcast search returned HTTP $status.")
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseResults(JSONObject(body))
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseResults(root: JSONObject): List<PodcastShow> {
        val results = root.optJSONArray("results") ?: return emptyList()
        return buildList {
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                val feedUrl = item.optString("feedUrl")
                val show = PodcastShow(
                    catalogId = item.optLong("collectionId").takeIf { it > 0 }?.toString().orEmpty(),
                    title = item.optString("collectionName"),
                    author = item.optString("artistName"),
                    description = "",
                    feedUrl = feedUrl,
                    // Apple discovery artwork is promotional content. The publisher feed supplies
                    // artwork after the user opens a show, avoiding reuse of Apple's promo asset.
                    artworkUrl = "",
                    websiteUrl = item.optString("collectionViewUrl"),
                    genre = item.optString("primaryGenreName"),
                    country = item.optString("country"),
                )
                if (show.isValid()) add(show)
            }
        }.distinctBy(PodcastShow::feedUrl)
    }
}
