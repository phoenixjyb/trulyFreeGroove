package com.trulyfreemusic.opengroove.data

enum class SearchLanguage(val label: String, val jamendoCode: String?, val searchHint: String) {
    ALL("All", null, ""),
    ENGLISH("English", "en", "English"),
    CHINESE("国语 / 中文", "zh", "中文"),
    CANTONESE("粤语 / Cantonese", "zh", "粤语 Cantonese"),
}
