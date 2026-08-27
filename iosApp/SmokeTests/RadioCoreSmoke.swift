import Foundation

@main
struct RadioCoreSmoke {
    static func main() throws {
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

        let stations = try RadioDirectory().decodeStations(payload)
        precondition(stations.count == 1)
        precondition(stations[0].name == "香港電台 Radio Hong Kong")
        precondition(stations[0].streamURL.absoluteString == "https://radio.example/live.m3u8")
        precondition(stations[0].tags == ["news", "粵語"])
        precondition(stations[0].isHLS)
        print("OpenGroove iOS radio core smoke passed")
    }
}
