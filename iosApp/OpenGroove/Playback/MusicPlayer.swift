import AVFoundation
import Combine
import MediaPlayer

@MainActor
final class MusicPlayer: ObservableObject {
    @Published private(set) var currentTrack: MusicTrack?
    @Published private(set) var isPlaying = false
    @Published private(set) var isBuffering = false
    @Published private(set) var isActive = false
    @Published private(set) var position: TimeInterval = 0
    @Published private(set) var duration: TimeInterval = 0
    @Published private(set) var errorMessage: String?

    private let player = AVPlayer()
    private var statusObservation: NSKeyValueObservation?
    private var itemStatusObservation: NSKeyValueObservation?
    private var periodicObserver: Any?
    private var endObserver: AnyCancellable?
    private var remoteTargets: [(MPRemoteCommand, Any)] = []

    init() {
        statusObservation = player.observe(\.timeControlStatus, options: [.initial, .new]) { [weak self] player, _ in
            Task { @MainActor in
                self?.isPlaying = player.timeControlStatus == .playing
                self?.isBuffering = player.timeControlStatus == .waitingToPlayAtSpecifiedRate
                self?.updateNowPlaying()
            }
        }
        periodicObserver = player.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 1, preferredTimescale: 600),
            queue: .main
        ) { [weak self] time in
            Task { @MainActor in self?.record(time.seconds) }
        }
        endObserver = NotificationCenter.default.publisher(for: AVPlayerItem.didPlayToEndTimeNotification)
            .sink { [weak self] notification in
                Task { @MainActor in
                    guard
                        let self,
                        let endedItem = notification.object as? AVPlayerItem,
                        endedItem === self.player.currentItem
                    else { return }
                    self.player.pause()
                    self.position = self.duration
                    self.updateNowPlaying()
                }
            }
    }

    func play(_ track: MusicTrack) {
        guard SharedPolicyBridge.allowsMusicPlayback(track) else {
            errorMessage = "This track is missing the HTTPS stream or license evidence required by OpenGroove."
            return
        }
        currentTrack = track
        position = 0
        duration = max(track.duration, 0)
        errorMessage = nil
        isActive = true
        activateAudioSession()
        installRemoteCommands()

        let item = AVPlayerItem(url: track.streamURL)
        itemStatusObservation = item.observe(\.status, options: [.new]) { [weak self] item, _ in
            Task { @MainActor in
                guard let self else { return }
                if item.status == .failed {
                    self.errorMessage = "This licensed source is unavailable right now."
                } else if item.status == .readyToPlay {
                    let seconds = item.duration.seconds
                    if seconds.isFinite && seconds > 0 { self.duration = seconds }
                }
            }
        }
        player.replaceCurrentItem(with: item)
        player.play()
        updateNowPlaying()
    }

    func togglePlayback() {
        if player.timeControlStatus == .playing { player.pause() }
        else { resumePlayback() }
    }

    func seek(to seconds: TimeInterval) {
        let safe = min(max(seconds, 0), max(duration, 0))
        player.seek(to: CMTime(seconds: safe, preferredTimescale: 600))
        position = safe
        updateNowPlaying()
    }

    func deactivate() {
        player.pause()
        if isActive { MPNowPlayingInfoCenter.default().nowPlayingInfo = nil }
        isActive = false
        removeRemoteCommands()
    }

    private func record(_ seconds: TimeInterval) {
        guard seconds.isFinite, seconds >= 0 else { return }
        position = seconds
        let itemDuration = player.currentItem?.duration.seconds ?? 0
        if itemDuration.isFinite && itemDuration > 0 { duration = itemDuration }
        updateNowPlaying()
    }

    private func activateAudioSession() {
#if os(iOS)
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default)
            try session.setActive(true)
        } catch {
            errorMessage = "Audio could not start because the system audio session is unavailable."
        }
#endif
    }

    private func resumePlayback() {
        activateAudioSession()
        player.play()
    }

    private func installRemoteCommands() {
        guard remoteTargets.isEmpty else { return }
        let commands = MPRemoteCommandCenter.shared()
        commands.changePlaybackPositionCommand.isEnabled = true
        remoteTargets.append((commands.playCommand, commands.playCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.resumePlayback() }
            return .success
        }))
        remoteTargets.append((commands.pauseCommand, commands.pauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.player.pause() }
            return .success
        }))
        remoteTargets.append((commands.togglePlayPauseCommand, commands.togglePlayPauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.togglePlayback() }
            return .success
        }))
        remoteTargets.append((commands.changePlaybackPositionCommand, commands.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let event = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            Task { @MainActor in self?.seek(to: event.positionTime) }
            return .success
        }))
    }

    private func removeRemoteCommands() {
        for (command, target) in remoteTargets { command.removeTarget(target) }
        remoteTargets.removeAll()
    }

    private func updateNowPlaying() {
        guard isActive, let track = currentTrack else { return }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = [
            MPMediaItemPropertyTitle: track.title,
            MPMediaItemPropertyArtist: track.artist,
            MPMediaItemPropertyAlbumTitle: track.album,
            MPMediaItemPropertyPlaybackDuration: duration,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: position,
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? 1 : 0,
        ]
    }
}
