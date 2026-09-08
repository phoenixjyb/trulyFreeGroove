package com.trulyfreemusic.opengroove.localization

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class UiLocalizationTest {
    @Test fun localeDefaultsDistinguishEnglishSimplifiedAndTraditionalChinese() {
        assertEquals(UiLanguage.ENGLISH, UiLanguage.fromLocale(Locale.forLanguageTag("en-GB")))
        assertEquals(UiLanguage.SIMPLIFIED_CHINESE, UiLanguage.fromLocale(Locale.forLanguageTag("zh-CN")))
        assertEquals(UiLanguage.TRADITIONAL_CHINESE, UiLanguage.fromLocale(Locale.forLanguageTag("zh-HK")))
        assertEquals(UiLanguage.TRADITIONAL_CHINESE, UiLanguage.fromLocale(Locale.forLanguageTag("zh-Hant")))
    }

    @Test fun primaryNavigationAndRadioLabelsTranslateInBothScripts() {
        assertEquals("发现", localizeUiText("Discover", UiLanguage.SIMPLIFIED_CHINESE))
        assertEquals("探索", localizeUiText("Discover", UiLanguage.TRADITIONAL_CHINESE))
        assertEquals("网络电台", localizeUiText("Internet Radio", UiLanguage.SIMPLIFIED_CHINESE))
        assertEquals("網絡電台", localizeUiText("Internet Radio", UiLanguage.TRADITIONAL_CHINESE))
        assertEquals("所有国家和地区", localizeUiText("All countries", UiLanguage.SIMPLIFIED_CHINESE))
        assertEquals("所有國家和地區", localizeUiText("All countries", UiLanguage.TRADITIONAL_CHINESE))
    }

    @Test fun dynamicCountsAndSearchTitlesRemainIntact() {
        assertEquals("已保存 12", localizeUiText("Saved 12", UiLanguage.SIMPLIFIED_CHINESE))
        assertEquals("已儲存 12", localizeUiText("Saved 12", UiLanguage.TRADITIONAL_CHINESE))
        assertEquals("3 首歌曲", localizeUiText("3 tracks", UiLanguage.SIMPLIFIED_CHINESE))
        assertEquals("搜尋結果： “BBC”", localizeUiText("Results for “BBC”", UiLanguage.TRADITIONAL_CHINESE))
        assertEquals("加入 “Morning News”", localizeUiText("Add “Morning News”", UiLanguage.TRADITIONAL_CHINESE))
        assertEquals("從此處繼續 12:45", localizeUiText("Resume at 12:45", UiLanguage.TRADITIONAL_CHINESE))
    }

    @Test fun countryBrowseAndCountryMusicHaveDistinctTranslations() {
        assertEquals("国家或地区", localizeUiText("Country or region", UiLanguage.SIMPLIFIED_CHINESE))
        assertEquals("乡村", localizeUiText("Country", UiLanguage.SIMPLIFIED_CHINESE))
        assertEquals("國家或地區", localizeUiText("Country or region", UiLanguage.TRADITIONAL_CHINESE))
        assertEquals("鄉村", localizeUiText("Country", UiLanguage.TRADITIONAL_CHINESE))
    }

    @Test fun applicationErrorsTranslateWithoutChangingEmbeddedBrandNames() {
        assertEquals(
            "YouTube 搜索失败，请检查密钥、配额和网络连接。",
            localizeUiText(
                "YouTube search failed. Check the key, quota, and connection.",
                UiLanguage.SIMPLIFIED_CHINESE,
            ),
        )
    }

    @Test fun EnglishLeavesProviderAndUserContentUntouched() {
        assertEquals("香港電台第一台", localizeUiText("香港電台第一台", UiLanguage.ENGLISH))
        assertEquals("Custom station", localizeUiText("Custom station", UiLanguage.ENGLISH))
    }
}
