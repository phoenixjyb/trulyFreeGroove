package com.trulyfreemusic.opengroove.radio

import com.trulyfreemusic.opengroove.SharedPlaybackPolicy

data class RadioStation(
    val id: String,
    val name: String,
    val streamUrl: String,
    val homepageUrl: String,
    val faviconUrl: String,
    val country: String,
    val countryCode: String,
    val language: String,
    val tags: List<String>,
    val codec: String,
    val bitrate: Int,
    val votes: Int,
    val isOnline: Boolean = true,
    val lastCheckedAt: String = "",
    val isHls: Boolean = false,
) {
    fun isPlayable(): Boolean =
        SharedPlaybackPolicy.isRadioStreamAllowed(id, name, streamUrl)
}

data class RadioCountry(
    val name: String,
    val code: String,
    val stationCount: Int,
)

enum class RadioBrowseMode {
    ALL,
    COUNTRY,
    GENRE,
    CATEGORY,
    SAVED,
    RECENT,
}

data class RadioUiState(
    val query: String = "",
    val stations: List<RadioStation> = emptyList(),
    val countries: List<RadioCountry> = emptyList(),
    val selectedCountry: RadioCountry? = null,
    val selectedTag: String? = null,
    val mode: RadioBrowseMode = RadioBrowseMode.ALL,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val nextOffset: Int = 0,
    val hasMore: Boolean = true,
    val error: String? = null,
)

val RadioGenres = listOf(
    "Pop", "Rock", "Jazz", "Classical", "Electronic", "Hip Hop", "Country", "Blues", "Reggae",
)

val RadioCategories = listOf(
    "News", "Talk", "Sports", "Culture", "Education", "Kids", "Religious", "Community",
)
