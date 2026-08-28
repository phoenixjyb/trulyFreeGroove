import AVFoundation
import Combine
import MediaPlayer

@MainActor
final class RadioPlayer: ObservableObject {
    @Published private(set) var currentStation: RadioStation?
    @Published private(set) var isPlaying = false
    @Published private(set) var isBuffering = false
    @Published private(set) var isActive = false
    @Published private(set) var errorMessage: String?

    private let player = AVPlayer()
    private var queue: [RadioStation] = []
    private var statusObservation: NSKeyValueObservation?
    private var itemStatusObservation: NSKeyValueObservation?
    private var remoteTargets: [(MPRemoteCommand, Any)] = []

    init() {
        configureAudioSession()
        installRemoteCommands()
        statusObservation = player.observe(\.timeControlStatus, options: [.initial, .new]) { [weak self] player, _ in
            Task { @MainActor in
                self?.isPlaying = player.timeControlStatus == .playing
                self?.isBuffering = player.timeControlStatus == .waitingToPlayAtSpecifiedRate
                self?.updateNowPlaying()
            }
        }
    }

    func play(_ station: RadioStation, queue: [RadioStation]) {
        guard SharedPolicyBridge.allowsRadioPlayback(station) else {
            errorMessage = "This station did not pass OpenGroove's shared playback policy."
            return
        }
        self.queue = queue.filter(\.isPlayable)
        isActive = true
        installRemoteCommands()
        currentStation = station
        errorMessage = nil
        activateAudioSession()
        let item = AVPlayerItem(url: station.streamURL)
        itemStatusObservation = item.observe(\.status, options: [.new]) { [weak self] item, _ in
            guard item.status == .failed else { return }
            Task { @MainActor in
                self?.errorMessage = "This station is unavailable right now. Try another station or retry."
            }
        }
        player.replaceCurrentItem(with: item)
        player.play()
        updateNowPlaying()
    }

    func togglePlayback() {
        if player.timeControlStatus == .playing {
            player.pause()
        } else {
            player.play()
        }
    }

    func retry() {
        guard let currentStation else { return }
        play(currentStation, queue: queue)
    }

    func yieldRemoteControl() {
        player.pause()
        isActive = false
        removeRemoteCommands()
    }

    @discardableResult
    func switchStation(offset: Int) -> Bool {
        guard
            let currentStation,
            let index = queue.firstIndex(where: { $0.id == currentStation.id }),
            !queue.isEmpty
        else { return false }
        let next = (index + offset + queue.count) % queue.count
        play(queue[next], queue: queue)
        return true
    }

    private func configureAudioSession() {
#if os(iOS)
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default)
        } catch {
            errorMessage = "Background audio could not be configured."
        }
#endif
    }

    private func activateAudioSession() {
#if os(iOS)
        do {
            try AVAudioSession.sharedInstance().setActive(true)
        } catch {
            errorMessage = "Audio could not start because the system audio session is unavailable."
        }
#endif
    }

    private func installRemoteCommands() {
        guard remoteTargets.isEmpty else { return }
        let commands = MPRemoteCommandCenter.shared()
        remoteTargets.append((commands.playCommand, commands.playCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.player.play() }
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
        remoteTargets.append((commands.nextTrackCommand, commands.nextTrackCommand.addTarget { [weak self] _ in
            guard let self else { return .commandFailed }
            Task { @MainActor in _ = self.switchStation(offset: 1) }
            return .success
        }))
        remoteTargets.append((commands.previousTrackCommand, commands.previousTrackCommand.addTarget { [weak self] _ in
            guard let self else { return .commandFailed }
            Task { @MainActor in _ = self.switchStation(offset: -1) }
            return .success
        }))
    }

    private func removeRemoteCommands() {
        for (command, target) in remoteTargets {
            command.removeTarget(target)
        }
        remoteTargets.removeAll()
    }

    private func updateNowPlaying() {
        guard let station = currentStation else {
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
            return
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = [
            MPMediaItemPropertyTitle: station.name,
            MPMediaItemPropertyArtist: [station.country, station.language].filter { !$0.isEmpty }.joined(separator: " • "),
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? 1 : 0,
        ]
    }
}
