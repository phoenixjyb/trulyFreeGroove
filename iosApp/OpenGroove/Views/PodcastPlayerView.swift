import SwiftUI

struct PodcastPlayerView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var player: PodcastPlayer

    var body: some View {
        NavigationStack {
            ZStack {
                LinearGradient(
                    colors: [.purple.opacity(0.34), .orange.opacity(0.13), .clear],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()

                if let episode = player.currentEpisode {
                    ScrollView {
                        VStack(spacing: 22) {
                            PodcastArtwork(url: episode.artworkURL, size: 220)
                                .shadow(color: .purple.opacity(0.22), radius: 26, y: 14)

                            VStack(spacing: 7) {
                                Text(episode.title).font(.title2.bold()).multilineTextAlignment(.center)
                                Text(episode.showTitle).foregroundStyle(.secondary)
                                Label("Streaming from publisher", systemImage: "dot.radiowaves.left.and.right")
                                    .font(.caption).foregroundStyle(.purple)
                            }

                            VStack(spacing: 6) {
                                Slider(
                                    value: Binding(
                                        get: { player.position },
                                        set: { newPosition in player.seek(to: newPosition) }
                                    ),
                                    in: 0...max(player.duration, 1)
                                )
                                HStack {
                                    Text(player.position.podcastTime)
                                    Spacer()
                                    Text(player.duration.podcastTime)
                                }
                                .font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                            }

                            HStack(spacing: 34) {
                                Button { _ = player.skip(offset: -1) } label: {
                                    Image(systemName: "backward.end.fill").font(.title2)
                                }
                                .disabled(player.queue.first?.id == episode.id)

                                Button(action: player.togglePlayback) {
                                    ZStack {
                                        Circle().fill(.purple).frame(width: 78, height: 78)
                                        if player.isBuffering {
                                            ProgressView().tint(.white)
                                        } else {
                                            Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                                                .font(.title).foregroundStyle(.white)
                                        }
                                    }
                                }

                                Button { _ = player.skip(offset: 1) } label: {
                                    Image(systemName: "forward.end.fill").font(.title2)
                                }
                                .disabled(player.queue.last?.id == episode.id)
                            }

                            HStack(spacing: 18) {
                                Menu {
                                    ForEach(PodcastPlaybackSettings.speeds, id: \.self) { speed in
                                        Button(speed == player.speed ? "✓ \(speedLabel(speed))" : speedLabel(speed)) {
                                            player.setSpeed(speed)
                                        }
                                    }
                                } label: {
                                    Label(speedLabel(player.speed), systemImage: "speedometer")
                                }

                                Menu {
                                    Button("Off") { player.setSleepTimer(minutes: nil) }
                                    ForEach(PodcastPlaybackSettings.sleepTimerMinutes, id: \.self) { minutes in
                                        Button("\(minutes) minutes") { player.setSleepTimer(minutes: minutes) }
                                    }
                                } label: {
                                    Label(player.sleepTimerEnd == nil ? "Sleep" : "Timer on", systemImage: "moon.zzz")
                                }
                            }
                            .buttonStyle(.bordered)

                            if let error = player.errorMessage {
                                Label(error, systemImage: "exclamationmark.triangle").foregroundStyle(.red)
                            }

                            if !player.queue.isEmpty {
                                VStack(alignment: .leading, spacing: 10) {
                                    Text("Queue").font(.headline)
                                    ForEach(player.queue) { queued in
                                        HStack {
                                            Button { player.playQueued(queued) } label: {
                                                HStack {
                                                    Image(systemName: queued.id == episode.id ? "speaker.wave.2.fill" : "line.3.horizontal")
                                                        .foregroundStyle(.purple)
                                                    Text(queued.title).lineLimit(2)
                                                    Spacer()
                                                }
                                                .contentShape(Rectangle())
                                            }
                                            .buttonStyle(.plain)
                                            Button {
                                                player.removeFromQueue(queued)
                                            } label: {
                                                Image(systemName: "trash").foregroundStyle(.secondary)
                                            }
                                            .buttonStyle(.plain)
                                            .accessibilityLabel("Remove from queue")
                                        }
                                        Divider()
                                    }
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(16)
                                .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 18))
                            }
                        }
                        .padding(28)
                    }
                    .toolbar {
                        ToolbarItem(placement: .navigation) {
                            Button("Podcasts", systemImage: "chevron.down", action: dismiss.callAsFunction)
                        }
                    }
                }
            }
        }
    }

    private func speedLabel(_ speed: Float) -> String {
        speed == floor(speed) ? "\(Int(speed))×" : "\(speed)×"
    }
}

struct PodcastMiniPlayer: View {
    @EnvironmentObject private var player: PodcastPlayer
    let episode: PodcastEpisode
    let onOpen: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Button(action: onOpen) {
                HStack(spacing: 10) {
                    PodcastArtwork(url: episode.artworkURL, size: 42)
                    VStack(alignment: .leading) {
                        Text(episode.title).font(.subheadline.bold()).lineLimit(1)
                        Text(episode.showTitle).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)
            Text(player.speed == 1 ? "" : "\(player.speed, specifier: "%g")×")
                .font(.caption.bold()).foregroundStyle(.purple)
            Button(action: player.togglePlayback) {
                if player.isBuffering {
                    ProgressView()
                } else {
                    Image(systemName: player.isPlaying ? "pause.fill" : "play.fill").font(.title3)
                }
            }
            .buttonStyle(.borderedProminent)
            .buttonBorderShape(.circle)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 9)
        .background(.ultraThinMaterial)
        .overlay(alignment: .top) { Divider() }
    }
}
