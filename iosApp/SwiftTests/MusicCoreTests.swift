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

@Test
func musicQueueSnapshotRoundTripRetainsPositionAndModes() throws {
    let suiteName = "MusicQueueTests.\(UUID().uuidString)"
    let defaults = try #require(UserDefaults(suiteName: suiteName))
    defer { defaults.removePersistentDomain(forName: suiteName) }
    let store = MusicQueueStore(defaults: defaults)
    store.save(MusicQueueSnapshot(
        tracks: [try musicTestTrack(id: "one"), try musicTestTrack(id: "two")],
        currentIndex: 1,
        position: 42.5,
        shuffleEnabled: true,
        repeatMode: .all
    ))

    let restored = try #require(store.load())
    #expect(restored.tracks.map(\.id) == ["one", "two"])
    #expect(restored.currentIndex == 1)
    #expect(restored.position == 42.5)
    #expect(restored.shuffleEnabled)
    #expect(restored.repeatMode == .all)
}

@Test
func musicQueueRestoreFailsClosedAndRemapsCurrentTrack() throws {
    let external = try musicTestTrack(id: "external", playbackMode: .externalOnly)
    let allowed = try musicTestTrack(id: "allowed")
    let restored = try #require(MusicQueueSnapshot(
        tracks: [external, allowed],
        currentIndex: 1,
        position: -5,
        shuffleEnabled: false,
        repeatMode: .off
    ).validated())

    #expect(restored.tracks.map(\.id) == ["allowed"])
    #expect(restored.currentIndex == 0)
    #expect(restored.position == 0)
    #expect(MusicQueueSnapshot(
        tracks: [external],
        currentIndex: 0,
        position: 0,
        shuffleEnabled: false,
        repeatMode: .off
    ).validated() == nil)
}

@Test
func musicQueueKeepsDuplicatePositionsAndBoundsSize() throws {
    let duplicate = try musicTestTrack(id: "same")
    let oversized = Array(repeating: duplicate, count: MusicQueueSnapshot.maximumTrackCount + 5)
    let restored = try #require(MusicQueueSnapshot(
        tracks: oversized,
        currentIndex: 1,
        position: 8,
        shuffleEnabled: false,
        repeatMode: .one
    ).validated())

    #expect(restored.tracks.count == MusicQueueSnapshot.maximumTrackCount)
    #expect(restored.currentIndex == 1)
}

@MainActor
@Test
func musicPlayerEditsTheQueueWithoutLosingTheCurrentTrack() throws {
    let suiteName = "MusicPlayerQueueTests.\(UUID().uuidString)"
    let defaults = try #require(UserDefaults(suiteName: suiteName))
    defer { defaults.removePersistentDomain(forName: suiteName) }
    let player = MusicPlayer(queueDefaults: defaults)
    let one = try musicTestTrack(id: "one")
    let two = try musicTestTrack(id: "two")
    let three = try musicTestTrack(id: "three")

    player.play(queue: [one, two], startingAt: 1)
    player.enqueue(three, playNext: true)
    #expect(player.queue.map(\.id) == ["one", "two", "three"])
    #expect(player.currentTrack?.id == "two")
    player.moveQueueItem(from: 2, to: 0)
    #expect(player.queue.map(\.id) == ["three", "one", "two"])
    #expect(player.currentIndex == 2)
    player.removeQueueItem(at: 2)
    #expect(player.currentTrack?.id == "one")
    player.toggleShuffle()
    player.cycleRepeatMode()
    #expect(player.shuffleEnabled)
    #expect(player.repeatMode == .all)
    player.clearQueue()
    #expect(player.queue.isEmpty)
    #expect(!player.isActive)
}

@MainActor
@Test
func musicPlayerRepeatAndShuffleTraversalMatchTheQueueContract() throws {
    let suiteName = "MusicPlayerTraversalTests.\(UUID().uuidString)"
    let defaults = try #require(UserDefaults(suiteName: suiteName))
    defer { defaults.removePersistentDomain(forName: suiteName) }
    let player = MusicPlayer(queueDefaults: defaults)
    let tracks = try ["one", "two", "three"].map { try musicTestTrack(id: $0) }

    player.play(queue: tracks, startingAt: 2)
    #expect(!player.skip(offset: 1))
    player.cycleRepeatMode()
    #expect(player.repeatMode == .all)
    #expect(player.skip(offset: 1))
    #expect(player.currentIndex == 0)

    player.toggleShuffle()
    let startingIndex = player.currentIndex
    #expect(player.skip(offset: 1))
    let secondIndex = player.currentIndex
    #expect(secondIndex != startingIndex)
    #expect(player.skip(offset: 1))
    #expect(![startingIndex, secondIndex].contains(player.currentIndex))
    #expect(player.skip(offset: -1))
    #expect(player.currentIndex == secondIndex)
    player.clearQueue()
}

private func musicTestTrack(
    id: String = "commons:1",
    playbackMode: MusicPlaybackMode = .directAuthorized
) throws -> MusicTrack {
    MusicTrack(
        id: id, title: "Track \(id)", artist: "Artist", album: "CC0", duration: 90,
        artworkURL: nil, providerName: "Wikimedia Commons",
        streamURL: try #require(URL(string: "https://upload.wikimedia.org/\(id).ogg")),
        sourceURL: try #require(URL(string: "https://commons.wikimedia.org/wiki/File:\(id).ogg")),
        licenseURL: try #require(URL(string: "https://creativecommons.org/publicdomain/zero/1.0/")),
        playbackMode: playbackMode
    )
}
