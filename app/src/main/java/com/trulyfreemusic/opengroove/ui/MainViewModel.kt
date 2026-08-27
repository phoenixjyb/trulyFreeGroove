package com.trulyfreemusic.opengroove.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trulyfreemusic.opengroove.BuildConfig
import com.trulyfreemusic.opengroove.data.JamendoCatalog
import com.trulyfreemusic.opengroove.data.PlaylistStore
import com.trulyfreemusic.opengroove.data.SearchLanguage
import com.trulyfreemusic.opengroove.data.WikimediaCatalog
import com.trulyfreemusic.opengroove.model.Track
import com.trulyfreemusic.opengroove.radio.RadioBrowseMode
import com.trulyfreemusic.opengroove.radio.RadioBrowserCatalog
import com.trulyfreemusic.opengroove.radio.RadioCountry
import com.trulyfreemusic.opengroove.radio.RadioStation
import com.trulyfreemusic.opengroove.radio.RadioUiState
import com.trulyfreemusic.opengroove.radio.SavedStationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val playlistStore = PlaylistStore(application)
    private val radioCatalog = RadioBrowserCatalog()
    private val savedStationStore = SavedStationStore(application)
    private val mutableSearchState = MutableStateFlow(SearchUiState())
    private val mutableRadioState = MutableStateFlow(RadioUiState())
    private var searchJob: Job? = null
    private var radioJob: Job? = null

    val searchState: StateFlow<SearchUiState> = mutableSearchState.asStateFlow()
    val playlists: StateFlow<Map<String, List<Track>>> = playlistStore.playlists
    val radioState: StateFlow<RadioUiState> = mutableRadioState.asStateFlow()
    val savedStations: StateFlow<List<RadioStation>> = savedStationStore.stations
    val jamendoConfigured: Boolean = BuildConfig.JAMENDO_CONFIGURED

    init {
        search("")
        loadRadio()
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
                    val tracks = commonsResult.getOrDefault(emptyList()) +
                        jamendoResult?.getOrDefault(emptyList()).orEmpty()
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
                    mutableSearchState.value = mutableSearchState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Search failed. Check your connection.",
                    )
                }
        }
    }

    fun createPlaylist(name: String): Boolean = playlistStore.create(name)
    fun addToPlaylist(playlistName: String, track: Track) = playlistStore.add(playlistName, track)
    fun removeFromPlaylist(playlistName: String, trackId: String) = playlistStore.remove(playlistName, trackId)

    fun setRadioQuery(query: String) {
        mutableRadioState.value = mutableRadioState.value.copy(query = query)
    }

    fun searchRadio() = loadRadio()

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

    fun toggleSavedStation(station: RadioStation) = savedStationStore.toggle(station)

    fun registerStationClick(stationId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { radioCatalog.registerClick(stationId) }
        }
    }

    private fun loadRadio() {
        radioJob?.cancel()
        radioJob = viewModelScope.launch {
            val snapshot = mutableRadioState.value
            mutableRadioState.value = snapshot.copy(isLoading = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    val countries = if (snapshot.countries.isEmpty()) {
                        runCatching { radioCatalog.countries() }.getOrDefault(emptyList())
                    } else snapshot.countries
                    val stations = radioCatalog.search(
                        name = snapshot.query,
                        countryCode = snapshot.selectedCountry?.code,
                        tag = snapshot.selectedTag,
                    )
                    countries to stations
                }
            }.onSuccess { (countries, stations) ->
                mutableRadioState.value = mutableRadioState.value.copy(
                    countries = countries,
                    stations = stations,
                    isLoading = false,
                    error = if (stations.isEmpty()) "No working stations found. Try another filter." else null,
                )
            }.onFailure { error ->
                mutableRadioState.value = mutableRadioState.value.copy(
                    isLoading = false,
                    error = error.message ?: "Radio search failed. Check your connection.",
                )
            }
        }
    }
}
