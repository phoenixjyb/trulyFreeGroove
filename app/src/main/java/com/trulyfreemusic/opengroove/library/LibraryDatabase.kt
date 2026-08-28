package com.trulyfreemusic.opengroove.library

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @androidx.room.PrimaryKey val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistName", "trackId"],
    indices = [Index("playlistName")],
)
data class PlaylistTrackEntity(
    val playlistName: String,
    val trackId: String,
    val position: Int,
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Int,
    val artworkUrl: String,
    val providerName: String,
    val streamUrl: String,
    val sourceUrl: String,
    val licenseUrl: String,
    val playbackMode: String,
)

@Entity(tableName = "saved_stations")
data class SavedStationEntity(
    @androidx.room.PrimaryKey val stationId: String,
    val name: String,
    val streamUrl: String,
    val homepageUrl: String,
    val faviconUrl: String,
    val country: String,
    val countryCode: String,
    val language: String,
    val tagsJson: String,
    val codec: String,
    val bitrate: Int,
    val votes: Int,
    val isOnline: Boolean,
    val lastCheckedAt: String,
    val isHls: Boolean,
    val savedAt: Long,
)

@Entity(tableName = "recent_stations")
data class RecentStationEntity(
    @androidx.room.PrimaryKey val stationId: String,
    val name: String,
    val streamUrl: String,
    val homepageUrl: String,
    val faviconUrl: String,
    val country: String,
    val countryCode: String,
    val language: String,
    val tagsJson: String,
    val codec: String,
    val bitrate: Int,
    val votes: Int,
    val isOnline: Boolean,
    val lastCheckedAt: String,
    val isHls: Boolean,
    val playedAt: Long,
)

@Entity(tableName = "saved_youtube_videos", indices = [Index("savedAt"), Index("metadataRefreshedAtMs")])
data class SavedYouTubeVideoEntity(
    @androidx.room.PrimaryKey val videoId: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String,
    val durationSeconds: Int,
    val publishedAtMs: Long,
    val embeddable: Boolean,
    val madeForKids: Boolean?,
    val isLive: Boolean,
    val metadataRefreshedAtMs: Long,
    val savedAt: Long,
)

@Entity(tableName = "podcast_shows")
data class PodcastShowEntity(
    @androidx.room.PrimaryKey val feedUrl: String,
    val catalogId: String,
    val title: String,
    val author: String,
    val description: String,
    val artworkUrl: String,
    val websiteUrl: String,
    val genre: String,
    val country: String,
    val subscribed: Boolean,
    val subscribedAt: Long,
    val lastRefreshedAt: Long,
)

@Entity(
    tableName = "podcast_episodes",
    indices = [Index("feedUrl"), Index("publishedAt")],
)
data class PodcastEpisodeEntity(
    @androidx.room.PrimaryKey val episodeId: String,
    val feedUrl: String,
    val guid: String,
    val title: String,
    val showTitle: String,
    val author: String,
    val description: String,
    val audioUrl: String,
    val websiteUrl: String,
    val artworkUrl: String,
    val mimeType: String,
    val publishedAt: Long,
    val durationMs: Long,
    val positionMs: Long,
    val completed: Boolean,
)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt, name")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlist_tracks ORDER BY playlistName, position")
    fun observePlaylistTracks(): Flow<List<PlaylistTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistTrack(track: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_tracks WHERE playlistName = :playlistName AND trackId = :trackId")
    suspend fun removePlaylistTrack(playlistName: String, trackId: String)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_tracks WHERE playlistName = :playlistName")
    suspend fun nextPlaylistPosition(playlistName: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM saved_stations WHERE stationId = :stationId)")
    suspend fun isStationSaved(stationId: String): Boolean

    @Query("SELECT * FROM saved_stations ORDER BY savedAt DESC")
    fun observeSavedStations(): Flow<List<SavedStationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedStation(station: SavedStationEntity)

    @Query("DELETE FROM saved_stations WHERE stationId = :stationId")
    suspend fun deleteSavedStation(stationId: String)

    @Query("SELECT * FROM recent_stations ORDER BY playedAt DESC LIMIT 20")
    fun observeRecentStations(): Flow<List<RecentStationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecentStation(station: RecentStationEntity)

    @Query("DELETE FROM recent_stations")
    suspend fun clearRecentStations()

    @Query("DELETE FROM recent_stations WHERE stationId NOT IN (SELECT stationId FROM recent_stations ORDER BY playedAt DESC LIMIT 20)")
    suspend fun trimRecentStations()

    @Query("SELECT * FROM saved_youtube_videos WHERE metadataRefreshedAtMs >= :freshAfterMs ORDER BY savedAt DESC")
    fun observeSavedYouTubeVideos(freshAfterMs: Long): Flow<List<SavedYouTubeVideoEntity>>

    @Query("SELECT * FROM saved_youtube_videos WHERE videoId = :videoId LIMIT 1")
    suspend fun savedYouTubeVideo(videoId: String): SavedYouTubeVideoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedYouTubeVideo(video: SavedYouTubeVideoEntity)

    @Query("DELETE FROM saved_youtube_videos WHERE videoId = :videoId")
    suspend fun deleteSavedYouTubeVideo(videoId: String)

    @Query("SELECT videoId FROM saved_youtube_videos WHERE metadataRefreshedAtMs < :freshAfterMs ORDER BY savedAt DESC")
    suspend fun staleSavedYouTubeVideoIds(freshAfterMs: Long): List<String>

    @Query("DELETE FROM saved_youtube_videos WHERE videoId IN (:videoIds)")
    suspend fun deleteSavedYouTubeVideos(videoIds: List<String>)

    @Query("SELECT * FROM podcast_shows WHERE subscribed = 1 ORDER BY subscribedAt DESC")
    fun observeSubscriptions(): Flow<List<PodcastShowEntity>>

    @Query("SELECT * FROM podcast_shows WHERE subscribed = 1 ORDER BY subscribedAt DESC")
    suspend fun subscribedPodcastShows(): List<PodcastShowEntity>

    @Query("SELECT * FROM podcast_shows WHERE feedUrl = :feedUrl LIMIT 1")
    suspend fun podcastShow(feedUrl: String): PodcastShowEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPodcastShow(show: PodcastShowEntity)

    @Query("UPDATE podcast_shows SET subscribed = :subscribed, subscribedAt = :changedAt WHERE feedUrl = :feedUrl")
    suspend fun setPodcastSubscribed(feedUrl: String, subscribed: Boolean, changedAt: Long)

    @Query("SELECT * FROM podcast_episodes WHERE feedUrl = :feedUrl ORDER BY publishedAt DESC")
    fun observePodcastEpisodes(feedUrl: String): Flow<List<PodcastEpisodeEntity>>

    @Query(
        """
        SELECT podcast_episodes.* FROM podcast_episodes
        INNER JOIN podcast_shows ON podcast_shows.feedUrl = podcast_episodes.feedUrl
        WHERE podcast_shows.subscribed = 1 AND podcast_episodes.completed = 0
        ORDER BY CASE WHEN podcast_episodes.positionMs > 0 THEN 0 ELSE 1 END,
            podcast_episodes.publishedAt DESC
        LIMIT 200
        """,
    )
    fun observeUnplayedPodcastEpisodes(): Flow<List<PodcastEpisodeEntity>>

    @Query("SELECT * FROM podcast_episodes WHERE feedUrl = :feedUrl ORDER BY publishedAt DESC")
    suspend fun podcastEpisodes(feedUrl: String): List<PodcastEpisodeEntity>

    @Query("SELECT * FROM podcast_episodes WHERE episodeId = :episodeId LIMIT 1")
    suspend fun podcastEpisode(episodeId: String): PodcastEpisodeEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPodcastEpisodes(episodes: List<PodcastEpisodeEntity>)

    @Query("UPDATE podcast_episodes SET title = :title, showTitle = :showTitle, author = :author, description = :description, audioUrl = :audioUrl, websiteUrl = :websiteUrl, artworkUrl = :artworkUrl, mimeType = :mimeType, publishedAt = :publishedAt, durationMs = CASE WHEN :durationMs > 0 THEN :durationMs ELSE durationMs END WHERE episodeId = :episodeId")
    suspend fun refreshPodcastEpisode(
        episodeId: String,
        title: String,
        showTitle: String,
        author: String,
        description: String,
        audioUrl: String,
        websiteUrl: String,
        artworkUrl: String,
        mimeType: String,
        publishedAt: Long,
        durationMs: Long,
    )

    @Query("UPDATE podcast_episodes SET positionMs = :positionMs, durationMs = CASE WHEN :durationMs > 0 THEN :durationMs ELSE durationMs END, completed = :completed WHERE episodeId = :episodeId")
    suspend fun updateEpisodeProgress(episodeId: String, positionMs: Long, durationMs: Long, completed: Boolean)

    @Query("UPDATE podcast_episodes SET positionMs = 0, completed = :completed WHERE episodeId = :episodeId")
    suspend fun setEpisodeCompleted(episodeId: String, completed: Boolean)

    @Query("DELETE FROM podcast_episodes WHERE feedUrl = :feedUrl AND episodeId NOT IN (:retainedEpisodeIds)")
    suspend fun deleteStalePodcastEpisodes(feedUrl: String, retainedEpisodeIds: List<String>)

    @Query("SELECT feedUrl FROM podcast_shows WHERE subscribed = 0 ORDER BY lastRefreshedAt DESC LIMIT -1 OFFSET :keepCount")
    suspend fun staleUnsubscribedPodcastFeeds(keepCount: Int): List<String>

    @Query("DELETE FROM podcast_episodes WHERE feedUrl IN (:feedUrls)")
    suspend fun deletePodcastEpisodesForFeeds(feedUrls: List<String>)

    @Query("DELETE FROM podcast_shows WHERE feedUrl IN (:feedUrls) AND subscribed = 0")
    suspend fun deleteUnsubscribedPodcastShows(feedUrls: List<String>)

    @Transaction
    suspend fun replacePodcastMetadata(feedUrl: String, episodes: List<PodcastEpisodeEntity>) {
        require(episodes.isNotEmpty()) { "A podcast metadata refresh must contain at least one episode." }
        insertPodcastEpisodes(episodes)
        episodes.forEach { episode ->
            refreshPodcastEpisode(
                episodeId = episode.episodeId,
                title = episode.title,
                showTitle = episode.showTitle,
                author = episode.author,
                description = episode.description,
                audioUrl = episode.audioUrl,
                websiteUrl = episode.websiteUrl,
                artworkUrl = episode.artworkUrl,
                mimeType = episode.mimeType,
                publishedAt = episode.publishedAt,
                durationMs = episode.durationMs,
            )
        }
        deleteStalePodcastEpisodes(feedUrl, episodes.map(PodcastEpisodeEntity::episodeId))
    }

    @Transaction
    suspend fun pruneUnsubscribedPodcastCache(keepCount: Int) {
        val staleFeeds = staleUnsubscribedPodcastFeeds(keepCount)
        if (staleFeeds.isEmpty()) return
        deletePodcastEpisodesForFeeds(staleFeeds)
        deleteUnsubscribedPodcastShows(staleFeeds)
    }
}

@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        SavedStationEntity::class,
        RecentStationEntity::class,
        PodcastShowEntity::class,
        PodcastEpisodeEntity::class,
        SavedYouTubeVideoEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class OpenGrooveDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        @Volatile private var instance: OpenGrooveDatabase? = null

        fun get(context: Context): OpenGrooveDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                OpenGrooveDatabase::class.java,
                "open_groove_library.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS saved_youtube_videos (
                        videoId TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        channelTitle TEXT NOT NULL,
                        thumbnailUrl TEXT NOT NULL,
                        durationSeconds INTEGER NOT NULL,
                        publishedAtMs INTEGER NOT NULL,
                        embeddable INTEGER NOT NULL,
                        madeForKids INTEGER,
                        isLive INTEGER NOT NULL,
                        metadataRefreshedAtMs INTEGER NOT NULL,
                        savedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_saved_youtube_videos_savedAt ON saved_youtube_videos (savedAt)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_saved_youtube_videos_metadataRefreshedAtMs " +
                        "ON saved_youtube_videos (metadataRefreshedAtMs)",
                )
            }
        }
    }
}
