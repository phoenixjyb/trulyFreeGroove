package com.trulyfreemusic.opengroove.library

import android.content.Context
import com.trulyfreemusic.opengroove.model.PlaybackMode
import com.trulyfreemusic.opengroove.model.Track
import com.trulyfreemusic.opengroove.podcast.PodcastEpisode
import com.trulyfreemusic.opengroove.podcast.PodcastShow
import com.trulyfreemusic.opengroove.radio.RadioStation
import com.trulyfreemusic.opengroove.radio.toRadioStationOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class LibraryRepository(private val context: Context) {
    private val dao = OpenGrooveDatabase.get(context).libraryDao()

    val playlists: Flow<Map<String, List<Track>>> = combine(
        dao.observePlaylists(),
        dao.observePlaylistTracks(),
    ) { playlists, tracks ->
        val byPlaylist = tracks.groupBy(PlaylistTrackEntity::playlistName)
        playlists.associate { playlist ->
            playlist.name to byPlaylist[playlist.name].orEmpty().map(PlaylistTrackEntity::toTrack)
        }
    }

    val savedStations: Flow<List<RadioStation>> =
        dao.observeSavedStations().map { stations -> stations.map(SavedStationEntity::toRadioStation) }

    val recentStations: Flow<List<RadioStation>> =
        dao.observeRecentStations().map { stations -> stations.map(RecentStationEntity::toRadioStation) }

    val subscriptions: Flow<List<PodcastShow>> =
        dao.observeSubscriptions().map { shows -> shows.map(PodcastShowEntity::toPodcastShow) }

    val unplayedPodcastEpisodes: Flow<List<PodcastEpisode>> =
        dao.observeUnplayedPodcastEpisodes().map { episodes -> episodes.map(PodcastEpisodeEntity::toPodcastEpisode) }

    suspend fun subscribedPodcasts(): List<PodcastShow> =
        dao.subscribedPodcastShows().map(PodcastShowEntity::toPodcastShow)

    suspend fun migrateLegacyData() {
        val migrationPreferences = context.getSharedPreferences("open_groove_room_migration", Context.MODE_PRIVATE)
        if (migrationPreferences.getBoolean(MIGRATION_KEY, false)) return

        val now = System.currentTimeMillis()
        legacyPlaylists().entries.forEachIndexed { playlistIndex, (name, tracks) ->
            dao.insertPlaylist(PlaylistEntity(name, now + playlistIndex))
            tracks.forEachIndexed { position, track ->
                dao.insertPlaylistTrack(track.toEntity(name, position))
            }
        }
        legacyStations("open_groove_radio", "saved_stations_v1").forEachIndexed { index, station ->
            dao.upsertSavedStation(station.toSavedEntity(now - index))
        }
        legacyStations("open_groove_recent_radio", "recent_stations_v1").forEachIndexed { index, station ->
            dao.upsertRecentStation(station.toRecentEntity(now - index))
        }
        dao.trimRecentStations()
        migrationPreferences.edit().putBoolean(MIGRATION_KEY, true).apply()
    }

    suspend fun createPlaylist(name: String): Boolean {
        val cleanName = name.trim()
        if (cleanName.isBlank() || cleanName.length > 50) return false
        return dao.insertPlaylist(PlaylistEntity(cleanName, System.currentTimeMillis())) != -1L
    }

    suspend fun addToPlaylist(playlistName: String, track: Track) {
        dao.insertPlaylistTrack(track.toEntity(playlistName, dao.nextPlaylistPosition(playlistName)))
    }

    suspend fun removeFromPlaylist(playlistName: String, trackId: String) =
        dao.removePlaylistTrack(playlistName, trackId)

    suspend fun toggleSavedStation(station: RadioStation) {
        if (dao.isStationSaved(station.id)) dao.deleteSavedStation(station.id)
        else dao.upsertSavedStation(station.toSavedEntity(System.currentTimeMillis()))
    }

    suspend fun recordRecentStation(station: RadioStation) {
        dao.upsertRecentStation(station.toRecentEntity(System.currentTimeMillis()))
        dao.trimRecentStations()
    }

    suspend fun clearRecentStations() = dao.clearRecentStations()

    fun episodes(feedUrl: String): Flow<List<PodcastEpisode>> =
        dao.observePodcastEpisodes(feedUrl).map { episodes -> episodes.map(PodcastEpisodeEntity::toPodcastEpisode) }

    suspend fun cachedEpisodes(feedUrl: String): List<PodcastEpisode> =
        dao.podcastEpisodes(feedUrl).map(PodcastEpisodeEntity::toPodcastEpisode)

    suspend fun upsertPodcast(show: PodcastShow, episodes: List<PodcastEpisode>, subscribed: Boolean? = null) {
        val existing = dao.podcastShow(show.feedUrl)
        val now = System.currentTimeMillis()
        dao.upsertPodcastShow(
            show.toEntity(
                subscribed = subscribed ?: existing?.subscribed ?: show.subscribed,
                subscribedAt = existing?.subscribedAt?.takeIf { it > 0 } ?: now,
                refreshedAt = now,
            ),
        )
        dao.replacePodcastMetadata(episodes.map(PodcastEpisode::toEntity))
    }

    suspend fun setPodcastSubscribed(show: PodcastShow, subscribed: Boolean) {
        val existing = dao.podcastShow(show.feedUrl)
        if (existing == null) {
            dao.upsertPodcastShow(show.toEntity(subscribed, System.currentTimeMillis(), 0L))
        } else {
            dao.setPodcastSubscribed(show.feedUrl, subscribed, System.currentTimeMillis())
        }
    }

    suspend fun episode(episodeId: String): PodcastEpisode? =
        dao.podcastEpisode(episodeId)?.toPodcastEpisode()

    suspend fun updateEpisodeProgress(episodeId: String, positionMs: Long, durationMs: Long) {
        val safeDuration = durationMs.coerceAtLeast(0L)
        val completed = safeDuration > 0 && positionMs >= safeDuration - 30_000L
        dao.updateEpisodeProgress(
            episodeId = episodeId,
            positionMs = if (completed) 0L else positionMs.coerceAtLeast(0L),
            durationMs = safeDuration,
            completed = completed,
        )
    }

    suspend fun setEpisodeCompleted(episodeId: String, completed: Boolean) =
        dao.setEpisodeCompleted(episodeId, completed)

    private fun legacyPlaylists(): Map<String, List<Track>> = runCatching {
        val payload = context.getSharedPreferences("open_groove_playlists", Context.MODE_PRIVATE)
            .getString("playlists_v1", null) ?: return@runCatching emptyMap()
        decodeLegacyPlaylists(payload)
    }.getOrDefault(emptyMap())

    private fun legacyStations(preferenceName: String, key: String): List<RadioStation> = runCatching {
        val payload = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
            .getString(key, null) ?: return@runCatching emptyList()
        decodeLegacyStations(payload)
    }.getOrDefault(emptyList())

    private companion object {
        const val MIGRATION_KEY = "preferences_to_room_v1"
    }
}

internal fun decodeLegacyPlaylists(payload: String): Map<String, List<Track>> {
    val root = JSONObject(payload)
    return buildMap {
        root.keys().forEach { name ->
            val array = root.optJSONArray(name) ?: JSONArray()
            put(name, buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.toLegacyTrackOrNull()?.let(::add)
                }
            })
        }
    }
}

internal fun decodeLegacyStations(payload: String): List<RadioStation> {
    val array = JSONArray(payload)
    return buildList {
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.toRadioStationOrNull()?.let(::add)
        }
    }
}

private fun JSONObject.toLegacyTrackOrNull(): Track? = runCatching {
    Track(
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
        playbackMode = runCatching { PlaybackMode.valueOf(optString("playbackMode")) }
            .getOrDefault(PlaybackMode.EXTERNAL_ONLY),
    )
}.getOrNull()

private fun Track.toEntity(playlistName: String, position: Int) = PlaylistTrackEntity(
    playlistName = playlistName,
    trackId = id,
    position = position,
    title = title,
    artist = artist,
    album = album,
    durationSeconds = durationSeconds,
    artworkUrl = artworkUrl,
    providerName = providerName,
    streamUrl = streamUrl,
    sourceUrl = sourceUrl,
    licenseUrl = licenseUrl,
    playbackMode = playbackMode.name,
)

private fun PlaylistTrackEntity.toTrack() = Track(
    id = trackId,
    title = title,
    artist = artist,
    album = album,
    durationSeconds = durationSeconds,
    artworkUrl = artworkUrl,
    providerName = providerName,
    streamUrl = streamUrl,
    sourceUrl = sourceUrl,
    licenseUrl = licenseUrl,
    playbackMode = runCatching { PlaybackMode.valueOf(playbackMode) }.getOrDefault(PlaybackMode.EXTERNAL_ONLY),
)

private fun RadioStation.toSavedEntity(timestamp: Long) = SavedStationEntity(
    stationId = id, name = name, streamUrl = streamUrl, homepageUrl = homepageUrl,
    faviconUrl = faviconUrl, country = country, countryCode = countryCode, language = language,
    tagsJson = JSONArray(tags).toString(), codec = codec, bitrate = bitrate, votes = votes,
    isOnline = isOnline, lastCheckedAt = lastCheckedAt, isHls = isHls, savedAt = timestamp,
)

private fun RadioStation.toRecentEntity(timestamp: Long) = RecentStationEntity(
    stationId = id, name = name, streamUrl = streamUrl, homepageUrl = homepageUrl,
    faviconUrl = faviconUrl, country = country, countryCode = countryCode, language = language,
    tagsJson = JSONArray(tags).toString(), codec = codec, bitrate = bitrate, votes = votes,
    isOnline = isOnline, lastCheckedAt = lastCheckedAt, isHls = isHls, playedAt = timestamp,
)

private fun SavedStationEntity.toRadioStation() = stationFromEntity(
    stationId, name, streamUrl, homepageUrl, faviconUrl, country, countryCode, language,
    tagsJson, codec, bitrate, votes, isOnline, lastCheckedAt, isHls,
)

private fun RecentStationEntity.toRadioStation() = stationFromEntity(
    stationId, name, streamUrl, homepageUrl, faviconUrl, country, countryCode, language,
    tagsJson, codec, bitrate, votes, isOnline, lastCheckedAt, isHls,
)

private fun stationFromEntity(
    id: String,
    name: String,
    streamUrl: String,
    homepageUrl: String,
    faviconUrl: String,
    country: String,
    countryCode: String,
    language: String,
    tagsJson: String,
    codec: String,
    bitrate: Int,
    votes: Int,
    isOnline: Boolean,
    lastCheckedAt: String,
    isHls: Boolean,
) = RadioStation(
    id, name, streamUrl, homepageUrl, faviconUrl, country, countryCode, language,
    tags = runCatching {
        val array = JSONArray(tagsJson)
        List(array.length()) { array.optString(it) }.filter(String::isNotBlank)
    }.getOrDefault(emptyList()),
    codec, bitrate, votes, isOnline, lastCheckedAt, isHls,
)

private fun PodcastShow.toEntity(subscribed: Boolean, subscribedAt: Long, refreshedAt: Long) = PodcastShowEntity(
    feedUrl = feedUrl,
    catalogId = catalogId,
    title = title,
    author = author,
    description = description,
    artworkUrl = artworkUrl,
    websiteUrl = websiteUrl,
    genre = genre,
    country = country,
    subscribed = subscribed,
    subscribedAt = subscribedAt,
    lastRefreshedAt = refreshedAt,
)

private fun PodcastShowEntity.toPodcastShow() = PodcastShow(
    catalogId, title, author, description, feedUrl, artworkUrl, websiteUrl, genre, country, subscribed,
)

private fun PodcastEpisode.toEntity() = PodcastEpisodeEntity(
    episodeId, feedUrl, guid, title, showTitle, author, description, audioUrl, websiteUrl,
    artworkUrl, mimeType, publishedAt, durationMs, positionMs, completed,
)

private fun PodcastEpisodeEntity.toPodcastEpisode() = PodcastEpisode(
    episodeId, feedUrl, guid, title, showTitle, author, description, audioUrl, websiteUrl,
    artworkUrl, mimeType, publishedAt, durationMs, positionMs, completed,
)
