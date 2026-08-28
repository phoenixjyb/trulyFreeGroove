import SwiftUI

struct RadioView: View {
    @EnvironmentObject private var player: RadioPlayer
    @EnvironmentObject private var podcastPlayer: PodcastPlayer
    @EnvironmentObject private var musicPlayer: MusicPlayer
    @EnvironmentObject private var saved: SavedStationStore
    @EnvironmentObject private var recent: RecentStationStore
    @StateObject private var model = RadioViewModel()

    let onOpenPlayer: () -> Void

    private let genres = ["Pop", "Rock", "Jazz", "Classical", "Electronic", "Hip Hop", "Blues", "Reggae"]
    private let categories = ["News", "Talk", "Sports", "Culture", "Education", "Kids", "Community"]

    private var visibleStations: [RadioStation] {
        switch model.mode {
        case .discover: model.stations
        case .saved: saved.stations
        case .recent: recent.stations
        }
    }

    var body: some View {
        List {
            Section {
                Picker("Stations", selection: $model.mode) {
                    ForEach(RadioViewModel.ListMode.allCases) { mode in
                        Text(mode.rawValue).tag(mode)
                    }
                }
                .pickerStyle(.segmented)
            }
            .listRowBackground(Color.clear)

            if model.mode == .discover {
                Section("Browse") {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 9) {
                            BrowseMenu(title: model.selectedCountry?.name ?? "Country", icon: "globe.asia.australia") {
                                Button("All countries") { Task { await model.select(country: nil) } }
                                ForEach(model.countries.prefix(80)) { country in
                                    Button("\(country.name) (\(country.stationCount))") {
                                        Task { await model.select(country: country) }
                                    }
                                }
                            }
                            BrowseMenu(title: model.selectedTag ?? "Genre", icon: "music.quarternote.3") {
                                Button("All genres") { Task { await model.select(tag: nil) } }
                                ForEach(genres, id: \.self) { genre in
                                    Button(genre) { Task { await model.select(tag: genre) } }
                                }
                            }
                            BrowseMenu(title: "Category", icon: "square.grid.2x2") {
                                ForEach(categories, id: \.self) { category in
                                    Button(category) { Task { await model.select(tag: category) } }
                                }
                            }
                        }
                    }
                }
            }

            if model.mode == .discover && model.isLoading && visibleStations.isEmpty {
                Section {
                    HStack {
                        Spacer()
                        ProgressView("Finding working stations…")
                        Spacer()
                    }
                    .padding(.vertical, 30)
                }
            } else if visibleStations.isEmpty {
                Section {
                    ContentUnavailableView(
                        emptyTitle,
                        systemImage: model.mode == .saved ? "heart" : "radio",
                        description: Text(emptyDescription)
                    )
                }
            } else {
                Section(listTitle) {
                    ForEach(visibleStations) { station in
                        StationRow(
                            station: station,
                            isSaved: saved.contains(station),
                            onPlay: {
                                musicPlayer.deactivate()
                                podcastPlayer.deactivateForRadio()
                                player.play(station, queue: visibleStations)
                                recent.record(station)
                                model.registerClick(station)
                                onOpenPlayer()
                            },
                            onSave: { saved.toggle(station) }
                        )
                    }
                }
            }

            if model.mode == .recent && !recent.stations.isEmpty {
                Section { Button("Clear recent stations", role: .destructive, action: recent.clear) }
            }

            if model.mode == .discover, let error = model.errorMessage {
                Section {
                    Label(error, systemImage: "exclamationmark.triangle")
                        .foregroundStyle(.red)
                }
            }
        }
        .navigationTitle("Radio")
        .searchable(text: $model.query, prompt: "Station name")
        .onSubmit(of: .search) { Task { await model.search() } }
        .refreshable { await model.search() }
        .task { await model.start() }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                if model.isLoading { ProgressView() }
            }
        }
    }

    private var listTitle: String {
        switch model.mode {
        case .discover: "Working internet streams"
        case .saved: "Saved stations"
        case .recent: "Recently played"
        }
    }

    private var emptyTitle: String {
        switch model.mode {
        case .discover: "No stations found"
        case .saved: "No saved stations"
        case .recent: "No recently played stations"
        }
    }

    private var emptyDescription: String {
        switch model.mode {
        case .discover: "Try another name, country or category."
        case .saved: "Save a station to keep it here."
        case .recent: "Stations you play will appear here."
        }
    }
}

private struct BrowseMenu<Content: View>: View {
    let title: String
    let icon: String
    @ViewBuilder let content: Content

    var body: some View {
        Menu {
            content
        } label: {
            Label(title, systemImage: icon)
                .font(.subheadline.weight(.semibold))
                .padding(.horizontal, 13)
                .padding(.vertical, 9)
                .background(.purple.opacity(0.12), in: Capsule())
        }
    }
}

private struct StationRow: View {
    let station: RadioStation
    let isSaved: Bool
    let onPlay: () -> Void
    let onSave: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            AsyncImage(url: station.faviconURL) { image in
                image.resizable().scaledToFill()
            } placeholder: {
                Image(systemName: "radio.fill")
                    .font(.title2)
                    .foregroundStyle(.purple)
            }
            .frame(width: 52, height: 52)
            .background(.purple.opacity(0.1), in: RoundedRectangle(cornerRadius: 13))
            .clipShape(RoundedRectangle(cornerRadius: 13))

            Button(action: onPlay) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(station.name)
                        .font(.headline)
                        .foregroundStyle(.primary)
                        .lineLimit(2)
                    Text(station.subtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                    Label("Online when checked", systemImage: "checkmark.circle.fill")
                        .font(.caption2)
                        .foregroundStyle(.green)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)

            Button(action: onSave) {
                Image(systemName: isSaved ? "heart.fill" : "heart")
                    .foregroundStyle(isSaved ? .pink : .secondary)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel(isSaved ? "Remove saved station" : "Save station")
        }
        .padding(.vertical, 3)
    }
}
