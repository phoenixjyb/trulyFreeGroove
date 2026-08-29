package com.trulyfreemusic.opengroove.youtube

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.trulyfreemusic.opengroove.BuildConfig
import com.trulyfreemusic.opengroove.data.SearchLanguage
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Instant
import org.json.JSONObject

class YouTubeCatalog(
    private val apiKey: String,
    private val applicationId: String,
    private val certificateSha1: String,
) {
    constructor(context: Context, apiKey: String) : this(
        apiKey = apiKey,
        applicationId = context.packageName,
        certificateSha1 = context.signingCertificateSha1(),
    )

    fun search(query: String, language: SearchLanguage): List<YouTubeVideo> {
        require(apiKey.isNotBlank()) { "A YouTube Data API key is required." }
        val cleanQuery = query.trim()
        require(cleanQuery.isNotBlank()) { "Type a song, artist, or channel before searching YouTube." }

        val languageHint = when (language) {
            SearchLanguage.ALL -> "music"
            SearchLanguage.ENGLISH -> "English music"
            SearchLanguage.CHINESE -> "中文 音乐"
            SearchLanguage.CANTONESE -> "粵語 廣東歌 Cantonese"
        }
        val parameters = linkedMapOf(
            "part" to "snippet",
            "type" to "video",
            "q" to "$cleanQuery $languageHint",
            "maxResults" to "20",
            "order" to "relevance",
            "safeSearch" to "moderate",
            "topicId" to "/m/04rlf",
            "videoEmbeddable" to "true",
            "videoSyndicated" to "true",
        )
        when (language) {
            SearchLanguage.ENGLISH -> {
                parameters["relevanceLanguage"] = "en"
                parameters["regionCode"] = "US"
            }
            SearchLanguage.CHINESE -> {
                parameters["relevanceLanguage"] = "zh-Hans"
                parameters["regionCode"] = "HK"
            }
            SearchLanguage.CANTONESE -> {
                parameters["relevanceLanguage"] = "zh-Hant"
                parameters["regionCode"] = "HK"
            }
            SearchLanguage.ALL -> Unit
        }

        val videoIds = parseVideoIds(request("search", parameters))
        return details(videoIds)
    }

    fun details(videoIds: List<String>): List<YouTubeVideo> {
        require(apiKey.isNotBlank()) { "A YouTube Data API key is required." }
        val retainedIds = videoIds.filter(YouTubePlaybackPolicy::isCanonicalVideoId).distinct().take(50)
        if (retainedIds.isEmpty()) return emptyList()
        val payload = request(
            "videos",
            linkedMapOf(
                "part" to "snippet,contentDetails,status",
                "id" to retainedIds.joinToString(","),
                "maxResults" to retainedIds.size.toString(),
            ),
        )
        val byId = parseVideos(payload).associateBy(YouTubeVideo::videoId)
        return retainedIds.mapNotNull(byId::get)
    }

    internal fun parseVideoIds(payload: String): List<String> {
        val root = JSONObject(payload)
        root.throwYouTubeErrorIfPresent()
        val items = root.optJSONArray("items") ?: return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val videoId = items.optJSONObject(index)?.optJSONObject("id")?.optString("videoId").orEmpty()
                if (YouTubePlaybackPolicy.isCanonicalVideoId(videoId)) add(videoId)
            }
        }.distinct()
    }

    internal fun parseVideos(payload: String, refreshedAtMs: Long = System.currentTimeMillis()): List<YouTubeVideo> {
        val root = JSONObject(payload)
        root.throwYouTubeErrorIfPresent()
        val items = root.optJSONArray("items") ?: return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val videoId = item.optString("id")
                val snippet = item.optJSONObject("snippet") ?: continue
                val status = item.optJSONObject("status") ?: continue
                val embeddable = status.optBoolean("embeddable", false)
                if (!YouTubePlaybackPolicy.isWatchable(videoId, embeddable)) continue
                val title = snippet.optString("title").decodeHtml().trim()
                val channelTitle = snippet.optString("channelTitle").decodeHtml().trim()
                val thumbnailUrl = snippet.optJSONObject("thumbnails").bestThumbnailUrl()
                if (title.isBlank() || channelTitle.isBlank() || !thumbnailUrl.startsWith("https://")) continue
                add(
                    YouTubeVideo(
                        videoId = videoId,
                        title = title,
                        channelTitle = channelTitle,
                        thumbnailUrl = thumbnailUrl,
                        durationSeconds = parseYouTubeDuration(
                            item.optJSONObject("contentDetails")?.optString("duration").orEmpty(),
                        ),
                        publishedAtMs = runCatching {
                            Instant.parse(snippet.optString("publishedAt")).toEpochMilli()
                        }.getOrDefault(0L),
                        embeddable = true,
                        madeForKids = status.opt("madeForKids") as? Boolean,
                        isLive = snippet.optString("liveBroadcastContent") == "live",
                        metadataRefreshedAtMs = refreshedAtMs,
                    ),
                )
            }
        }
    }

    private fun request(resource: String, parameters: Map<String, String>): String {
        val encoded = parameters.entries.joinToString("&") { (key, value) ->
            "${key.encode()}=${value.encode()}"
        }
        val connection = URL("https://www.googleapis.com/youtube/v3/$resource?$encoded")
            .openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "OpenGroove/${BuildConfig.VERSION_NAME} Android")
        connection.setRequestProperty("X-goog-api-key", apiKey)
        connection.setRequestProperty("X-Android-Package", applicationId)
        if (certificateSha1.isNotBlank()) connection.setRequestProperty("X-Android-Cert", certificateSha1)
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val payload = stream?.use { it.readLimitedText(MAX_RESPONSE_BYTES) }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching {
                    JSONObject(payload).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty()
                throw IOException(message.ifBlank { "YouTube Data API returned HTTP $status." })
            }
            payload
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 2L * 1_024L * 1_024L
    }
}

internal fun parseYouTubeDuration(value: String): Int {
    val match = Regex("^P(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+(?:\\.\\d+)?)S)?)?$")
        .matchEntire(value.trim()) ?: return 0
    val days = match.groupValues[1].toLongOrNull() ?: 0L
    val hours = match.groupValues[2].toLongOrNull() ?: 0L
    val minutes = match.groupValues[3].toLongOrNull() ?: 0L
    val seconds = match.groupValues[4].toDoubleOrNull() ?: 0.0
    val total = days * 86_400.0 + hours * 3_600.0 + minutes * 60.0 + seconds
    if (!total.isFinite() || total <= 0.0 || total > Int.MAX_VALUE) return 0
    return total.toInt()
}

private fun JSONObject?.bestThumbnailUrl(): String {
    val thumbnails = this ?: return ""
    return listOf("maxres", "standard", "high", "medium", "default")
        .firstNotNullOfOrNull { name ->
            thumbnails.optJSONObject(name)?.optString("url")?.takeIf(String::isNotBlank)
        }.orEmpty()
}

private fun JSONObject.throwYouTubeErrorIfPresent() {
    optJSONObject("error")?.let { error ->
        throw IOException(error.optString("message", "YouTube Data API request failed."))
    }
}

private fun String.decodeHtml(): String {
    val numericDecoded = Regex("&#(x[0-9A-Fa-f]+|[0-9]+);").replace(this) { match ->
        val token = match.groupValues[1]
        val codePoint = if (token.startsWith("x", ignoreCase = true)) {
            token.drop(1).toIntOrNull(16)
        } else {
            token.toIntOrNull()
        }
        codePoint?.takeIf(Character::isValidCodePoint)?.let { String(Character.toChars(it)) } ?: match.value
    }
    return numericDecoded
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
}

private fun String.encode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

private fun InputStream.readLimitedText(maxBytes: Long): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        if (total > maxBytes) throw IOException("YouTube Data API response was too large.")
        output.write(buffer, 0, count)
    }
    return output.toString(Charsets.UTF_8.name())
}

@Suppress("DEPRECATION")
private fun Context.signingCertificateSha1(): String = runCatching {
    val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            .signingInfo?.apkContentsSigners
    } else {
        packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
    }.orEmpty()
    val digest = MessageDigest.getInstance("SHA-1").digest(signatures.first().toByteArray())
    digest.joinToString("") { byte -> "%02X".format(byte) }
}.getOrDefault("")
