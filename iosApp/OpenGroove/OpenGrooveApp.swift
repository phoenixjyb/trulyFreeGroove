import SwiftUI

@main
struct OpenGrooveApp: App {
    @StateObject private var player = RadioPlayer()
    @StateObject private var savedStations = SavedStationStore()
    @StateObject private var recentStations = RecentStationStore()
    @StateObject private var podcastPlayer = PodcastPlayer()
    @StateObject private var podcastLibrary = PodcastLibraryStore()
    @StateObject private var musicPlayer = MusicPlayer()
    @StateObject private var musicLibrary = MusicLibraryStore()
    @StateObject private var uiLanguage = UiLanguageStore()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(player)
                .environmentObject(savedStations)
                .environmentObject(recentStations)
                .environmentObject(podcastPlayer)
                .environmentObject(podcastLibrary)
                .environmentObject(musicPlayer)
                .environmentObject(musicLibrary)
                .environmentObject(uiLanguage)
                .environment(\.locale, uiLanguage.selection.locale)
                .tint(.purple)
        }
        .backgroundTask(.appRefresh(PodcastBackgroundRefresh.identifier)) {
            _ = await podcastLibrary.refreshSubscribedFeeds()
            PodcastBackgroundRefresh.schedule()
        }
    }
}
