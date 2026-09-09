import SwiftUI

struct MusicDiscoveryView: View {
    @EnvironmentObject private var musicPlayer: MusicPlayer
    @EnvironmentObject private var radioPlayer: RadioPlayer
    @EnvironmentObject private var podcastPlayer: PodcastPlayer
    @StateObject private var model = MusicViewModel()
    @State private var presentedSheet: MusicDiscoverySheet?

    let onOpenPlayer: () -> Void

    var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack(alignment: .top, spacing: 12) {
                        VStack(alignment: .leading, spacing: 5) {
                            Text("OpenGroove").font(.largeTitle.weight(.black)).foregroundStyle(.purple)
                            Text("Music with a clear source.").foregroundStyle(.secondary)
                        }
                        Spacer(minLength: 8)
                        Button {
                            presentedSheet = .settings
                        } label: {
                            Image(systemName: "gearshape")
                                .font(.title3)
                                .foregroundStyle(.secondary)
                                .frame(width: 44, height: 44)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Settings")
                    }
                    Picker("Language", selection: $model.language) {
                        ForEach(MusicSearchLanguage.allCases) { language in
                            Text(LocalizedStringKey(language.rawValue)).tag(language)
                        }
                    }
                    .onChange(of: model.language) { _, _ in Task { await model.search() } }
                }

                Section("Search official platforms") {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 9) {
                            ForEach(OfficialMusicProvider.allCases) { provider in
                                if let url = provider.searchURL(query: effectiveOfficialQuery) {
                                    Link(destination: url) {
                                        Label(provider.rawValue, systemImage: "arrow.up.right.square")
                                            .font(.caption.bold())
                                            .padding(.horizontal, 11).padding(.vertical, 8)
                                            .background(.purple.opacity(0.1), in: Capsule())
                                    }
                                }
                            }
                        }
                    }
                    Text("These buttons hand search to each platform's official website; OpenGroove does not extract or play their media.")
                        .font(.caption).foregroundStyle(.secondary)
                }

                if !model.jamendoConfigured {
                    Section {
                        Label(
                            "Wikimedia Commons is active. Add your own Jamendo client ID to expand the open-music catalog.",
                            systemImage: "info.circle"
                        )
                        .font(.caption).foregroundStyle(.secondary)
                    }
                }

                Section {
                    if model.isLoading && model.tracks.isEmpty {
                        HStack { Spacer(); ProgressView("Searching licensed sources…"); Spacer() }
                            .padding(.vertical, 24)
                    } else if model.tracks.isEmpty {
                        ContentUnavailableView(
                            "No licensed tracks",
                            systemImage: "music.note.slash",
                            description: Text("Try another song, artist, mood or language.")
                        )
                    } else {
                        ForEach(model.tracks) { track in
                            MusicTrackRow(
                                track: track,
                                onPlay: { play(track) },
                                onAdd: { presentedSheet = .playlist(track) },
                                onQueue: { presentedSheet = .queue(track) }
                            )
                        }
                    }
                } header: {
                    Text(LocalizedStringKey(model.query.isEmpty ? "Fresh finds" : "Licensed results"))
                }

                if let error = model.errorMessage {
                    Section {
                        Label {
                            Text(LocalizedStringKey(error))
                        } icon: {
                            Image(systemName: "exclamationmark.triangle")
                        }
                        .foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle("Discover")
            .searchable(text: $model.query, prompt: "Song, artist, mood…")
            .onSubmit(of: .search) { Task { await model.search() } }
            .refreshable { await model.search() }
            .task { await model.start() }
            .sheet(item: $presentedSheet) { sheet in
                switch sheet {
                case let .playlist(track): AddTrackToPlaylistView(track: track)
                case let .queue(track): AddTrackToQueueView(track: track)
                case .settings: AppSettingsView()
                }
            }
        }
    }

    private var effectiveOfficialQuery: String {
        [model.query.isEmpty ? "music" : model.query, model.language.searchHint]
            .filter { !$0.isEmpty }.joined(separator: " ")
    }

    private func play(_ track: MusicTrack) {
        radioPlayer.yieldRemoteControl()
        podcastPlayer.deactivateForRadio()
        musicPlayer.play(track)
        onOpenPlayer()
    }
}

enum OfficialMusicProvider: String, CaseIterable, Identifiable {
    case youtubeMusic = "YouTube Music"
    case youtube = "YouTube"
    case spotify = "Spotify"
    case qqMusic = "QQ音乐"
    case netease = "网易云音乐"

    var id: Self { self }

    func searchURL(query: String) -> URL? {
        switch self {
        case .youtubeMusic:
            return queryURL("https://music.youtube.com/search", name: "q", value: query)
        case .youtube:
            return queryURL("https://www.youtube.com/results", name: "search_query", value: query)
        case .spotify:
            let unreserved = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "-._~"))
            let encoded = query.addingPercentEncoding(withAllowedCharacters: unreserved) ?? "music"
            var components = URLComponents(string: "https://open.spotify.com")
            components?.percentEncodedPath = "/search/\(encoded)"
            return components?.url
        case .qqMusic:
            var components = URLComponents(string: "https://y.qq.com/n/ryqq/search")
            components?.queryItems = [URLQueryItem(name: "w", value: query), URLQueryItem(name: "t", value: "song")]
            return components?.url
        case .netease:
            let allowed = CharacterSet.urlQueryAllowed.subtracting(CharacterSet(charactersIn: "&=?+#"))
            let encoded = query.addingPercentEncoding(withAllowedCharacters: allowed) ?? "music"
            return URL(string: "https://music.163.com/#/search/m/?s=\(encoded)&type=1")
        }
    }

    private func queryURL(_ base: String, name: String, value: String) -> URL? {
        var components = URLComponents(string: base)
        components?.queryItems = [URLQueryItem(name: name, value: value)]
        return components?.url
    }
}

struct MusicTrackRow: View {
    let track: MusicTrack
    let onPlay: () -> Void
    let onAdd: () -> Void
    let onQueue: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            MusicArtwork(url: track.artworkURL, size: 58)
            VStack(alignment: .leading, spacing: 4) {
                Text(track.title).font(.headline).lineLimit(2)
                Text(track.artist).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                Label(track.providerName, systemImage: "checkmark.circle.fill")
                    .font(.caption2).foregroundStyle(.purple)
                HStack(spacing: 12) {
                    Link("License", destination: track.licenseURL)
                    Link("Source", destination: track.sourceURL)
                }
                .font(.caption2)
            }
            Spacer()
            Button(action: onAdd) { Image(systemName: "text.badge.plus") }
                .buttonStyle(.borderless).accessibilityLabel("Add to playlist")
            Button(action: onQueue) { Image(systemName: "text.line.first.and.arrowtriangle.forward") }
                .buttonStyle(.borderless).accessibilityLabel("Add to queue")
            Button(action: onPlay) { Image(systemName: "play.fill") }
                .buttonStyle(.borderedProminent).buttonBorderShape(.circle)
        }
        .padding(.vertical, 4)
    }
}

private enum MusicDiscoverySheet: Identifiable {
    case playlist(MusicTrack)
    case queue(MusicTrack)
    case settings

    var id: String {
        switch self {
        case let .playlist(track): "playlist:\(track.providerName):\(track.id)"
        case let .queue(track): "queue:\(track.providerName):\(track.id)"
        case .settings: "settings"
        }
    }
}

struct MusicArtwork: View {
    let url: URL?
    let size: CGFloat

    var body: some View {
        AsyncImage(url: url) { image in image.resizable().scaledToFill() } placeholder: {
            Image(systemName: "music.note").font(.title2).foregroundStyle(.purple)
        }
        .frame(width: size, height: size)
        .background(.purple.opacity(0.1), in: RoundedRectangle(cornerRadius: size * 0.2))
        .clipShape(RoundedRectangle(cornerRadius: size * 0.2))
    }
}

private struct AddTrackToPlaylistView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.locale) private var locale
    @EnvironmentObject private var library: MusicLibraryStore
    @State private var newPlaylistName = ""
    let track: MusicTrack

    var body: some View {
        NavigationStack {
            List {
                if library.playlists.isEmpty {
                    Section { Text("Create a playlist first.").foregroundStyle(.secondary) }
                } else {
                    Section("Choose a playlist") {
                        ForEach(library.playlists) { playlist in
                            Button {
                                library.add(track, to: playlist.id)
                                dismiss()
                            } label: {
                                HStack { Text(playlist.name); Spacer(); Text("\(playlist.tracks.count)").foregroundStyle(.secondary) }
                            }
                        }
                    }
                }
                Section("New playlist") {
                    TextField("Playlist name", text: $newPlaylistName)
                    Button("Create and add") {
                        if library.createPlaylist(named: newPlaylistName),
                           let playlist = library.playlists.last {
                            library.add(track, to: playlist.id)
                            dismiss()
                        }
                    }
                    .disabled(newPlaylistName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
            .navigationTitle(localizedUiFormat("Add “%@”", locale: locale, arguments: [track.title]))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel", action: dismiss.callAsFunction) } }
        }
    }
}
