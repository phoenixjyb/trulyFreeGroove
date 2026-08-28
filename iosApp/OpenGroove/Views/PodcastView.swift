import SwiftUI

struct PodcastView: View {
    @EnvironmentObject private var library: PodcastLibraryStore
    @EnvironmentObject private var podcastPlayer: PodcastPlayer
    @EnvironmentObject private var radioPlayer: RadioPlayer
    @EnvironmentObject private var musicPlayer: MusicPlayer
    @StateObject private var model = PodcastViewModel()
    @State private var showAddFeed = false

    let onOpenPlayer: () -> Void

    var body: some View {
        List {
            Section {
                Picker("Podcasts", selection: $model.mode) {
                    ForEach(PodcastViewModel.Mode.allCases) { mode in
                        Text(mode.rawValue).tag(mode)
                    }
                }
                .pickerStyle(.segmented)
            }
            .listRowBackground(Color.clear)

            switch model.mode {
            case .search: searchContent
            case .subscriptions: subscriptionContent
            case .unplayed: unplayedContent
            }

            if let error = model.errorMessage {
                Section {
                    Label(error, systemImage: "exclamationmark.triangle")
                        .foregroundStyle(.red)
                }
            }
        }
        .navigationTitle("Podcasts")
        .searchable(text: $model.query, prompt: "Podcast, host or topic")
        .onSubmit(of: .search) { Task { await model.search() } }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button("Add RSS feed", systemImage: "dot.radiowaves.up.forward") {
                    showAddFeed = true
                }
            }
        }
        .sheet(isPresented: $showAddFeed) { AddPodcastFeedView() }
    }

    @ViewBuilder
    private var searchContent: some View {
        Section("Directory") {
            Picker("Search region", selection: $model.language) {
                ForEach(PodcastSearchLanguage.allCases) { language in
                    Text(language.rawValue).tag(language)
                }
            }
            if model.isLoading {
                HStack { Spacer(); ProgressView("Searching…"); Spacer() }
                    .padding(.vertical, 18)
            } else if model.results.isEmpty {
                ContentUnavailableView(
                    "Find a podcast",
                    systemImage: "magnifyingglass",
                    description: Text("Search the podcast directory or add a publisher RSS feed.")
                )
            } else {
                ForEach(model.results) { show in
                    NavigationLink(value: show) { PodcastShowRow(show: show) }
                }
            }
        }
        .navigationDestination(for: PodcastShow.self) { show in
            PodcastShowView(show: show, onOpenPlayer: onOpenPlayer)
        }
    }

    @ViewBuilder
    private var subscriptionContent: some View {
        if library.subscriptions.isEmpty {
            Section {
                ContentUnavailableView(
                    "No subscriptions",
                    systemImage: "dot.radiowaves.left.and.right",
                    description: Text("Subscribe from search results or add a publisher RSS feed.")
                )
            }
        } else {
            Section("Publisher feeds") {
                ForEach(library.subscriptions) { show in
                    NavigationLink(value: show) { PodcastShowRow(show: show) }
                }
                .onDelete { offsets in
                    for index in offsets { library.setSubscribed(library.subscriptions[index], subscribed: false) }
                }
            }
            .navigationDestination(for: PodcastShow.self) { show in
                PodcastShowView(show: show, onOpenPlayer: onOpenPlayer)
            }
        }
    }

    @ViewBuilder
    private var unplayedContent: some View {
        if library.unplayedEpisodes.isEmpty {
            Section {
                ContentUnavailableView(
                    "You're caught up",
                    systemImage: "checkmark.circle",
                    description: Text("New and unfinished episodes from subscriptions appear here.")
                )
            }
        } else {
            Section("New and in progress") {
                ForEach(library.unplayedEpisodes) { episode in
                    PodcastEpisodeRow(
                        episode: episode,
                        onPlay: { play(episode, context: library.unplayedEpisodes) },
                        onQueue: { podcastPlayer.addToQueue(episode) },
                        onTogglePlayed: { library.togglePlayed(episode) }
                    )
                }
            }
        }
    }

    private func play(_ episode: PodcastEpisode, context: [PodcastEpisode]) {
        musicPlayer.deactivate()
        radioPlayer.yieldRemoteControl()
        podcastPlayer.play(episode, context: context)
        onOpenPlayer()
    }
}

private struct PodcastShowView: View {
    @EnvironmentObject private var library: PodcastLibraryStore
    @EnvironmentObject private var podcastPlayer: PodcastPlayer
    @EnvironmentObject private var radioPlayer: RadioPlayer
    @EnvironmentObject private var musicPlayer: MusicPlayer
    @State private var loadedShow: PodcastShow
    @State private var episodes: [PodcastEpisode] = []
    @State private var episodeQuery = ""
    @State private var isLoading = false
    @State private var errorMessage: String?

    let onOpenPlayer: () -> Void

    private var visibleEpisodes: [PodcastEpisode] {
        episodes.filter { $0.matches(episodeQuery) }
    }

    init(show: PodcastShow, onOpenPlayer: @escaping () -> Void) {
        _loadedShow = State(initialValue: show)
        self.onOpenPlayer = onOpenPlayer
    }

    var body: some View {
        List {
            Section {
                HStack(alignment: .top, spacing: 14) {
                    PodcastArtwork(url: loadedShow.artworkURL, size: 86)
                    VStack(alignment: .leading, spacing: 6) {
                        Text(loadedShow.title).font(.title3.bold())
                        if !loadedShow.author.isEmpty {
                            Text(loadedShow.author).foregroundStyle(.secondary)
                        }
                        Label("Publisher RSS", systemImage: "checkmark.seal")
                            .font(.caption).foregroundStyle(.purple)
                    }
                }
                if !loadedShow.description.isEmpty {
                    Text(loadedShow.description).font(.subheadline).foregroundStyle(.secondary)
                }
                Button(library.isSubscribed(loadedShow) ? "Unsubscribe" : "Subscribe") {
                    library.setSubscribed(loadedShow, subscribed: !library.isSubscribed(loadedShow))
                }
                if let websiteURL = loadedShow.websiteURL {
                    Link("Publisher website", destination: websiteURL)
                }
            }

            if isLoading && episodes.isEmpty {
                Section { HStack { Spacer(); ProgressView("Loading publisher feed…"); Spacer() } }
            } else if visibleEpisodes.isEmpty {
                Section {
                    ContentUnavailableView(
                        episodeQuery.isEmpty ? "No playable episodes" : "No matching episodes",
                        systemImage: episodeQuery.isEmpty ? "waveform.slash" : "magnifyingglass"
                    )
                }
            } else {
                Section("Episodes") {
                    ForEach(visibleEpisodes) { episode in
                        PodcastEpisodeRow(
                            episode: episode,
                            onPlay: {
                                musicPlayer.deactivate()
                                radioPlayer.yieldRemoteControl()
                                podcastPlayer.play(episode, context: visibleEpisodes)
                                onOpenPlayer()
                            },
                            onQueue: { podcastPlayer.addToQueue(episode) },
                            onTogglePlayed: {
                                library.togglePlayed(episode)
                                episodes = library.cachedEpisodes(for: loadedShow.feedURL)
                            }
                        )
                    }
                }
            }

            if let errorMessage {
                Section { Label(errorMessage, systemImage: "exclamationmark.triangle").foregroundStyle(.red) }
            }
        }
        .navigationTitle(loadedShow.title)
        .navigationBarTitleDisplayMode(.inline)
        .searchable(text: $episodeQuery, prompt: "Search this show's episodes")
        .task { await load() }
        .refreshable { await load() }
    }

    private func load() async {
        episodes = library.cachedEpisodes(for: loadedShow.feedURL)
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            let feed = try await PodcastFeedDirectory().load(feedURL: loadedShow.feedURL, fallback: loadedShow)
            loadedShow = feed.show
            library.upsert(feed)
            episodes = library.cachedEpisodes(for: feed.show.feedURL)
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

private struct AddPodcastFeedView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var library: PodcastLibraryStore
    @State private var feedAddress = ""
    @State private var isLoading = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            Form {
                Section("Publisher RSS or Atom URL") {
                    TextField("https://publisher.example/feed.xml", text: $feedAddress)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.URL)
                        .autocorrectionDisabled()
                }
                Section {
                    Text("OpenGroove reads publisher metadata and streams the enclosure audio. It does not copy or download episodes.")
                        .font(.caption).foregroundStyle(.secondary)
                }
                if let errorMessage {
                    Section { Label(errorMessage, systemImage: "exclamationmark.triangle").foregroundStyle(.red) }
                }
            }
            .navigationTitle("Add Podcast Feed")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel", action: dismiss.callAsFunction) }
                ToolbarItem(placement: .confirmationAction) {
                    Button(isLoading ? "Adding…" : "Add") { Task { await add() } }
                        .disabled(isLoading || feedAddress.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }

    private func add() async {
        guard let url = URL(string: feedAddress.trimmingCharacters(in: .whitespacesAndNewlines)) else {
            errorMessage = PodcastFeedError.invalidURL.localizedDescription
            return
        }
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            let feed = try await PodcastFeedDirectory().load(feedURL: url)
            library.upsert(feed, subscribed: true)
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

private struct PodcastShowRow: View {
    let show: PodcastShow

    var body: some View {
        HStack(spacing: 12) {
            PodcastArtwork(url: show.artworkURL, size: 58)
            VStack(alignment: .leading, spacing: 4) {
                Text(show.title).font(.headline).lineLimit(2)
                Text(show.author).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                if !show.genre.isEmpty {
                    Text(show.genre).font(.caption2).foregroundStyle(.purple)
                }
            }
        }
        .padding(.vertical, 3)
    }
}

struct PodcastEpisodeRow: View {
    let episode: PodcastEpisode
    let onPlay: () -> Void
    let onQueue: () -> Void
    let onTogglePlayed: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Button(action: onPlay) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12).fill(.purple.opacity(0.12))
                    Image(systemName: episode.position > 0 ? "play.circle.fill" : "play.fill")
                        .font(.title2).foregroundStyle(.purple)
                }
                .frame(width: 50, height: 50)
            }
            .buttonStyle(.plain)

            VStack(alignment: .leading, spacing: 5) {
                Text(episode.title).font(.headline).lineLimit(3)
                Text(episode.showTitle).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                HStack(spacing: 8) {
                    if let publishedAt = episode.publishedAt {
                        Text(publishedAt, style: .date)
                    }
                    if episode.duration > 0 { Text(episode.duration.podcastTime) }
                    if episode.position > 0 { Text("\(episode.position.podcastTime) played") }
                }
                .font(.caption2).foregroundStyle(.secondary)
            }

            Menu {
                Button("Play next", systemImage: "text.line.first.and.arrowtriangle.forward") { onQueue() }
                Button(episode.completed ? "Mark unplayed" : "Mark played", systemImage: "checkmark.circle") {
                    onTogglePlayed()
                }
                if let websiteURL = episode.websiteURL {
                    Link("Publisher episode page", destination: websiteURL)
                }
            } label: {
                Image(systemName: "ellipsis").frame(width: 28, height: 36)
            }
        }
        .padding(.vertical, 4)
        .opacity(episode.completed ? 0.58 : 1)
    }
}

struct PodcastArtwork: View {
    let url: URL?
    let size: CGFloat

    var body: some View {
        AsyncImage(url: url) { image in
            image.resizable().scaledToFill()
        } placeholder: {
            Image(systemName: "mic.fill").font(.title2).foregroundStyle(.purple)
        }
        .frame(width: size, height: size)
        .background(.purple.opacity(0.1), in: RoundedRectangle(cornerRadius: size * 0.2))
        .clipShape(RoundedRectangle(cornerRadius: size * 0.2))
    }
}

extension TimeInterval {
    var podcastTime: String {
        guard isFinite, self > 0 else { return "0:00" }
        let total = Int(self)
        if total >= 3600 {
            return String(format: "%d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60)
        }
        return String(format: "%d:%02d", total / 60, total % 60)
    }
}
