package com.trulyfreemusic.opengroove.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.trulyfreemusic.opengroove.model.PlaybackMode
import com.trulyfreemusic.opengroove.model.Track
import com.trulyfreemusic.opengroove.podcast.PodcastEpisode
import com.trulyfreemusic.opengroove.radio.RadioStation

private const val TYPE = "open_groove_type"
private const val TYPE_TRACK = "track"
private const val TYPE_STATION = "station"
private const val TYPE_PODCAST = "podcast"
private const val FIELD_ID = "id"
private const val FIELD_NAME = "name"
private const val FIELD_ARTIST = "artist"
private const val FIELD_ALBUM = "album"
private const val FIELD_DURATION = "duration"
private const val FIELD_ARTWORK = "artwork"
private const val FIELD_PROVIDER = "provider"
private const val FIELD_STREAM = "stream"
private const val FIELD_SOURCE = "source"
private const val FIELD_LICENSE = "license"
private const val FIELD_PLAYBACK_MODE = "playback_mode"
private const val FIELD_HOMEPAGE = "homepage"
private const val FIELD_COUNTRY = "country"
private const val FIELD_COUNTRY_CODE = "country_code"
private const val FIELD_LANGUAGE = "language"
private const val FIELD_TAGS = "tags"
private const val FIELD_CODEC = "codec"
private const val FIELD_BITRATE = "bitrate"
private const val FIELD_VOTES = "votes"
private const val FIELD_ONLINE = "online"
private const val FIELD_LAST_CHECKED = "last_checked"
private const val FIELD_HLS = "hls"
private const val FIELD_FEED = "feed"
private const val FIELD_GUID = "guid"
private const val FIELD_SHOW = "show"
private const val FIELD_DESCRIPTION = "description"
private const val FIELD_MIME = "mime"
private const val FIELD_PUBLISHED = "published"
private const val FIELD_POSITION = "position"
private const val TAG_SEPARATOR = "\u001F"

fun Track.toMediaItem(): MediaItem {
    val extras = Bundle().apply {
        putString(TYPE, TYPE_TRACK)
        putString(FIELD_ID, id)
        putString(FIELD_NAME, title)
        putString(FIELD_ARTIST, artist)
        putString(FIELD_ALBUM, album)
        putInt(FIELD_DURATION, durationSeconds)
        putString(FIELD_ARTWORK, artworkUrl)
        putString(FIELD_PROVIDER, providerName)
        putString(FIELD_STREAM, streamUrl)
        putString(FIELD_SOURCE, sourceUrl)
        putString(FIELD_LICENSE, licenseUrl)
        putString(FIELD_PLAYBACK_MODE, playbackMode.name)
    }
    return MediaItem.Builder()
        .setMediaId("track:$id")
        .setUri(streamUrl)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUrl.toUriOrNull())
                .setExtras(extras)
                .build(),
        )
        .build()
}

fun RadioStation.toMediaItem(): MediaItem {
    val extras = Bundle().apply {
        putString(TYPE, TYPE_STATION)
        putString(FIELD_ID, id)
        putString(FIELD_NAME, name)
        putString(FIELD_STREAM, streamUrl)
        putString(FIELD_HOMEPAGE, homepageUrl)
        putString(FIELD_ARTWORK, faviconUrl)
        putString(FIELD_COUNTRY, country)
        putString(FIELD_COUNTRY_CODE, countryCode)
        putString(FIELD_LANGUAGE, language)
        putString(FIELD_TAGS, tags.joinToString(TAG_SEPARATOR))
        putString(FIELD_CODEC, codec)
        putInt(FIELD_BITRATE, bitrate)
        putInt(FIELD_VOTES, votes)
        putBoolean(FIELD_ONLINE, isOnline)
        putString(FIELD_LAST_CHECKED, lastCheckedAt)
        putBoolean(FIELD_HLS, isHls)
    }
    return MediaItem.Builder()
        .setMediaId("radio:$id")
        .setUri(streamUrl)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(name)
                .setArtist(country.ifBlank { "Internet radio" })
                .setAlbumTitle("OpenGroove Radio")
                .setArtworkUri(faviconUrl.toUriOrNull())
                .setExtras(extras)
                .build(),
        )
        .build()
}

fun PodcastEpisode.toMediaItem(): MediaItem {
    val extras = Bundle().apply {
        putString(TYPE, TYPE_PODCAST)
        putString(FIELD_ID, episodeId)
        putString(FIELD_FEED, feedUrl)
        putString(FIELD_GUID, guid)
        putString(FIELD_NAME, title)
        putString(FIELD_SHOW, showTitle)
        putString(FIELD_ARTIST, author)
        putString(FIELD_DESCRIPTION, description)
        putString(FIELD_STREAM, audioUrl)
        putString(FIELD_SOURCE, websiteUrl)
        putString(FIELD_ARTWORK, artworkUrl)
        putString(FIELD_MIME, mimeType)
        putLong(FIELD_PUBLISHED, publishedAt)
        putLong(FIELD_DURATION, durationMs)
        putLong(FIELD_POSITION, positionMs)
    }
    return MediaItem.Builder()
        .setMediaId("podcast:$episodeId")
        .setUri(audioUrl)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(showTitle)
                .setAlbumTitle("OpenGroove Podcasts")
                .setArtworkUri(artworkUrl.toUriOrNull())
                .setIsPlayable(true)
                .setExtras(extras)
                .build(),
        )
        .build()
}

fun MediaItem.toTrackOrNull(): Track? {
    val extras = mediaMetadata.extras ?: return null
    if (extras.getString(TYPE) != TYPE_TRACK) return null
    val id = extras.getString(FIELD_ID).orEmpty()
    val title = extras.getString(FIELD_NAME).orEmpty()
    if (id.isBlank() || title.isBlank()) return null
    return Track(
        id = id,
        title = title,
        artist = extras.getString(FIELD_ARTIST).orEmpty(),
        album = extras.getString(FIELD_ALBUM).orEmpty(),
        durationSeconds = extras.getInt(FIELD_DURATION),
        artworkUrl = extras.getString(FIELD_ARTWORK).orEmpty(),
        providerName = extras.getString(FIELD_PROVIDER).orEmpty(),
        streamUrl = extras.getString(FIELD_STREAM).orEmpty(),
        sourceUrl = extras.getString(FIELD_SOURCE).orEmpty(),
        licenseUrl = extras.getString(FIELD_LICENSE).orEmpty(),
        playbackMode = runCatching {
            PlaybackMode.valueOf(extras.getString(FIELD_PLAYBACK_MODE).orEmpty())
        }.getOrDefault(PlaybackMode.EXTERNAL_ONLY),
    )
}

fun MediaItem.toRadioStationOrNull(): RadioStation? {
    val extras = mediaMetadata.extras ?: return null
    if (extras.getString(TYPE) != TYPE_STATION) return null
    val station = RadioStation(
        id = extras.getString(FIELD_ID).orEmpty(),
        name = extras.getString(FIELD_NAME).orEmpty(),
        streamUrl = extras.getString(FIELD_STREAM).orEmpty(),
        homepageUrl = extras.getString(FIELD_HOMEPAGE).orEmpty(),
        faviconUrl = extras.getString(FIELD_ARTWORK).orEmpty(),
        country = extras.getString(FIELD_COUNTRY).orEmpty(),
        countryCode = extras.getString(FIELD_COUNTRY_CODE).orEmpty(),
        language = extras.getString(FIELD_LANGUAGE).orEmpty(),
        tags = extras.getString(FIELD_TAGS).orEmpty().split(TAG_SEPARATOR).filter(String::isNotBlank),
        codec = extras.getString(FIELD_CODEC).orEmpty(),
        bitrate = extras.getInt(FIELD_BITRATE),
        votes = extras.getInt(FIELD_VOTES),
        isOnline = extras.getBoolean(FIELD_ONLINE, true),
        lastCheckedAt = extras.getString(FIELD_LAST_CHECKED).orEmpty(),
        isHls = extras.getBoolean(FIELD_HLS),
    )
    return station.takeIf(RadioStation::isPlayable)
}

fun MediaItem.toPodcastEpisodeOrNull(): PodcastEpisode? {
    val extras = mediaMetadata.extras ?: return null
    if (extras.getString(TYPE) != TYPE_PODCAST) return null
    val episode = PodcastEpisode(
        episodeId = extras.getString(FIELD_ID).orEmpty(),
        feedUrl = extras.getString(FIELD_FEED).orEmpty(),
        guid = extras.getString(FIELD_GUID).orEmpty(),
        title = extras.getString(FIELD_NAME).orEmpty(),
        showTitle = extras.getString(FIELD_SHOW).orEmpty(),
        author = extras.getString(FIELD_ARTIST).orEmpty(),
        description = extras.getString(FIELD_DESCRIPTION).orEmpty(),
        audioUrl = extras.getString(FIELD_STREAM).orEmpty(),
        websiteUrl = extras.getString(FIELD_SOURCE).orEmpty(),
        artworkUrl = extras.getString(FIELD_ARTWORK).orEmpty(),
        mimeType = extras.getString(FIELD_MIME).orEmpty(),
        publishedAt = extras.getLong(FIELD_PUBLISHED),
        durationMs = extras.getLong(FIELD_DURATION),
        positionMs = extras.getLong(FIELD_POSITION),
    )
    return episode.takeIf(PodcastEpisode::isPlayable)
}

private fun String.toUriOrNull(): Uri? = takeIf(String::isNotBlank)?.let(Uri::parse)
