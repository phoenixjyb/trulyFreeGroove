import Foundation
import Testing
#if canImport(OpenGrooveIOSCore)
@testable import OpenGrooveIOSCore
#elseif canImport(OpenGroove)
@testable import OpenGroove
#endif

@Test
func interfaceLocaleDefaultsMatchAndroid() {
    #expect(UiLanguage.from(locale: Locale(identifier: "en-GB")) == .english)
    #expect(UiLanguage.from(locale: Locale(identifier: "zh-CN")) == .simplifiedChinese)
    #expect(UiLanguage.from(locale: Locale(identifier: "zh-HK")) == .traditionalChinese)
    #expect(UiLanguage.from(locale: Locale(identifier: "zh-MO")) == .traditionalChinese)
    #expect(UiLanguage.from(locale: Locale(identifier: "zh-TW")) == .traditionalChinese)
    #expect(UiLanguage.from(locale: Locale(identifier: "zh-Hant")) == .traditionalChinese)
}

@MainActor
@Test
func interfaceLanguageSelectionPersistsIndependently() throws {
    let suiteName = "UiLanguageTests.\(UUID().uuidString)"
    let defaults = try #require(UserDefaults(suiteName: suiteName))
    defer { defaults.removePersistentDomain(forName: suiteName) }

    let fresh = UiLanguageStore(defaults: defaults, deviceLocale: Locale(identifier: "zh-CN"))
    #expect(fresh.selection == .simplifiedChinese)
    fresh.select(.traditionalChinese)

    let restored = UiLanguageStore(defaults: defaults, deviceLocale: Locale(identifier: "en-US"))
    #expect(restored.selection == .traditionalChinese)
}

@Test
func primaryInterfaceCopyLocalizesInBothChineseScripts() {
    #expect(localizedUiText("Discover", locale: Locale(identifier: "zh-Hans")) == "发现")
    #expect(localizedUiText("Discover", locale: Locale(identifier: "zh-Hant")) == "探索")
    #expect(localizedUiText(
        "Internet stream • online when checked",
        locale: Locale(identifier: "zh-Hans")
    ) == "互联网音频流 • 目录检查时在线")
    #expect(localizedUiText(
        "Internet stream • online when checked",
        locale: Locale(identifier: "zh-Hant")
    ) == "網絡音訊串流 • 目錄檢查時在線")
}

@Test
func dynamicInterfaceCopyPreservesUserAndProviderContent() {
    #expect(localizedUiFormat(
        "Results for “%@”",
        locale: Locale(identifier: "zh-Hant"),
        arguments: ["BBC"]
    ) == "「BBC」的搜尋結果")
    #expect(localizedUiFormat(
        "%@ • %ld tracks",
        locale: Locale(identifier: "zh-Hans"),
        arguments: ["MorningLine", 3]
    ) == "MorningLine • 3 首歌曲")
    #expect(localizedUiText(
        "香港電台第一台",
        locale: Locale(identifier: "zh-Hans")
    ) == "香港電台第一台")
}
