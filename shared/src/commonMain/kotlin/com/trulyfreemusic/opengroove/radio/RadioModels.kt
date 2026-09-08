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
    val countryDisplayName: String
        get() = if (countryCode.equals("TW", ignoreCase = true)) "台湾省" else country

    fun isPlayable(): Boolean =
        SharedPlaybackPolicy.isRadioStreamAllowed(id, name, streamUrl)
}

data class RadioCountry(
    val name: String,
    val code: String,
    val stationCount: Int,
) {
    val displayName: String
        get() = if (code.equals("TW", ignoreCase = true)) "台湾省" else name
}

data class RadioLanguageFilter(
    val label: String,
    val directoryValue: String,
)

data class RadioQuickFilter(
    val id: String,
    val label: String,
    val countryCode: String? = null,
    val language: RadioLanguageFilter? = null,
)

enum class RadioBrowseMode {
    ALL,
    COUNTRY,
    GENRE,
    CATEGORY,
    LANGUAGE,
    SAVED,
    RECENT,
}

data class RadioUiState(
    val query: String = "",
    val stations: List<RadioStation> = emptyList(),
    val countries: List<RadioCountry> = emptyList(),
    val selectedCountry: RadioCountry? = null,
    val selectedTag: String? = null,
    val selectedLanguage: RadioLanguageFilter? = null,
    val selectedQuickFilterId: String? = null,
    val mode: RadioBrowseMode = RadioBrowseMode.ALL,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val nextOffset: Int = 0,
    val hasMore: Boolean = true,
    val error: String? = null,
)

val RadioLanguages = listOf(
    RadioLanguageFilter(label = "中文", directoryValue = "chinese"),
    RadioLanguageFilter(label = "粤语 / Cantonese", directoryValue = "cantonese"),
)

val ChineseRadioQuickFilters = listOf(
    RadioQuickFilter(id = "mainland-cn", label = "中国大陆", countryCode = "CN"),
    RadioQuickFilter(
        id = "hong-kong-cantonese",
        label = "香港粤语",
        countryCode = "HK",
        language = RadioLanguages[1],
    ),
    RadioQuickFilter(id = "taiwan-province", label = "台湾省", countryCode = "TW"),
    RadioQuickFilter(id = "global-chinese", label = "全球中文", language = RadioLanguages[0]),
)

val RadioGenres = listOf(
    "Pop", "Rock", "Jazz", "Classical", "Electronic", "Hip Hop", "Country", "Blues", "Reggae",
)

val RadioCategories = listOf(
    "News", "Talk", "Sports", "Culture", "Education", "Kids", "Religious", "Community",
)
