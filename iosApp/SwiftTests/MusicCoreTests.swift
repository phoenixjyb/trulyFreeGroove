import Foundation
import Testing
#if canImport(OpenGrooveIOSCore)
@testable import OpenGrooveIOSCore
#elseif canImport(OpenGroove)
@testable import OpenGroove
#endif

@Test
func wikimediaCatalogKeepsOnlyLicenseExplicitHTTPSAudio() throws {
    let payload = """
    {
      "query": {
        "pages": {
          "101": {
            "pageid": 101,
            "title": "File:Moonlight Sonata.ogg",
            "canonicalurl": "https://commons.wikimedia.org/wiki/File:Moonlight_Sonata.ogg",
            "imageinfo": [{
              "url": "https://upload.wikimedia.org/music/moonlight.ogg",
              "descriptionurl": "https://commons.wikimedia.org/wiki/File:Moonlight_Sonata.ogg",
              "mime": "application/ogg",
              "extmetadata": {
                "ObjectName": {"value": "Moonlight Sonata.ogg"},
                "Artist": {"value": "<b>Public Domain Performer</b>"},
                "LicenseShortName": {"value": "Public domain"},
                "LicenseUrl": {"value": "https://creativecommons.org/publicdomain/mark/1.0/"}
              }
            }]
          },
          "102": {
            "pageid": 102,
            "title": "File:Unsafe.mp3",
            "canonicalurl": "https://commons.wikimedia.org/wiki/File:Unsafe.mp3",
            "imageinfo": [{
              "url": "http://upload.wikimedia.org/unsafe.mp3",
              "mime": "audio/mpeg",
              "extmetadata": {"LicenseShortName": {"value": "Unknown"}}
            }]
          }
        }
      }
    }
    """.data(using: .utf8)!

    let tracks = try WikimediaMusicCatalog().decode(payload)
    let track = try #require(tracks.first)
    #expect(tracks.count == 1)
    #expect(track.title == "Moonlight Sonata")
    #expect(track.artist == "Public Domain Performer")
    #expect(track.album == "Public domain")
    #expect(track.providerName == "Wikimedia Commons")
    #expect(track.isDirectPlaybackCandidate)
    #expect(SharedPolicyBridge.allowsMusicPlayback(track))
}

@Test
func musicWithoutLicenseEvidenceFailsClosed() throws {
    let payload = """
    {"query":{"pages":{"7":{"pageid":7,"title":"File:No License.mp3",
    "canonicalurl":"https://commons.wikimedia.org/wiki/File:No_License.mp3",
    "imageinfo":[{"url":"https://upload.wikimedia.org/no-license.mp3","mime":"audio/mpeg","extmetadata":{}}]}}}}
    """.data(using: .utf8)!
    #expect(try WikimediaMusicCatalog().decode(payload).isEmpty)
}

@Test
func jamendoAdapterRequiresLicenseAndHTTPSAudio() throws {
    let payload = """
    {
      "headers": {"status": "success"},
      "results": [
        {
          "id": "55", "name": "Open Song", "artist_name": "CC Artist",
          "album_name": "Open Album", "duration": 215,
          "image": "https://usercontent.jamendo.com/cover.jpg", "album_image": "",
          "audio": "https://prod-1.storage.jamendo.com/song.mp3",
          "shareurl": "https://www.jamendo.com/track/55", "shorturl": "",
          "license_ccurl": "http://creativecommons.org/licenses/by/4.0/"
        },
        {
          "id": "56", "name": "Unsafe", "artist_name": "Unknown",
          "album_name": "", "duration": 0, "image": "", "album_image": "",
          "audio": "http://insecure.example/song.mp3",
          "shareurl": "https://www.jamendo.com/track/56", "shorturl": "",
          "license_ccurl": "https://creativecommons.org/licenses/by/4.0/"
        }
      ]
    }
    """.data(using: .utf8)!
    let catalog = try #require(JamendoMusicCatalog(clientID: "public-client-id"))
    let track = try #require(catalog.decode(payload).first)
    #expect(try catalog.decode(payload).count == 1)
    #expect(track.providerName == "Jamendo")
    #expect(track.duration == 215)
    #expect(track.licenseURL.absoluteString.hasPrefix("https://"))
    #expect(SharedPolicyBridge.allowsMusicPlayback(track))
}

@Test
func officialMusicHandoffsRemainHTTPSAndEncodeSearch() throws {
    for provider in OfficialMusicProvider.allCases {
        let url = try #require(provider.searchURL(query: "周杰伦 & jazz"))
        #expect(url.scheme == "https")
        #expect(!url.absoluteString.contains(" "))
    }
    let netease = try #require(OfficialMusicProvider.netease.searchURL(query: "A&B"))
    #expect(netease.absoluteString.contains("A%26B"))
    let spotify = try #require(OfficialMusicProvider.spotify.searchURL(query: "粤语/rock?#"))
    #expect(spotify.absoluteString.contains("%2F"))
    #expect(spotify.absoluteString.contains("%3F"))
    #expect(spotify.absoluteString.contains("%23"))
}

@MainActor
@Test
func musicPlaylistsPersistAndRejectDuplicateNames() throws {
    let suiteName = "MusicCoreTests.\(UUID().uuidString)"
    let defaults = try #require(UserDefaults(suiteName: suiteName))
    defer { defaults.removePersistentDomain(forName: suiteName) }
    let track = try musicTestTrack()

    let first = MusicLibraryStore(defaults: defaults)
    #expect(first.createPlaylist(named: "Evening"))
    #expect(!first.createPlaylist(named: "evening"))
    let playlist = try #require(first.playlists.first)
    first.add(track, to: playlist.id)
    first.add(track, to: playlist.id)

    let restored = MusicLibraryStore(defaults: defaults)
    #expect(restored.playlists.count == 1)
    #expect(restored.playlists.first?.tracks == [track])
}

private func musicTestTrack() throws -> MusicTrack {
    MusicTrack(
        id: "commons:1", title: "Track", artist: "Artist", album: "CC0", duration: 90,
        artworkURL: nil, providerName: "Wikimedia Commons",
        streamURL: try #require(URL(string: "https://upload.wikimedia.org/track.ogg")),
        sourceURL: try #require(URL(string: "https://commons.wikimedia.org/wiki/File:Track.ogg")),
        licenseURL: try #require(URL(string: "https://creativecommons.org/publicdomain/zero/1.0/")),
        playbackMode: .directAuthorized
    )
}
