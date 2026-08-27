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
}
