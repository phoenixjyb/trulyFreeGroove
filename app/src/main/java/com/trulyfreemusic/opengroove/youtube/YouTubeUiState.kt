package com.trulyfreemusic.opengroove.youtube

import com.trulyfreemusic.opengroove.data.SearchLanguage

data class YouTubeUiState(
    val query: String = "",
    val language: SearchLanguage = SearchLanguage.ALL,
    val results: List<YouTubeVideo> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null,
)
