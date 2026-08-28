import SwiftUI

struct ContentView: View {
    @Environment(\.scenePhase) private var scenePhase
    @EnvironmentObject private var player: RadioPlayer
    @EnvironmentObject private var podcastPlayer: PodcastPlayer
    @EnvironmentObject private var podcastLibrary: PodcastLibraryStore
    @EnvironmentObject private var musicPlayer: MusicPlayer
    @State private var showRadioPlayer = false
    @State private var showPodcastPlayer = false
    @State private var showMusicPlayer = false

    var body: some View {
        TabView {
            MusicDiscoveryView(onOpenPlayer: { showMusicPlayer = true })
            .tabItem { Label("Discover", systemImage: "sparkles") }

            NavigationStack {
                RadioView(onOpenPlayer: { showRadioPlayer = true })
            }
            .tabItem { Label("Radio", systemImage: "radio") }

            NavigationStack {
                PodcastView(onOpenPlayer: { showPodcastPlayer = true })
            }
            .tabItem { Label("Podcasts", systemImage: "mic.fill") }

            MusicLibraryView(onOpenPlayer: { showMusicPlayer = true })
            .tabItem { Label("Library", systemImage: "square.stack.fill") }
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if musicPlayer.isActive, let track = musicPlayer.currentTrack {
                MusicMiniPlayer(track: track, onOpen: { showMusicPlayer = true })
            } else if podcastPlayer.isActive, let episode = podcastPlayer.currentEpisode {
                PodcastMiniPlayer(episode: episode, onOpen: { showPodcastPlayer = true })
            } else if player.isActive, let station = player.currentStation {
                RadioMiniPlayer(station: station, onOpen: { showRadioPlayer = true })
            }
        }
        .sheet(isPresented: $showRadioPlayer) {
            RadioPlayerView()
        }
        .sheet(isPresented: $showPodcastPlayer) { PodcastPlayerView() }
        .sheet(isPresented: $showMusicPlayer) { MusicPlayerView() }
        .task {
            podcastPlayer.progressHandler = { episode, position, duration in
                podcastLibrary.updateProgress(
                    episodeID: episode.id,
                    feedURL: episode.feedURL,
                    position: position,
                    duration: duration
                )
            }
            PodcastBackgroundRefresh.schedule()
        }
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase != .active { podcastPlayer.checkpointProgress() }
        }
    }
}

private struct FeaturePreviewView: View {
    let title: String
    let subtitle: String
    let icon: String

    var body: some View {
        NavigationStack {
            ContentUnavailableView(title, systemImage: icon, description: Text(subtitle))
                .navigationTitle(title)
        }
    }
}
