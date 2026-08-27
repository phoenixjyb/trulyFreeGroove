import SwiftUI

struct RadioPlayerView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var player: RadioPlayer
    @EnvironmentObject private var saved: SavedStationStore

    var body: some View {
        NavigationStack {
            ZStack {
                LinearGradient(
                    colors: [.purple.opacity(0.38), .blue.opacity(0.16), .clear],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()

                if let station = player.currentStation {
                    VStack(spacing: 24) {
                        Spacer()
                        AsyncImage(url: station.faviconURL) { image in
                            image.resizable().scaledToFill()
                        } placeholder: {
                            Image(systemName: "radio.fill")
                                .font(.system(size: 76))
                                .foregroundStyle(.purple)
                        }
                        .frame(width: 220, height: 220)
                        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 38))
                        .clipShape(RoundedRectangle(cornerRadius: 38))
                        .shadow(color: .purple.opacity(0.25), radius: 28, y: 15)

                        VStack(spacing: 8) {
                            Text(station.name)
                                .font(.title.bold())
                                .multilineTextAlignment(.center)
                            Text(station.subtitle)
                                .foregroundStyle(.secondary)
                                .multilineTextAlignment(.center)
                            Label("Internet stream • online when checked", systemImage: "dot.radiowaves.left.and.right")
                                .font(.caption)
                                .foregroundStyle(.purple)
                        }

                        HStack(spacing: 34) {
                            Button { _ = player.switchStation(offset: -1) } label: {
                                Image(systemName: "backward.fill").font(.title)
                            }
                            Button(action: player.togglePlayback) {
                                ZStack {
                                    Circle().fill(.purple).frame(width: 78, height: 78)
                                    if player.isBuffering {
                                        ProgressView().tint(.white)
                                    } else {
                                        Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                                            .font(.title)
                                            .foregroundStyle(.white)
                                    }
                                }
                            }
                            Button { _ = player.switchStation(offset: 1) } label: {
                                Image(systemName: "forward.fill").font(.title)
                            }
                        }

                        if let error = player.errorMessage {
                            VStack {
                                Text(error).font(.caption).foregroundStyle(.red)
                                Button("Retry", action: player.retry)
                            }
                        }
                        Spacer()
                    }
                    .padding(28)
                    .toolbar {
                        ToolbarItem(placement: .navigation) {
                            Button("Stations", systemImage: "chevron.down", action: dismiss.callAsFunction)
                        }
                        ToolbarItem(placement: .primaryAction) {
                            Button { saved.toggle(station) } label: {
                                Image(systemName: saved.contains(station) ? "heart.fill" : "heart")
                            }
                            .accessibilityLabel(saved.contains(station) ? "Remove saved station" : "Save station")
                        }
                    }
                }
            }
        }
    }
}

struct RadioMiniPlayer: View {
    @EnvironmentObject private var player: RadioPlayer
    let station: RadioStation
    let onOpen: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Button(action: onOpen) {
                HStack(spacing: 10) {
                    AsyncImage(url: station.faviconURL) { image in
                        image.resizable().scaledToFill()
                    } placeholder: {
                        Image(systemName: "radio.fill").foregroundStyle(.purple)
                    }
                    .frame(width: 42, height: 42)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    VStack(alignment: .leading) {
                        Text(station.name).font(.subheadline.bold()).lineLimit(1)
                        Text(station.country).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)
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
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 9)
        .background(.ultraThinMaterial)
        .overlay(alignment: .top) { Divider() }
    }
}
