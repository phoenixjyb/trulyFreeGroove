import SwiftUI

struct AppSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var uiLanguage: UiLanguageStore

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    VStack(alignment: .leading, spacing: 14) {
                        Label {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Language").font(.headline)
                                Text("App interface")
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                            }
                        } icon: {
                            Image(systemName: "character.bubble")
                                .foregroundStyle(.purple)
                        }

                        Picker(
                            "Interface language",
                            selection: Binding(
                                get: { uiLanguage.selection },
                                set: { uiLanguage.select($0) }
                            )
                        ) {
                            ForEach(UiLanguage.allCases) { language in
                                Text(verbatim: language.compactLabel).tag(language)
                            }
                        }
                        .pickerStyle(.segmented)
                        .labelsHidden()
                        .accessibilityLabel("Interface language")
                    }
                    .padding(.vertical, 4)
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done", action: dismiss.callAsFunction)
                }
            }
        }
        .presentationDetents([.medium])
    }
}
