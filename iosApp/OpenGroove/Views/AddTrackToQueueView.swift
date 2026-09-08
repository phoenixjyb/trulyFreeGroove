import SwiftUI

struct AddTrackToQueueView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var player: MusicPlayer

    let track: MusicTrack

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    LabeledContent("Track", value: track.title)
                    LabeledContent("Queue", value: "\(player.queue.count) tracks")
                }

                Section {
                    if player.queue.isEmpty {
                        Button("Play now", systemImage: "play.fill") {
                            player.enqueue(track, playNext: false)
                            dismiss()
                        }
                    } else {
                        Button("Play next", systemImage: "text.line.first.and.arrowtriangle.forward") {
                            player.enqueue(track, playNext: true)
                            dismiss()
                        }
                        Button("Add to end", systemImage: "text.append") {
                            player.enqueue(track, playNext: false)
                            dismiss()
                        }
                    }
                }
            }
            .navigationTitle("Add to Queue")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", action: dismiss.callAsFunction)
                }
            }
        }
    }
}
