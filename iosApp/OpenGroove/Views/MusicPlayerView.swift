import SwiftUI

struct MusicPlayerView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var player: MusicPlayer

    var body: some View {
        NavigationStack {
            ZStack {
                LinearGradient(colors: [.purple.opacity(0.38), .pink.opacity(0.13), .clear], startPoint: .topLeading, endPoint: .bottomTrailing)
                    .ignoresSafeArea()
                if let track = player.currentTrack {
                    VStack(spacing: 24) {
                        Spacer()
                        MusicArtwork(url: track.artworkURL, size: 220)
                            .shadow(color: .purple.opacity(0.24), radius: 26, y: 14)
                        VStack(spacing: 7) {
                            Text(track.title).font(.title.bold()).multilineTextAlignment(.center)
                            Text(track.artist).foregroundStyle(.secondary)
                            Label("\(track.providerName) • license verified", systemImage: "checkmark.seal")
                                .font(.caption).foregroundStyle(.purple)
                        }
                        VStack(spacing: 6) {
                            Slider(
                                value: Binding(get: { player.position }, set: { player.seek(to: $0) }),
                                in: 0...max(player.duration, 1)
                            )
                            HStack { Text(player.position.podcastTime); Spacer(); Text(player.duration.podcastTime) }
                                .font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                        }
                        Button(action: player.togglePlayback) {
                            ZStack {
                                Circle().fill(.purple).frame(width: 82, height: 82)
                                if player.isBuffering { ProgressView().tint(.white) }
                                else { Image(systemName: player.isPlaying ? "pause.fill" : "play.fill").font(.title).foregroundStyle(.white) }
                            }
                        }
                        HStack(spacing: 20) {
                            Link("License", destination: track.licenseURL)
                            Link("Source", destination: track.sourceURL)
                        }
                        .font(.subheadline.bold())
                        if let error = player.errorMessage {
                            Label(error, systemImage: "exclamationmark.triangle").foregroundStyle(.red)
                        }
                        Spacer()
                    }
                    .padding(28)
                    .toolbar {
                        ToolbarItem(placement: .navigation) {
                            Button("Discover", systemImage: "chevron.down", action: dismiss.callAsFunction)
                        }
                    }
                }
            }
        }
    }
}

struct MusicMiniPlayer: View {
    @EnvironmentObject private var player: MusicPlayer
    let track: MusicTrack
    let onOpen: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Button(action: onOpen) {
                HStack(spacing: 10) {
                    MusicArtwork(url: track.artworkURL, size: 42)
                    VStack(alignment: .leading) {
                        Text(track.title).font(.subheadline.bold()).lineLimit(1)
                        Text(track.artist).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)
            Button(action: player.togglePlayback) {
                if player.isBuffering { ProgressView() }
                else { Image(systemName: player.isPlaying ? "pause.fill" : "play.fill").font(.title3) }
            }
            .buttonStyle(.borderedProminent).buttonBorderShape(.circle)
        }
        .padding(.horizontal, 14).padding(.vertical, 9)
        .background(.ultraThinMaterial)
        .overlay(alignment: .top) { Divider() }
    }
}
