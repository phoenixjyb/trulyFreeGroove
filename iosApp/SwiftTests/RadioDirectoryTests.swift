import Foundation
import Testing
#if canImport(OpenGrooveIOSCore)
@testable import OpenGrooveIOSCore
#elseif canImport(OpenGroove)
@testable import OpenGroove
#endif

@Test
func directoryKeepsResolvedWorkingStreamsAndMultilingualMetadata() throws {
    let payload = """
    [{
      "stationuuid": "station-one",
      "name": "香港電台 Radio Hong Kong",
      "url": "http://legacy.example/live",
      "url_resolved": "https://radio.example/live.m3u8",
      "homepage": "https://radio.example",
      "favicon": "https://radio.example/icon.png",
      "country": "Hong Kong",
      "countrycode": "HK",
      "language": "Cantonese",
      "tags": "news,粵語",
      "codec": "AAC",
      "bitrate": 128,
      "votes": 42,
      "lastcheckok": 1,
      "lastchecktime_iso8601": "2026-08-28T00:00:00Z",
      "hls": 1
    }]
    """.data(using: .utf8)!

    let station = try #require(RadioDirectory().decodeStations(payload).first)
    #expect(station.name == "香港電台 Radio Hong Kong")
    #expect(station.streamURL.absoluteString == "https://radio.example/live.m3u8")
    #expect(station.tags == ["news", "粵語"])
    #expect(station.isHLS)
}

@Test
func brokenAndUnsupportedStationsFailClosed() throws {
    let payload = """
    [{
      "stationuuid": "broken",
      "name": "Broken",
      "url": "file:///private/audio.mp3",
      "url_resolved": "file:///private/audio.mp3",
      "homepage": "", "favicon": "", "country": "", "countrycode": "",
      "language": "", "tags": "", "codec": "", "bitrate": 0, "votes": 0,
      "lastcheckok": 0, "lastchecktime_iso8601": "", "hls": 0
    }]
    """.data(using: .utf8)!

    #expect(try RadioDirectory().decodeStations(payload).isEmpty)
}

@Test
func chineseDirectoryFiltersCombineCountryAndLanguage() {
    let query = RadioDirectory().searchQueryItems(
        name: " 香港電台 ",
        countryCode: "hk",
        tag: nil,
        language: "Cantonese",
        offset: -4
    )
    let parameters = Dictionary(uniqueKeysWithValues: query.compactMap { item in
        item.value.map { (item.name, $0) }
    })

    #expect(parameters["name"] == "香港電台")
    #expect(parameters["countrycode"] == "HK")
    #expect(parameters["language"] == "cantonese")
    #expect(parameters["offset"] == "0")
    #expect(parameters["hidebroken"] == "true")
}

@Test
func chineseRadioQuickFiltersAndTaiwanLabelMatchAndroid() throws {
    let hongKong = try #require(chineseRadioQuickFilters.first(where: { $0.id == "hong-kong-cantonese" }))
    #expect(hongKong.countryCode == "HK")
    #expect(hongKong.language?.directoryValue == "cantonese")

    let taiwan = RadioCountry(name: "Taiwan, Republic Of China", code: "TW", stationCount: 120)
    #expect(taiwan.displayName == "台湾省")
    #expect(taiwan.code == "TW")
    #expect(chineseRadioQuickFilters.first(where: { $0.countryCode == "TW" })?.label == "台湾省")
}

@MainActor
@Test
func recentStationsPersistDeduplicateAndCapAtTwenty() throws {
    let suiteName = "RadioDirectoryTests.\(UUID().uuidString)"
    let defaults = try #require(UserDefaults(suiteName: suiteName))
    defer { defaults.removePersistentDomain(forName: suiteName) }
    let store = RecentStationStore(defaults: defaults)
    for index in 0..<22 { store.record(try radioTestStation(index)) }
    store.record(try radioTestStation(10))

    let restored = RecentStationStore(defaults: defaults)
    #expect(restored.stations.count == 20)
    #expect(restored.stations.first?.id == "station-10")
    #expect(Set(restored.stations.map(\.id)).count == 20)
}

private func radioTestStation(_ index: Int) throws -> RadioStation {
    RadioStation(
        id: "station-\(index)", name: "Station \(index)",
        streamURL: try #require(URL(string: "https://radio.example/\(index).m3u8")),
        homepageURL: nil, faviconURL: nil, country: "", countryCode: "", language: "",
        tags: [], codec: "AAC", bitrate: 128, votes: 0, isOnline: true,
        lastCheckedAt: "", isHLS: true
    )
}
