import Foundation
#if canImport(OpenGrooveShared)
import OpenGrooveShared
#endif

enum SharedPolicyBridge {
    static func allowsRadioPlayback(_ station: RadioStation) -> Bool {
#if canImport(OpenGrooveShared)
        SharedPlaybackPolicy.shared.isRadioStreamAllowed(
            id: station.id,
            name: station.name,
            streamUrl: station.streamURL.absoluteString
        )
#else
        station.isPlayable
#endif
    }

    static func allowsPodcastPlayback(_ episode: PodcastEpisode) -> Bool {
#if canImport(OpenGrooveShared)
        SharedPlaybackPolicy.shared.isPodcastStreamAllowed(
            title: episode.title,
            audioUrl: episode.audioURL.absoluteString
        )
#else
        episode.isPlayable
#endif
    }

    static func allowsMusicPlayback(_ track: MusicTrack) -> Bool {
#if canImport(OpenGrooveShared)
        SharedPlaybackPolicy.shared.isDirectTrackPlaybackAllowed(
            playbackMode: track.playbackMode == .directAuthorized ? PlaybackMode.directAuthorized : PlaybackMode.externalOnly,
            streamUrl: track.streamURL.absoluteString,
            licenseUrl: track.licenseURL.absoluteString
        )
#else
        track.isDirectPlaybackCandidate
#endif
    }
}
