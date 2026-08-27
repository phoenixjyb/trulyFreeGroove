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
