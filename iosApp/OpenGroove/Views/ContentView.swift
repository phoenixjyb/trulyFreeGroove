import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var player: RadioPlayer
    @State private var showPlayer = false

    var body: some View {
        TabView {
            FeaturePreviewView(
                title: "Discover",
                subtitle: "Licensed music and official platform handoffs are the next parity slice.",
                icon: "music.note.list"
            )
            .tabItem { Label("Discover", systemImage: "sparkles") }

            NavigationStack {
                RadioView(onOpenPlayer: { showPlayer = true })
            }
            .tabItem { Label("Radio", systemImage: "radio") }

            FeaturePreviewView(
                title: "Podcasts",
                subtitle: "Publisher feeds, subscriptions and episode playback are the next parity slice.",
                icon: "mic.fill"
            )
            .tabItem { Label("Podcasts", systemImage: "mic.fill") }

            FeaturePreviewView(
                title: "Library",
                subtitle: "Saved radio stations are already available from the Radio tab.",
                icon: "square.stack.fill"
            )
            .tabItem { Label("Library", systemImage: "square.stack.fill") }
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if let station = player.currentStation {
                RadioMiniPlayer(station: station, onOpen: { showPlayer = true })
            }
        }
        .sheet(isPresented: $showPlayer) {
            RadioPlayerView()
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
