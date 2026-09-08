import Combine
import Foundation

enum UiLanguage: String, CaseIterable, Identifiable, Sendable {
    case english = "en"
    case simplifiedChinese = "zh-Hans"
    case traditionalChinese = "zh-Hant"

    var id: Self { self }

    var compactLabel: String {
        switch self {
        case .english: "EN"
        case .simplifiedChinese: "简中"
        case .traditionalChinese: "繁中"
        }
    }

    var locale: Locale { Locale(identifier: rawValue) }

    static func from(locale: Locale) -> UiLanguage {
        let components = Locale.Components(identifier: locale.identifier)
        guard components.languageComponents.languageCode?.identifier == "zh" else { return .english }
        if components.languageComponents.script?.identifier.caseInsensitiveCompare("Hant") == .orderedSame {
            return .traditionalChinese
        }
        let traditionalRegions = Set(["HK", "MO", "TW"])
        if let region = components.languageComponents.region?.identifier.uppercased(),
           traditionalRegions.contains(region) {
            return .traditionalChinese
        }
        return .simplifiedChinese
    }
}

@MainActor
final class UiLanguageStore: ObservableObject {
    static let storageKey = "open_groove_ui_language"

    @Published private(set) var selection: UiLanguage

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard, deviceLocale: Locale = .autoupdatingCurrent) {
        self.defaults = defaults
        selection = defaults.string(forKey: Self.storageKey).flatMap(UiLanguage.init(rawValue:))
            ?? UiLanguage.from(locale: deviceLocale)
    }

    func select(_ language: UiLanguage) {
        guard language != selection else { return }
        selection = language
        defaults.set(language.rawValue, forKey: Self.storageKey)
    }
}

private final class UiLocalizationBundleToken: NSObject {}

func localizedUiText(
    _ key: String,
    locale: Locale,
    bundle: Bundle = Bundle(for: UiLocalizationBundleToken.self)
) -> String {
    let language = UiLanguage.from(locale: locale)
    guard language != .english,
          let path = bundle.path(forResource: language.rawValue, ofType: "lproj"),
          let localizedBundle = Bundle(path: path)
    else { return key }
    return localizedBundle.localizedString(forKey: key, value: key, table: nil)
}

func localizedUiFormat(
    _ key: String,
    locale: Locale,
    arguments: [CVarArg],
    bundle: Bundle = Bundle(for: UiLocalizationBundleToken.self)
) -> String {
    String(
        format: localizedUiText(key, locale: locale, bundle: bundle),
        locale: locale,
        arguments: arguments
    )
}
