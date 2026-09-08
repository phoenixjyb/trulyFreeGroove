import SwiftUI

struct MusicPlayerView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var player: MusicPlayer

    var body: some View {
        NavigationStack {
            ZStack {
                LinearGradient(
                    colors: [.purple.opacity(0.38), .pink.opacity(0.13), .clear],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()

                if let track = player.currentTrack {
                    ScrollView {
                        VStack(spacing: 22) {
                            MusicNowPlayingHeader(track: track)
                            MusicTransportControls()
                            MusicQueueEditor()
                        }
                        .padding(.horizontal, 24)
                        .padding(.bottom, 32)
                    }
                    .toolbar {
                        ToolbarItem(placement: .navigation) {
                            Button("Discover", systemImage: "chevron.down", action: dismiss.callAsFunction)
                        }
                    }
                }
            }
        }
        .onChange(of: player.isActive) { _, isActive in
            if !isActive { dismiss() }
        }
    }
}

private struct MusicNowPlayingHeader: View {
    @EnvironmentObject private var player: MusicPlayer
    let track: MusicTrack

    var body: some View {
        VStack(spacing: 16) {
            MusicArtwork(url: track.artworkURL, size: 220)
                .shadow(color: .purple.opacity(0.24), radius: 26, y: 14)
                .padding(.top, 28)
            VStack(spacing: 7) {
                Text(track.title)
                    .font(.title.bold())
                    .multilineTextAlignment(.center)
                Text(track.artist).foregroundStyle(.secondary)
                Label("\(track.providerName) • license verified", systemImage: "checkmark.seal")
                    .font(.caption)
                    .foregroundStyle(.purple)
            }
            VStack(spacing: 6) {
                Slider(
                    value: Binding(get: { player.position }, set: { player.seek(to: $0) }),
                    in: 0...max(player.duration, 1)
                )
                HStack {
                    Text(player.position.podcastTime)
                    Spacer()
                    Text(player.duration.podcastTime)
                }
                .font(.caption.monospacedDigit())
                .foregroundStyle(.secondary)
            }
        }
    }
}

private struct MusicTransportControls: View {
    @EnvironmentObject private var player: MusicPlayer

    var body: some View {
        VStack(spacing: 14) {
            HStack(spacing: 18) {
                Button(action: player.toggleShuffle) {
                    Image(systemName: "shuffle")
                        .foregroundStyle(player.shuffleEnabled ? Color.purple : Color.gray)
                }
                .disabled(player.queue.count < 2)
                .accessibilityLabel(player.shuffleEnabled ? "Turn shuffle off" : "Turn shuffle on")

                Button { player.skip(offset: -1) } label: {
                    Image(systemName: "backward.fill").font(.title2)
                }
                .disabled(player.queue.count < 2)
                .accessibilityLabel("Previous track")

                Button(action: player.togglePlayback) {
                    ZStack {
                        Circle().fill(.purple).frame(width: 76, height: 76)
                        if player.isBuffering {
                            ProgressView().tint(.white)
                        } else {
                            Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                                .font(.title)
                                .foregroundStyle(.white)
                        }
                    }
                }
                .accessibilityLabel("Play or pause")

                Button { player.skip(offset: 1) } label: {
                    Image(systemName: "forward.fill").font(.title2)
                }
                .disabled(player.queue.count < 2)
                .accessibilityLabel("Next track")

                Button(action: player.cycleRepeatMode) {
                    Image(systemName: player.repeatMode.systemImage)
                        .foregroundStyle(player.repeatMode == .off ? Color.gray : Color.purple)
                }
                .accessibilityLabel(player.repeatMode.accessibilityLabel)
            }

            HStack(spacing: 20) {
                if let track = player.currentTrack {
                    Link("License", destination: track.licenseURL)
                    Link("Source", destination: track.sourceURL)
                }
            }
            .font(.subheadline.bold())

            if let error = player.errorMessage {
                Label(error, systemImage: "exclamationmark.triangle")
                    .foregroundStyle(.red)
                    .font(.caption)
            }
        }
    }
}

private struct MusicQueueEditor: View {
    @EnvironmentObject private var player: MusicPlayer

    var body: some View {
        VStack(spacing: 10) {
            HStack {
                Label("Up Next", systemImage: "text.line.first.and.arrowtriangle.forward")
                    .font(.headline)
                Spacer()
                Text("\(player.queue.count)")
                    .foregroundStyle(.secondary)
            }

            LazyVStack(spacing: 8) {
                ForEach(Array(player.queue.enumerated()), id: \.offset) { index, track in
                    MusicQueueRow(index: index, track: track)
                }
            }

            Button("Clear queue", systemImage: "trash", role: .destructive) {
                player.clearQueue()
            }
            .buttonStyle(.bordered)
            .frame(maxWidth: .infinity)
        }
        .padding(.top, 4)
    }
}

private struct MusicQueueRow: View {
    @EnvironmentObject private var player: MusicPlayer
    let index: Int
    let track: MusicTrack

    var body: some View {
        HStack(spacing: 8) {
            Button { player.jump(to: index) } label: {
                HStack(spacing: 10) {
                    Group {
                        if index == player.currentIndex {
                            Image(systemName: "play.fill")
                                .foregroundStyle(.purple)
                        } else {
                            Text("\(index + 1)")
                                .font(.caption.monospacedDigit())
                                .foregroundStyle(.secondary)
                        }
                    }
                    .frame(width: 22)

                    MusicArtwork(url: track.artworkURL, size: 42)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(track.title).font(.subheadline.bold()).lineLimit(1)
                        Text(track.artist).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .buttonStyle(.plain)

            VStack(spacing: 0) {
                Button { player.moveQueueItem(from: index, to: index - 1) } label: {
                    Image(systemName: "chevron.up")
                }
                .disabled(index == player.queue.startIndex)
                .accessibilityLabel("Move up")
                Button { player.moveQueueItem(from: index, to: index + 1) } label: {
                    Image(systemName: "chevron.down")
                }
                .disabled(index == player.queue.index(before: player.queue.endIndex))
                .accessibilityLabel("Move down")
            }
            .font(.caption)

            Button(role: .destructive) {
                player.removeQueueItem(at: index)
            } label: {
                Image(systemName: "trash")
            }
            .accessibilityLabel("Remove from queue")
        }
        .padding(9)
        .background(
            index == player.currentIndex ? Color.purple.opacity(0.13) : Color.primary.opacity(0.05),
            in: RoundedRectangle(cornerRadius: 14)
        )
    }
}

struct MusicMiniPlayer: View {
    @EnvironmentObject private var player: MusicPlayer
    let track: MusicTrack
    let onOpen: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            ProgressView(value: player.position, total: max(player.duration, 1))
                .tint(.purple)
            HStack(spacing: 10) {
                Button(action: onOpen) {
                    HStack(spacing: 10) {
                        MusicArtwork(url: track.artworkURL, size: 42)
                        VStack(alignment: .leading) {
                            Text(track.title).font(.subheadline.bold()).lineLimit(1)
                            Text(miniPlayerSubtitle)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .buttonStyle(.plain)

                Button { player.skip(offset: -1) } label: {
                    Image(systemName: "backward.fill")
                }
                .disabled(player.queue.count < 2)
                .accessibilityLabel("Previous track")

                Button(action: player.togglePlayback) {
                    if player.isBuffering {
                        ProgressView()
                    } else {
                        Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                            .font(.title3)
                    }
                }
                .buttonStyle(.borderedProminent)
                .buttonBorderShape(.circle)
                .accessibilityLabel("Play or pause")

                Button { player.skip(offset: 1) } label: {
                    Image(systemName: "forward.fill")
                }
                .disabled(player.queue.count < 2)
                .accessibilityLabel("Next track")
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
        }
        .background(.ultraThinMaterial)
        .overlay(alignment: .top) { Divider() }
    }

    private var miniPlayerSubtitle: String {
        player.queue.count > 1 ? "\(track.artist) • \(player.queue.count) in queue" : track.artist
    }
}
