import AVFoundation
import Combine
import MediaPlayer

@MainActor
final class PodcastPlayer: ObservableObject {
    @Published private(set) var currentEpisode: PodcastEpisode?
    @Published private(set) var queue: [PodcastEpisode] = []
    @Published private(set) var isPlaying = false
    @Published private(set) var isBuffering = false
    @Published private(set) var isActive = false
    @Published private(set) var position: TimeInterval = 0
    @Published private(set) var duration: TimeInterval = 0
    @Published private(set) var speed: Float = 1
    @Published private(set) var sleepTimerEnd: Date?
    @Published private(set) var errorMessage: String?

    var progressHandler: ((PodcastEpisode, TimeInterval, TimeInterval) -> Void)?

    private let player = AVPlayer()
    private var currentIndex = 0
    private var statusObservation: NSKeyValueObservation?
    private var itemStatusObservation: NSKeyValueObservation?
    private var periodicObserver: Any?
    private var endObserver: AnyCancellable?
    private var sleepTimer: Timer?
    private var remoteTargets: [(MPRemoteCommand, Any)] = []
    private var lastCheckpointEpisodeID: String?
    private var lastCheckpointPosition: TimeInterval = 0
    private var lastCheckpointDuration: TimeInterval = 0

    private static let checkpointInterval: TimeInterval = 15

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
            Task { @MainActor in self?.recordProgress(time.seconds) }
        }
        endObserver = NotificationCenter.default.publisher(for: AVPlayerItem.didPlayToEndTimeNotification)
            .sink { [weak self] notification in
                Task { @MainActor in
                    guard
                        let self,
                        let endedItem = notification.object as? AVPlayerItem,
                        endedItem === self.player.currentItem
                    else { return }
                    self.position = self.duration
                    if !self.skip(offset: 1) {
                        self.emitProgress(force: true)
                        self.player.pause()
                        self.updateNowPlaying()
                    }
                }
            }
    }

    func play(_ episode: PodcastEpisode, context: [PodcastEpisode]) {
        guard SharedPolicyBridge.allowsPodcastPlayback(episode) else {
            errorMessage = "This episode did not pass OpenGroove's shared playback policy."
            return
        }
        var playable = context.filter(\.isPlayable)
        if !playable.contains(where: { $0.id == episode.id }) { playable.insert(episode, at: 0) }
        queue = playable.reduce(into: []) { result, item in
            if !result.contains(where: { $0.id == item.id }) { result.append(item) }
        }
        currentIndex = queue.firstIndex(where: { $0.id == episode.id }) ?? 0
        isActive = true
        installRemoteCommands()
        start(episode)
    }

    func togglePlayback() {
        if player.timeControlStatus == .playing {
            pauseAndCheckpoint()
        } else {
            resumePlayback()
        }
    }

    func seek(to seconds: TimeInterval) {
        let upperBound = duration > 0 ? duration : max(seconds, 0)
        let safeSeconds = min(max(seconds, 0), upperBound)
        player.seek(to: CMTime(seconds: safeSeconds, preferredTimescale: 600))
        position = safeSeconds
        updateNowPlaying()
    }

    func setSpeed(_ newSpeed: Float) {
        guard PodcastPlaybackSettings.speeds.contains(newSpeed) else { return }
        speed = newSpeed
        player.defaultRate = newSpeed
        if isPlaying { player.rate = newSpeed }
        updateNowPlaying()
    }

    func setSleepTimer(minutes: Int?) {
        sleepTimer?.invalidate()
        sleepTimer = nil
        guard let minutes, PodcastPlaybackSettings.sleepTimerMinutes.contains(minutes) else {
            sleepTimerEnd = nil
            return
        }
        sleepTimerEnd = Date().addingTimeInterval(TimeInterval(minutes * 60))
        sleepTimer = Timer.scheduledTimer(withTimeInterval: TimeInterval(minutes * 60), repeats: false) { [weak self] _ in
            Task { @MainActor in
                self?.pauseAndCheckpoint()
                self?.sleepTimerEnd = nil
                self?.sleepTimer = nil
            }
        }
    }

    func addToQueue(_ episode: PodcastEpisode) {
        guard episode.isPlayable, !queue.contains(where: { $0.id == episode.id }) else { return }
        queue.append(episode)
    }

    func removeFromQueue(at offsets: IndexSet) {
        guard let currentEpisode else {
            queue.remove(atOffsets: offsets)
            return
        }
        let removedCurrent = offsets.contains { queue[$0].id == currentEpisode.id }
        queue.remove(atOffsets: offsets)
        if removedCurrent {
            if queue.isEmpty {
                pauseAndCheckpoint()
                if isActive { MPNowPlayingInfoCenter.default().nowPlayingInfo = nil }
                self.currentEpisode = nil
                isActive = false
                removeRemoteCommands()
            } else {
                currentIndex = min(currentIndex, queue.count - 1)
                start(queue[currentIndex])
            }
        } else {
            currentIndex = queue.firstIndex(where: { $0.id == currentEpisode.id }) ?? 0
        }
    }

    func removeFromQueue(_ episode: PodcastEpisode) {
        guard let index = queue.firstIndex(where: { $0.id == episode.id }) else { return }
        removeFromQueue(at: IndexSet(integer: index))
    }

    @discardableResult
    func skip(offset: Int) -> Bool {
        let nextIndex = currentIndex + offset
        guard queue.indices.contains(nextIndex) else { return false }
        currentIndex = nextIndex
        start(queue[nextIndex])
        return true
    }

    func playQueued(_ episode: PodcastEpisode) {
        guard let index = queue.firstIndex(where: { $0.id == episode.id }) else { return }
        currentIndex = index
        isActive = true
        installRemoteCommands()
        start(episode)
    }

    func deactivateForRadio() {
        pauseAndCheckpoint()
        if isActive { MPNowPlayingInfoCenter.default().nowPlayingInfo = nil }
        isActive = false
        removeRemoteCommands()
    }

    func checkpointProgress() {
        emitProgress(force: true)
    }

    private func start(_ episode: PodcastEpisode) {
        emitProgress(force: true)
        currentEpisode = episode
        position = max(episode.position, 0)
        duration = max(episode.duration, 0)
        lastCheckpointEpisodeID = episode.id
        lastCheckpointPosition = position
        lastCheckpointDuration = duration
        errorMessage = nil
        activateAudioSession()

        let item = AVPlayerItem(url: episode.audioURL)
        itemStatusObservation = item.observe(\.status, options: [.new]) { [weak self] item, _ in
            Task { @MainActor in
                guard let self else { return }
                if item.status == .failed {
                    self.errorMessage = "This publisher audio is unavailable right now."
                } else if item.status == .readyToPlay {
                    let itemDuration = item.duration.seconds
                    if itemDuration.isFinite && itemDuration > 0 { self.duration = itemDuration }
                }
            }
        }
        player.replaceCurrentItem(with: item)
        player.defaultRate = speed
        if position > 0 { player.seek(to: CMTime(seconds: position, preferredTimescale: 600)) }
        player.playImmediately(atRate: speed)
        updateNowPlaying()
    }

    private func recordProgress(_ seconds: TimeInterval) {
        guard seconds.isFinite, seconds >= 0, let episode = currentEpisode else { return }
        position = seconds
        let itemDuration = player.currentItem?.duration.seconds ?? 0
        if itemDuration.isFinite && itemDuration > 0 { duration = itemDuration }
        if let sleepTimerEnd, sleepTimerEnd <= Date() {
            pauseAndCheckpoint()
            self.sleepTimerEnd = nil
            sleepTimer?.invalidate()
            sleepTimer = nil
            return
        }
        if episode.id != lastCheckpointEpisodeID || abs(position - lastCheckpointPosition) >= Self.checkpointInterval {
            emitProgress(force: false)
        }
        updateNowPlaying()
    }

    private func emitProgress(force: Bool) {
        guard let episode = currentEpisode else { return }
        let changedEpisode = episode.id != lastCheckpointEpisodeID
        let movedEnough = abs(position - lastCheckpointPosition) >= Self.checkpointInterval
        let changedSinceCheckpoint = position != lastCheckpointPosition || duration != lastCheckpointDuration
        guard changedEpisode || movedEnough || (force && changedSinceCheckpoint) else { return }
        progressHandler?(episode, position, duration)
        lastCheckpointEpisodeID = episode.id
        lastCheckpointPosition = position
        lastCheckpointDuration = duration
    }

    private func pauseAndCheckpoint() {
        player.pause()
        emitProgress(force: true)
    }

    private func resumePlayback() {
        activateAudioSession()
        player.playImmediately(atRate: speed)
    }

    private func activateAudioSession() {
#if os(iOS)
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .spokenAudio)
            try session.setActive(true)
        } catch {
            errorMessage = "Audio could not start because the system audio session is unavailable."
        }
#endif
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
            Task { @MainActor in self?.pauseAndCheckpoint() }
            return .success
        }))
        remoteTargets.append((commands.togglePlayPauseCommand, commands.togglePlayPauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.togglePlayback() }
            return .success
        }))
        remoteTargets.append((commands.nextTrackCommand, commands.nextTrackCommand.addTarget { [weak self] _ in
            Task { @MainActor in _ = self?.skip(offset: 1) }
            return .success
        }))
        remoteTargets.append((commands.previousTrackCommand, commands.previousTrackCommand.addTarget { [weak self] _ in
            Task { @MainActor in _ = self?.skip(offset: -1) }
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
        guard isActive, let episode = currentEpisode else { return }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = [
            MPMediaItemPropertyTitle: episode.title,
            MPMediaItemPropertyPodcastTitle: episode.showTitle,
            MPMediaItemPropertyArtist: episode.author,
            MPMediaItemPropertyPlaybackDuration: duration,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: position,
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? speed : 0,
            MPNowPlayingInfoPropertyDefaultPlaybackRate: speed,
        ]
    }
}
