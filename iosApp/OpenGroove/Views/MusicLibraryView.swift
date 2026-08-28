import SwiftUI

struct MusicLibraryView: View {
    @EnvironmentObject private var library: MusicLibraryStore
    @EnvironmentObject private var musicPlayer: MusicPlayer
    @EnvironmentObject private var radioPlayer: RadioPlayer
    @EnvironmentObject private var podcastPlayer: PodcastPlayer
    @State private var showCreatePlaylist = false

    let onOpenPlayer: () -> Void

    var body: some View {
        NavigationStack {
            List {
                if library.playlists.isEmpty {
                    ContentUnavailableView(
                        "Start your first playlist",
                        systemImage: "music.note.list",
                        description: Text("Create one, then add any license-explicit track you discover.")
                    )
                } else {
                    ForEach(library.playlists) { playlist in
                        Section("\(playlist.name) • \(playlist.tracks.count) tracks") {
                            if playlist.tracks.isEmpty {
                                Text("No tracks yet").foregroundStyle(.secondary)
                            } else {
                                ForEach(playlist.tracks) { track in
                                    PlaylistMusicTrackRow(
                                        track: track,
                                        onPlay: {
                                            radioPlayer.yieldRemoteControl()
                                            podcastPlayer.deactivateForRadio()
                                            musicPlayer.play(track)
                                            onOpenPlayer()
                                        },
                                        onRemove: { library.remove(track, from: playlist.id) }
                                    )
                                    .swipeActions {
                                        Button(role: .destructive) { library.remove(track, from: playlist.id) } label: {
                                            Label("Remove", systemImage: "trash")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("Your Playlists")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button("Create playlist", systemImage: "plus") { showCreatePlaylist = true }
                }
            }
            .sheet(isPresented: $showCreatePlaylist) { CreateMusicPlaylistView() }
        }
    }
}

private struct PlaylistMusicTrackRow: View {
    let track: MusicTrack
    let onPlay: () -> Void
    let onRemove: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            MusicArtwork(url: track.artworkURL, size: 50)
            Button(action: onPlay) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(track.title).font(.headline).lineLimit(2)
                    Text(track.artist).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)
            Link(destination: track.sourceURL) { Image(systemName: "arrow.up.right.square") }
                .accessibilityLabel("Open source")
            Button(role: .destructive, action: onRemove) { Image(systemName: "trash") }
                .buttonStyle(.borderless).accessibilityLabel("Remove from playlist")
        }
        .padding(.vertical, 3)
    }
}

private struct CreateMusicPlaylistView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var library: MusicLibraryStore
    @State private var name = ""
    @State private var isDuplicate = false

    var body: some View {
        NavigationStack {
            Form {
                TextField("Playlist name", text: $name)
                if isDuplicate { Text("Use a unique name of 50 characters or fewer.").foregroundStyle(.red) }
            }
            .navigationTitle("New Playlist")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel", action: dismiss.callAsFunction) }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Create") {
                        if library.createPlaylist(named: name) { dismiss() } else { isDuplicate = true }
                    }
                    .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }
}
