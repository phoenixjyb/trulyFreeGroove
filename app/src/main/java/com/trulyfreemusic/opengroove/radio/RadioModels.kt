package com.trulyfreemusic.opengroove.radio

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
) {
    fun isPlayable(): Boolean =
        id.isNotBlank() && name.isNotBlank() &&
            (streamUrl.startsWith("https://") || streamUrl.startsWith("http://"))
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
}

data class RadioUiState(
    val query: String = "",
    val stations: List<RadioStation> = emptyList(),
    val countries: List<RadioCountry> = emptyList(),
    val selectedCountry: RadioCountry? = null,
    val selectedTag: String? = null,
    val mode: RadioBrowseMode = RadioBrowseMode.ALL,
    val isLoading: Boolean = false,
    val error: String? = null,
)

val RadioGenres = listOf(
    "Pop",
    "Rock",
    "Jazz",
    "Classical",
    "Electronic",
    "Hip Hop",
    "Country",
    "Blues",
    "Reggae",
)

val RadioCategories = listOf(
    "News",
    "Talk",
    "Sports",
    "Culture",
    "Education",
    "Kids",
    "Religious",
    "Community",
)
