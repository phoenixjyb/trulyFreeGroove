package com.trulyfreemusic.opengroove.podcast

import com.trulyfreemusic.opengroove.data.SearchLanguage

data class PodcastUiState(
    val query: String = "",
    val libraryQuery: String = "",
    val language: SearchLanguage = SearchLanguage.ALL,
    val results: List<PodcastShow> = emptyList(),
    val selectedShow: PodcastShow? = null,
    val episodes: List<PodcastEpisode> = emptyList(),
    val isSearching: Boolean = false,
    val isLoadingFeed: Boolean = false,
    val error: String? = null,
)
