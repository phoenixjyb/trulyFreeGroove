import SwiftUI

@main
struct OpenGrooveApp: App {
    @StateObject private var player = RadioPlayer()
    @StateObject private var savedStations = SavedStationStore()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(player)
                .environmentObject(savedStations)
                .tint(.purple)
        }
    }
}
