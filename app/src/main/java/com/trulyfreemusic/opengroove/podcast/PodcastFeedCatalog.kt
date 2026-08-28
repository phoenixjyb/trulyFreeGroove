package com.trulyfreemusic.opengroove.podcast

import com.trulyfreemusic.opengroove.BuildConfig
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class PodcastFeedCatalog {
    fun load(feedUrl: String, fallback: PodcastShow? = null): PodcastFeed {
        require(feedUrl.isHttpUrl()) { "The podcast feed must use HTTP or HTTPS." }
        val connection = URL(feedUrl).openConnection() as HttpURLConnection
        return try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
            connection.setRequestProperty("User-Agent", "OpenGroove/${BuildConfig.VERSION_NAME}")
            val status = connection.responseCode
            if (status !in 200..299) error("Podcast feed returned HTTP $status.")
            if (connection.contentLengthLong > MAX_FEED_BYTES) error("This podcast feed is too large to process safely.")
            val payload = connection.inputStream.buffered().use { it.readLimitedBytes(MAX_FEED_BYTES) }
            parse(payload.inputStream(), feedUrl, fallback)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(input: InputStream, feedUrl: String, fallback: PodcastShow? = null): PodcastFeed {
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        parser.setInput(input, null)

        var channelTitle = ""
        var channelAuthor = ""
        var channelDescription = ""
        var channelArtwork = ""
        var channelWebsite = ""
        var inItem = false
        var episode = EpisodeBuilder()
        val episodes = mutableListOf<PodcastEpisode>()

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                val name = parser.name.lowercase()
                when {
                    name == "item" || name == "entry" -> {
                        inItem = true
                        episode = EpisodeBuilder()
                    }
                    inItem -> when (name) {
                        "title" -> episode.title = parser.readTextContent()
                        "guid", "id" -> episode.guid = parser.readTextContent()
                        "description", "summary", "encoded" -> episode.description = parser.readTextContent()
                        "author", "creator" -> episode.author = parser.readTextContent()
                        "pubdate", "published", "updated" -> episode.publishedAt = parsePodcastDate(parser.readTextContent())
                        "duration" -> episode.durationMs = parsePodcastDuration(parser.readTextContent())
                        "link" -> {
                            val href = parser.attribute("href")
                            val relation = parser.attribute("rel")
                            val type = parser.attribute("type")
                            if (href.isHttpUrl() && (relation == "enclosure" || type.startsWith("audio/"))) {
                                episode.audioUrl = href
                                episode.mimeType = type
                            } else if (href.isHttpUrl()) {
                                episode.websiteUrl = href
                            } else {
                                episode.websiteUrl = parser.readTextContent()
                            }
                        }
                        "enclosure" -> {
                            episode.audioUrl = parser.attribute("url")
                            episode.mimeType = parser.attribute("type")
                        }
                        "image", "thumbnail" -> {
                            episode.artworkUrl = parser.attribute("href").ifBlank { parser.attribute("url") }
                        }
                    }
                    else -> when (name) {
                        "title" -> if (channelTitle.isBlank()) channelTitle = parser.readTextContent()
                        "author", "creator" -> if (channelAuthor.isBlank()) channelAuthor = parser.readTextContent()
                        "description", "subtitle" -> if (channelDescription.isBlank()) channelDescription = parser.readTextContent()
                        "link" -> {
                            val href = parser.attribute("href")
                            if (href.isHttpUrl()) channelWebsite = href else {
                                val text = parser.readTextContent()
                                if (text.isHttpUrl()) channelWebsite = text
                            }
                        }
                        "image" -> {
                            val artwork = parser.attribute("href").ifBlank { parser.attribute("url") }
                            if (artwork.isHttpUrl()) channelArtwork = artwork
                        }
                        "url" -> {
                            val text = parser.readTextContent()
                            if (text.isHttpUrl() && channelArtwork.isBlank()) channelArtwork = text
                        }
                    }
                }
            } else if (parser.eventType == XmlPullParser.END_TAG) {
                val name = parser.name.lowercase()
                if ((name == "item" || name == "entry") && inItem) {
                    if (episodes.size < MAX_EPISODES) {
                        episode.build(feedUrl, channelTitle, channelAuthor, channelArtwork)?.let(episodes::add)
                    }
                    inItem = false
                }
            }
            parser.next()
        }

        val show = PodcastShow(
            catalogId = fallback?.catalogId.orEmpty(),
            title = channelTitle.ifBlank { fallback?.title.orEmpty() }.ifBlank { "Podcast" },
            author = channelAuthor.ifBlank { fallback?.author.orEmpty() },
            description = cleanPodcastText(channelDescription.ifBlank { fallback?.description.orEmpty() }),
            feedUrl = feedUrl,
            artworkUrl = channelArtwork.ifBlank { fallback?.artworkUrl.orEmpty() },
            websiteUrl = channelWebsite.ifBlank { fallback?.websiteUrl.orEmpty() },
            genre = fallback?.genre.orEmpty(),
            country = fallback?.country.orEmpty(),
            subscribed = fallback?.subscribed ?: false,
        )
        if (episodes.isEmpty()) error("No playable audio episodes were found in this feed.")
        return PodcastFeed(show, episodes.distinctBy(PodcastEpisode::episodeId))
    }

    private companion object {
        const val MAX_EPISODES = 250
        const val MAX_FEED_BYTES = 8L * 1_024L * 1_024L
    }
}

private data class EpisodeBuilder(
    var guid: String = "",
    var title: String = "",
    var author: String = "",
    var description: String = "",
    var audioUrl: String = "",
    var websiteUrl: String = "",
    var artworkUrl: String = "",
    var mimeType: String = "",
    var publishedAt: Long = 0L,
    var durationMs: Long = 0L,
) {
    fun build(feedUrl: String, showTitle: String, showAuthor: String, showArtwork: String): PodcastEpisode? {
        if (title.isBlank() || !audioUrl.isHttpUrl()) return null
        return PodcastEpisode(
            episodeId = stableEpisodeId(feedUrl, guid, audioUrl),
            feedUrl = feedUrl,
            guid = guid,
            title = title,
            showTitle = showTitle,
            author = author.ifBlank { showAuthor },
            description = cleanPodcastText(description),
            audioUrl = audioUrl,
            websiteUrl = websiteUrl,
            artworkUrl = artworkUrl.ifBlank { showArtwork },
            mimeType = mimeType,
            publishedAt = publishedAt,
            durationMs = durationMs,
        )
    }
}

private fun XmlPullParser.attribute(name: String): String =
    (0 until attributeCount).firstOrNull { getAttributeName(it).equals(name, ignoreCase = true) }
        ?.let(::getAttributeValue).orEmpty()

private fun XmlPullParser.readTextContent(): String {
    val elementDepth = depth
    val textContent = StringBuilder()
    while (eventType != XmlPullParser.END_DOCUMENT) {
        when (next()) {
            XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> textContent.append(text)
            XmlPullParser.END_TAG -> if (depth == elementDepth) return textContent.toString().trim()
        }
    }
    return textContent.toString().trim()
}

private fun InputStream.readLimitedBytes(maxBytes: Long): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        if (total > maxBytes) error("This podcast feed is too large to process safely.")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

internal fun cleanPodcastText(text: String): String = text
    .replace(Regex("<[^>]+>"), " ")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace(Regex("\\s+"), " ")
    .trim()
    .take(4_000)

private fun parsePodcastDate(value: String): Long {
    if (value.isBlank()) return 0L
    val formatters = listOf(
        DateTimeFormatter.RFC_1123_DATE_TIME,
        DateTimeFormatter.ISO_ZONED_DATE_TIME,
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
    )
    for (formatter in formatters) {
        try {
            return ZonedDateTime.parse(value.trim(), formatter).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            // Try the next common podcast date format.
        }
    }
    return 0L
}
