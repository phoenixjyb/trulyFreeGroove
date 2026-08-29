package com.trulyfreemusic.opengroove.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trulyfreemusic.opengroove.BuildConfig
import com.trulyfreemusic.opengroove.data.JamendoCatalog
import com.trulyfreemusic.opengroove.data.SearchLanguage
import com.trulyfreemusic.opengroove.data.WikimediaCatalog
import com.trulyfreemusic.opengroove.library.LibraryRepository
import com.trulyfreemusic.opengroove.model.Track
import com.trulyfreemusic.opengroove.podcast.ApplePodcastCatalog
import com.trulyfreemusic.opengroove.podcast.PodcastEpisode
import com.trulyfreemusic.opengroove.podcast.PodcastFeedCatalog
import com.trulyfreemusic.opengroove.podcast.PodcastShow
import com.trulyfreemusic.opengroove.podcast.PodcastUiState
import com.trulyfreemusic.opengroove.radio.RadioBrowseMode
import com.trulyfreemusic.opengroove.radio.RadioBrowserCatalog
import com.trulyfreemusic.opengroove.radio.RadioCountry
import com.trulyfreemusic.opengroove.radio.RadioStation
import com.trulyfreemusic.opengroove.radio.RadioUiState
import com.trulyfreemusic.opengroove.youtube.YouTubeCatalog
import com.trulyfreemusic.opengroove.youtube.YouTubeUiState
import com.trulyfreemusic.opengroove.youtube.YouTubeVideo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SearchUiState(
    val query: String = "",
    val language: SearchLanguage = SearchLanguage.ALL,
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val jamendoCatalog = BuildConfig.JAMENDO_CLIENT_ID.takeIf(String::isNotBlank)?.let(::JamendoCatalog)
    private val wikimediaCatalog = WikimediaCatalog()
    private val library = LibraryRepository(application)
    private val radioCatalog = RadioBrowserCatalog()
    private val applePodcastCatalog = ApplePodcastCatalog()
    private val podcastFeedCatalog = PodcastFeedCatalog()
    private val youtubeCatalog = BuildConfig.YOUTUBE_API_KEY.takeIf(String::isNotBlank)?.let { key ->
        YouTubeCatalog(application, key)
    }
    private val mutableSearchState = MutableStateFlow(SearchUiState())
    private val mutableRadioState = MutableStateFlow(RadioUiState())
    private val mutablePodcastState = MutableStateFlow(PodcastUiState())
    private val mutableYouTubeState = MutableStateFlow(YouTubeUiState())
    private var searchJob: Job? = null
    private var radioJob: Job? = null
    private var podcastSearchJob: Job? = null
    private var podcastFeedJob: Job? = null
    private var podcastEpisodesJob: Job? = null
    private var youtubeSearchJob: Job? = null

    val searchState: StateFlow<SearchUiState> = mutableSearchState.asStateFlow()
    val playlists: StateFlow<Map<String, List<Track>>> = library.playlists.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap(),
    )
    val radioState: StateFlow<RadioUiState> = mutableRadioState.asStateFlow()
    val savedStations: StateFlow<List<RadioStation>> = library.savedStations.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )
    val recentStations: StateFlow<List<RadioStation>> = library.recentStations.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )
    val podcastState: StateFlow<PodcastUiState> = mutablePodcastState.asStateFlow()
    val podcastSubscriptions: StateFlow<List<PodcastShow>> = library.subscriptions.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )
    val unplayedPodcastEpisodes: StateFlow<List<PodcastEpisode>> = library.unplayedPodcastEpisodes.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )
    val youtubeState: StateFlow<YouTubeUiState> = mutableYouTubeState.asStateFlow()
    val savedYouTubeVideos: StateFlow<List<YouTubeVideo>> = library.savedYouTubeVideos.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )
    val jamendoConfigured: Boolean = BuildConfig.JAMENDO_CONFIGURED
    val youtubeConfigured: Boolean = BuildConfig.YOUTUBE_CONFIGURED

    init {
        viewModelScope.launch(Dispatchers.IO) { runCatching { library.migrateLegacyData() } }
        search("")
        loadRadio()
        refreshStaleSavedYouTubeVideos()
    }

    fun setQuery(query: String) {
        mutableSearchState.value = mutableSearchState.value.copy(query = query)
    }

    fun setLanguage(language: SearchLanguage) {
        mutableSearchState.value = mutableSearchState.value.copy(language = language)
        search()
    }

    fun search(query: String = mutableSearchState.value.query) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            mutableSearchState.value = mutableSearchState.value.copy(
                query = query,
                isLoading = true,
                error = null,
            )
            val language = mutableSearchState.value.language
            runCatching {
                withContext(Dispatchers.IO) {
                    val commonsResult = runCatching { wikimediaCatalog.search(query, language) }
                    val jamendoResult = jamendoCatalog?.let { catalog ->
                        runCatching { catalog.search(query, language) }
                    }
                    val tracks = (
                        commonsResult.getOrDefault(emptyList()) +
                            jamendoResult?.getOrDefault(emptyList()).orEmpty()
                        ).distinctBy { "${it.providerName.lowercase()}:${it.sourceUrl}" }
                    if (tracks.isEmpty()) {
                        commonsResult.exceptionOrNull()?.let { throw it }
                        jamendoResult?.exceptionOrNull()?.let { throw it }
                    }
                    tracks
                }
            }
                .onSuccess { tracks ->
                    mutableSearchState.value = mutableSearchState.value.copy(
                        tracks = tracks,
                        isLoading = false,
                        error = if (tracks.isEmpty()) "No licensed tracks found. Try another search." else null,
                    )
                }
                .onFailure { error ->
                    if (error is CancellationException) return@onFailure
                    mutableSearchState.value = mutableSearchState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Search failed. Check your connection.",
                    )
                }
        }
    }

    fun createPlaylist(name: String): Boolean {
        val cleanName = name.trim()
        if (cleanName.isBlank() || cleanName.length > 50 || playlists.value.containsKey(cleanName)) return false
        viewModelScope.launch(Dispatchers.IO) { library.createPlaylist(cleanName) }
        return true
    }

    fun addToPlaylist(playlistName: String, track: Track) {
        viewModelScope.launch(Dispatchers.IO) { library.addToPlaylist(playlistName, track) }
    }

    fun removeFromPlaylist(playlistName: String, trackId: String) {
        viewModelScope.launch(Dispatchers.IO) { library.removeFromPlaylist(playlistName, trackId) }
    }

    fun setYouTubeQuery(query: String) {
        mutableYouTubeState.value = mutableYouTubeState.value.copy(query = query)
    }

    fun setYouTubeLanguage(language: SearchLanguage) {
        mutableYouTubeState.value = mutableYouTubeState.value.copy(language = language)
    }

    fun searchYouTube() {
        val snapshot = mutableYouTubeState.value
        val catalog = youtubeCatalog
        if (catalog == null) {
            mutableYouTubeState.value = snapshot.copy(
                isSearching = false,
                error = "Add a restricted YouTube Data API key to enable in-app search.",
            )
            return
        }
        if (snapshot.query.isBlank()) {
            mutableYouTubeState.value = snapshot.copy(
                isSearching = false,
                error = "Type a song, artist, or channel before searching YouTube.",
            )
            return
        }
        youtubeSearchJob?.cancel()
        youtubeSearchJob = viewModelScope.launch {
            mutableYouTubeState.value = snapshot.copy(isSearching = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) { catalog.search(snapshot.query, snapshot.language) }
            }.onSuccess { videos ->
                mutableYouTubeState.value = mutableYouTubeState.value.copy(
                    results = videos,
                    isSearching = false,
                    error = if (videos.isEmpty()) "No embeddable YouTube videos matched this search." else null,
                )
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                mutableYouTubeState.value = mutableYouTubeState.value.copy(
                    isSearching = false,
                    error = error.message ?: "YouTube search failed. Check the key, quota, and connection.",
                )
            }
        }
    }

    fun toggleSavedYouTubeVideo(video: YouTubeVideo) {
        viewModelScope.launch(Dispatchers.IO) { library.toggleSavedYouTubeVideo(video) }
    }

    fun setRadioQuery(query: String) {
        mutableRadioState.value = mutableRadioState.value.copy(query = query)
    }

    fun searchRadio() = loadRadio()

    fun loadMoreRadio() {
        val state = mutableRadioState.value
        if (!state.isLoading && !state.isLoadingMore && state.hasMore) loadRadio(append = true)
    }

    fun selectRadioCountry(country: RadioCountry?) {
        mutableRadioState.value = mutableRadioState.value.copy(
            selectedCountry = country,
            selectedTag = null,
            mode = if (country == null) RadioBrowseMode.ALL else RadioBrowseMode.COUNTRY,
        )
        loadRadio()
    }

    fun selectRadioTag(tag: String, mode: RadioBrowseMode) {
        require(mode == RadioBrowseMode.GENRE || mode == RadioBrowseMode.CATEGORY)
        mutableRadioState.value = mutableRadioState.value.copy(
            selectedCountry = null,
            selectedTag = tag,
            mode = mode,
        )
        loadRadio()
    }

    fun clearRadioFilters() {
        mutableRadioState.value = mutableRadioState.value.copy(
            selectedCountry = null,
            selectedTag = null,
            mode = RadioBrowseMode.ALL,
        )
        loadRadio()
    }

    fun toggleSavedStation(station: RadioStation) {
        viewModelScope.launch(Dispatchers.IO) { library.toggleSavedStation(station) }
    }

    fun recordRecentStation(station: RadioStation) {
        viewModelScope.launch(Dispatchers.IO) { library.recordRecentStation(station) }
    }

    fun clearRecentStations() {
        viewModelScope.launch(Dispatchers.IO) { library.clearRecentStations() }
    }

    fun setPodcastQuery(query: String) {
        mutablePodcastState.value = mutablePodcastState.value.copy(query = query)
    }

    fun setPodcastLibraryQuery(query: String) {
        mutablePodcastState.value = mutablePodcastState.value.copy(libraryQuery = query)
    }

    fun setPodcastLanguage(language: SearchLanguage) {
        mutablePodcastState.value = mutablePodcastState.value.copy(language = language)
    }

    fun searchPodcasts() {
        val snapshot = mutablePodcastState.value
        podcastSearchJob?.cancel()
        podcastSearchJob = viewModelScope.launch {
            mutablePodcastState.value = snapshot.copy(isSearching = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    applePodcastCatalog.search(snapshot.query, snapshot.language)
                }
            }.onSuccess { shows ->
                mutablePodcastState.value = mutablePodcastState.value.copy(
                    results = shows,
                    isSearching = false,
                    error = if (shows.isEmpty()) "No podcasts found. Try another title, host, or language." else null,
                )
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                mutablePodcastState.value = mutablePodcastState.value.copy(
                    isSearching = false,
                    error = error.message ?: "Podcast search failed. Check your connection.",
                )
            }
        }
    }

    fun addPodcastFeed(feedUrl: String) {
        selectPodcast(
            PodcastShow(
                catalogId = "",
                title = "Podcast",
                author = "",
                description = "",
                feedUrl = feedUrl,
                artworkUrl = "",
                websiteUrl = "",
                genre = "",
                country = "",
            ),
        )
    }

    fun selectPodcast(show: PodcastShow) {
        podcastEpisodesJob?.cancel()
        podcastEpisodesJob = viewModelScope.launch {
            library.episodes(show.feedUrl).collect { episodes ->
                if (mutablePodcastState.value.selectedShow?.feedUrl == show.feedUrl) {
                    mutablePodcastState.value = mutablePodcastState.value.copy(episodes = episodes)
                }
            }
        }
        mutablePodcastState.value = mutablePodcastState.value.copy(
            selectedShow = show,
            episodes = emptyList(),
            error = null,
        )
        refreshSelectedPodcast()
    }

    fun closePodcast() {
        podcastFeedJob?.cancel()
        podcastEpisodesJob?.cancel()
        mutablePodcastState.value = mutablePodcastState.value.copy(
            selectedShow = null,
            episodes = emptyList(),
            isLoadingFeed = false,
            error = null,
        )
    }

    fun refreshSelectedPodcast() {
        val show = mutablePodcastState.value.selectedShow ?: return
        podcastFeedJob?.cancel()
        podcastFeedJob = viewModelScope.launch {
            mutablePodcastState.value = mutablePodcastState.value.copy(isLoadingFeed = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) { podcastFeedCatalog.load(show.feedUrl, show) }
            }.onSuccess { feed ->
                withContext(Dispatchers.IO) { library.upsertPodcast(feed.show, feed.episodes) }
                mutablePodcastState.value = mutablePodcastState.value.copy(
                    selectedShow = feed.show,
                    isLoadingFeed = false,
                    error = null,
                )
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                mutablePodcastState.value = mutablePodcastState.value.copy(
                    isLoadingFeed = false,
                    error = error.message ?: "This publisher feed could not be loaded.",
                )
            }
        }
    }

    fun togglePodcastSubscription(show: PodcastShow) {
        val subscribed = podcastSubscriptions.value.any { it.feedUrl == show.feedUrl }
        viewModelScope.launch(Dispatchers.IO) { library.setPodcastSubscribed(show, !subscribed) }
    }

    fun updatePodcastProgress(episodeId: String, positionMs: Long, durationMs: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            library.updateEpisodeProgress(episodeId, positionMs, durationMs)
        }
    }

    fun togglePodcastPlayed(episode: PodcastEpisode) {
        viewModelScope.launch(Dispatchers.IO) {
            library.setEpisodeCompleted(episode.episodeId, !episode.completed)
        }
    }

    fun registerStationClick(stationId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { radioCatalog.registerClick(stationId) }
        }
    }

    private fun refreshStaleSavedYouTubeVideos() {
        viewModelScope.launch(Dispatchers.IO) {
            val staleIds = library.staleSavedYouTubeVideoIds()
            if (staleIds.isEmpty()) return@launch
            val catalog = youtubeCatalog
            if (catalog == null) {
                library.deleteSavedYouTubeVideos(staleIds)
                return@launch
            }
            staleIds.chunked(50).forEach { ids ->
                val refreshed = try {
                    catalog.details(ids)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    library.deleteSavedYouTubeVideos(ids)
                    return@forEach
                }
                library.refreshSavedYouTubeVideos(refreshed)
                library.deleteSavedYouTubeVideos(ids - refreshed.map(YouTubeVideo::videoId).toSet())
            }
        }
    }

    private fun loadRadio(append: Boolean = false) {
        radioJob?.cancel()
        radioJob = viewModelScope.launch {
            val snapshot = mutableRadioState.value
            val offset = if (append) snapshot.nextOffset else 0
            mutableRadioState.value = snapshot.copy(
                isLoading = !append,
                isLoadingMore = append,
                error = null,
            )
            runCatching {
                withContext(Dispatchers.IO) {
                    val countries = if (snapshot.countries.isEmpty()) {
                        runCatching { radioCatalog.countries() }.getOrDefault(emptyList())
                    } else snapshot.countries
                    val stations = radioCatalog.search(
                        name = snapshot.query,
                        countryCode = snapshot.selectedCountry?.code,
                        tag = snapshot.selectedTag,
                        offset = offset,
                    )
                    countries to stations
                }
            }.onSuccess { (countries, stations) ->
                val combined = if (append) {
                    (mutableRadioState.value.stations + stations).distinctBy(RadioStation::id)
                } else {
                    stations
                }
                mutableRadioState.value = mutableRadioState.value.copy(
                    countries = countries,
                    stations = combined,
                    isLoading = false,
                    isLoadingMore = false,
                    nextOffset = offset + stations.size,
                    hasMore = stations.size == RadioBrowserCatalog.PAGE_SIZE,
                    error = if (combined.isEmpty()) "No working stations found. Try another filter." else null,
                )
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                mutableRadioState.value = mutableRadioState.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    error = error.message ?: if (append) {
                        "Could not load more stations. Try again."
                    } else {
                        "Radio search failed. Check your connection."
                    },
                )
            }
        }
    }
}
