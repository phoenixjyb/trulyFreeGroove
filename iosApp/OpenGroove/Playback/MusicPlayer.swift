import AVFoundation
import Combine
import MediaPlayer

@MainActor
final class MusicPlayer: ObservableObject {
    @Published private(set) var currentTrack: MusicTrack?
    @Published private(set) var queue: [MusicTrack] = []
    @Published private(set) var currentIndex = -1
    @Published private(set) var shuffleEnabled = false
    @Published private(set) var repeatMode: MusicRepeatMode = .off
    @Published private(set) var isPlaying = false
    @Published private(set) var isBuffering = false
    @Published private(set) var isActive = false
    @Published private(set) var position: TimeInterval = 0
    @Published private(set) var duration: TimeInterval = 0
    @Published private(set) var errorMessage: String?

    private static let checkpointInterval: TimeInterval = 15

    private let player = AVPlayer()
    private let queueStore: MusicQueueStore
    private var statusObservation: NSKeyValueObservation?
    private var itemStatusObservation: NSKeyValueObservation?
    private var periodicObserver: Any?
    private var endObserver: AnyCancellable?
    private var remoteTargets: [(MPRemoteCommand, Any)] = []
    private var lastCheckpointPosition: TimeInterval = 0
    private var shuffleHistory: [Int] = []
    private var shuffleCursor = -1

    init(queueDefaults: UserDefaults = .standard) {
        queueStore = MusicQueueStore(defaults: queueDefaults)
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
                    self.handlePlaybackEnded()
                }
            }
        restoreQueue()
    }

    func play(_ track: MusicTrack) {
        play(queue: [track], startingAt: 0)
    }

    func play(queue requestedTracks: [MusicTrack], startingAt requestedIndex: Int = 0) {
        guard requestedTracks.indices.contains(requestedIndex) else { return }
        let requestedTrack = requestedTracks[requestedIndex]
        guard SharedPolicyBridge.allowsMusicPlayback(requestedTrack) else {
            errorMessage = "This track is missing the HTTPS stream or license evidence required by OpenGroove."
            return
        }

        let approved = requestedTracks.prefix(MusicQueueSnapshot.maximumTrackCount).enumerated()
            .filter { SharedPolicyBridge.allowsMusicPlayback($0.element) }
        guard let startIndex = approved.firstIndex(where: { $0.offset == requestedIndex }) else { return }

        queue = approved.map(\.element)
        currentIndex = startIndex
        shuffleEnabled = false
        repeatMode = .off
        resetShuffleHistory()
        startCurrent(position: 0, autoplay: true)
        persistQueue()
    }

    func enqueue(_ track: MusicTrack, playNext: Bool) {
        guard SharedPolicyBridge.allowsMusicPlayback(track) else {
            errorMessage = "This track is missing the HTTPS stream or license evidence required by OpenGroove."
            return
        }
        guard !queue.isEmpty, queue.indices.contains(currentIndex) else {
            play(track)
            return
        }
        guard queue.count < MusicQueueSnapshot.maximumTrackCount else {
            errorMessage = "The music queue is full. Remove a track before adding another."
            return
        }

        let insertionIndex = playNext ? currentIndex + 1 : queue.endIndex
        queue.insert(track, at: min(max(insertionIndex, 0), queue.endIndex))
        resetShuffleHistory()
        updateRemoteCommandAvailability()
        persistQueue()
    }

    func jump(to index: Int) {
        guard queue.indices.contains(index) else { return }
        currentIndex = index
        resetShuffleHistory()
        startCurrent(position: 0, autoplay: true)
        persistQueue()
    }

    func moveQueueItem(from source: Int, to destination: Int) {
        guard queue.indices.contains(source), queue.indices.contains(destination), source != destination else { return }
        let moved = queue.remove(at: source)
        queue.insert(moved, at: destination)

        if currentIndex == source {
            currentIndex = destination
        } else if source < currentIndex, destination >= currentIndex {
            currentIndex -= 1
        } else if source > currentIndex, destination <= currentIndex {
            currentIndex += 1
        }
        resetShuffleHistory()
        persistQueue()
    }

    func removeQueueItem(at index: Int) {
        guard queue.indices.contains(index) else { return }
        let wasCurrent = index == currentIndex
        let wasPlaying = isPlaying
        queue.remove(at: index)

        guard !queue.isEmpty else {
            clearQueue()
            return
        }
        if index < currentIndex {
            currentIndex -= 1
        } else if wasCurrent {
            currentIndex = min(index, queue.count - 1)
            startCurrent(position: 0, autoplay: wasPlaying)
        }
        resetShuffleHistory()
        updateRemoteCommandAvailability()
        persistQueue()
    }

    func clearQueue() {
        player.pause()
        player.replaceCurrentItem(with: nil)
        queue = []
        currentIndex = -1
        currentTrack = nil
        position = 0
        duration = 0
        isActive = false
        errorMessage = nil
        resetShuffleHistory()
        queueStore.clear()
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        removeRemoteCommands()
    }

    func toggleShuffle() {
        guard queue.count > 1 else { return }
        shuffleEnabled.toggle()
        resetShuffleHistory()
        persistQueue()
    }

    func cycleRepeatMode() {
        repeatMode = repeatMode.next
        persistQueue()
        updateNowPlaying()
    }

    @discardableResult
    func skip(offset: Int) -> Bool {
        guard offset != 0, let nextIndex = queueIndex(offset: offset) else { return false }
        currentIndex = nextIndex
        startCurrent(position: 0, autoplay: true)
        persistQueue()
        return true
    }

    func togglePlayback() {
        if player.timeControlStatus == .playing {
            player.pause()
            persistQueue()
        } else {
            resumePlayback()
        }
    }

    func seek(to seconds: TimeInterval) {
        let safe = min(max(seconds, 0), max(duration, 0))
        player.seek(to: CMTime(seconds: safe, preferredTimescale: 600))
        position = safe
        persistQueue()
        updateNowPlaying()
    }

    func checkpointQueue() {
        persistQueue()
    }

    func deactivate() {
        clearQueue()
    }

    private func startCurrent(position requestedPosition: TimeInterval, autoplay: Bool) {
        guard queue.indices.contains(currentIndex) else { return }
        let track = queue[currentIndex]
        currentTrack = track
        position = max(requestedPosition, 0)
        duration = max(track.duration, 0)
        lastCheckpointPosition = position
        errorMessage = nil
        isActive = true
        installRemoteCommands()
        updateRemoteCommandAvailability()
        if autoplay { activateAudioSession() }

        let item = AVPlayerItem(url: track.streamURL)
        itemStatusObservation = item.observe(\.status, options: [.new]) { [weak self] item, _ in
            Task { @MainActor in
                guard let self, item === self.player.currentItem else { return }
                if item.status == .failed {
                    self.errorMessage = "This licensed source is unavailable right now."
                } else if item.status == .readyToPlay {
                    let seconds = item.duration.seconds
                    if seconds.isFinite && seconds > 0 { self.duration = seconds }
                }
            }
        }
        player.replaceCurrentItem(with: item)
        if position > 0 {
            player.seek(to: CMTime(seconds: position, preferredTimescale: 600))
        }
        if autoplay { player.play() } else { player.pause() }
        updateNowPlaying()
    }

    private func record(_ seconds: TimeInterval) {
        guard seconds.isFinite, seconds >= 0 else { return }
        position = seconds
        let itemDuration = player.currentItem?.duration.seconds ?? 0
        if itemDuration.isFinite && itemDuration > 0 { duration = itemDuration }
        if abs(position - lastCheckpointPosition) >= Self.checkpointInterval {
            persistQueue()
        }
        updateNowPlaying()
    }

    private func handlePlaybackEnded() {
        if repeatMode == .one || (repeatMode == .all && queue.count == 1) {
            startCurrent(position: 0, autoplay: true)
        } else if let nextIndex = queueIndex(offset: 1, allowRepeatAll: true) {
            currentIndex = nextIndex
            startCurrent(position: 0, autoplay: true)
        } else {
            player.pause()
            position = duration
            persistQueue()
            updateNowPlaying()
        }
    }

    private func queueIndex(offset: Int, allowRepeatAll: Bool = true) -> Int? {
        guard queue.indices.contains(currentIndex), queue.count > 1 else { return nil }
        if shuffleEnabled {
            if offset < 0 {
                guard shuffleCursor > 0 else { return nil }
                shuffleCursor -= 1
                return shuffleHistory[shuffleCursor]
            }
            if shuffleCursor + 1 < shuffleHistory.count {
                shuffleCursor += 1
                return shuffleHistory[shuffleCursor]
            }
            var candidates = queue.indices.filter { !shuffleHistory.contains($0) }
            if candidates.isEmpty, allowRepeatAll, repeatMode == .all {
                shuffleHistory = [currentIndex]
                shuffleCursor = 0
                candidates = queue.indices.filter { $0 != currentIndex }
            }
            guard let next = candidates.randomElement() else { return nil }
            shuffleHistory = Array(shuffleHistory.prefix(shuffleCursor + 1)) + [next]
            shuffleCursor += 1
            return next
        }

        let requested = currentIndex + offset
        if queue.indices.contains(requested) { return requested }
        guard allowRepeatAll, repeatMode == .all else { return nil }
        return requested < queue.startIndex ? queue.index(before: queue.endIndex) : queue.startIndex
    }

    private func resetShuffleHistory() {
        if queue.indices.contains(currentIndex) {
            shuffleHistory = [currentIndex]
            shuffleCursor = 0
        } else {
            shuffleHistory = []
            shuffleCursor = -1
        }
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
        guard currentTrack != nil else { return }
        activateAudioSession()
        player.play()
    }

    private func restoreQueue() {
        guard let snapshot = queueStore.load() else { return }
        let approved = snapshot.tracks.enumerated().filter { SharedPolicyBridge.allowsMusicPlayback($0.element) }
        guard !approved.isEmpty else {
            queueStore.clear()
            return
        }
        queue = approved.map(\.element)
        currentIndex = approved.firstIndex(where: { $0.offset == snapshot.currentIndex })
            ?? min(snapshot.currentIndex, queue.count - 1)
        shuffleEnabled = snapshot.shuffleEnabled
        repeatMode = snapshot.repeatMode
        resetShuffleHistory()
        startCurrent(position: snapshot.position, autoplay: false)
    }

    private func persistQueue() {
        guard queue.indices.contains(currentIndex) else {
            queueStore.clear()
            return
        }
        queueStore.save(MusicQueueSnapshot(
            tracks: queue,
            currentIndex: currentIndex,
            position: position,
            shuffleEnabled: shuffleEnabled,
            repeatMode: repeatMode
        ))
        lastCheckpointPosition = position
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
            Task { @MainActor in
                self?.player.pause()
                self?.persistQueue()
            }
            return .success
        }))
        remoteTargets.append((commands.togglePlayPauseCommand, commands.togglePlayPauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.togglePlayback() }
            return .success
        }))
        remoteTargets.append((commands.previousTrackCommand, commands.previousTrackCommand.addTarget { [weak self] _ in
            Task { @MainActor in _ = self?.skip(offset: -1) }
            return .success
        }))
        remoteTargets.append((commands.nextTrackCommand, commands.nextTrackCommand.addTarget { [weak self] _ in
            Task { @MainActor in _ = self?.skip(offset: 1) }
            return .success
        }))
        remoteTargets.append((commands.changePlaybackPositionCommand, commands.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let event = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            Task { @MainActor in self?.seek(to: event.positionTime) }
            return .success
        }))
    }

    private func updateRemoteCommandAvailability() {
        let commands = MPRemoteCommandCenter.shared()
        commands.previousTrackCommand.isEnabled = queue.count > 1
        commands.nextTrackCommand.isEnabled = queue.count > 1
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
            MPNowPlayingInfoPropertyExternalContentIdentifier: track.id,
            MPNowPlayingInfoPropertyPlaybackQueueIndex: currentIndex,
            MPNowPlayingInfoPropertyPlaybackQueueCount: queue.count,
        ]
    }
}
