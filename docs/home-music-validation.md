# Home Music local validation — 2026-11-08

Scope: Android-first implementation on the existing `codex/ios-settings-card`
branch. The working tree was clean at entry; its two existing local commits
were preserved. Local validation did not run GitHub Actions or merge changes.
Delivery uses the separate `codex/home-music` branch, with `[skip ci]` in the
commit message to preserve the owner's GitHub Actions pause.

## Automated checks

`./gradlew --offline --no-daemon :app:testDebugUnitTest
:shared:testDebugUnitTest :app:assembleDebug :app:lintDebug` passed: 38 Android
unit tests and 17 shared unit tests, with no failures or errors. Tests include
Subsonic token authentication, LAN URL validation, metadata parsing/pagination,
opaque song IDs, HTTP redirect rejection, redacted errors, credential-free
queue restoration and the distinct Home Music playback policy. Debug APK and
Android lint passed. Existing AGP/Kotlin/SDK compatibility warnings remain.
Dependencies needed the configured proxy on the first run; the final gate used
the cache. No repository proxy configuration was added.

The independent Android review found one opaque-ID decoding edge case, fixed
before the final gate. Public provider and YouTube playback restrictions remain
covered by the existing shared tests. iOS Home Music is not implemented; iOS
build and physical-device checks were not run for this Android-only scope.

## Server and formats

Eight Python preparation tests also passed, including path/overwrite safeguards,
CUE rejection cases and normalization.

See [server preparation and format matrix](home-music-server.md) for the actual
Navidrome v0.64.2 API and generated-audio evidence. Windows was reached through
the documented private SSH route only for a read-only identity check. No music
folder discovery, installation, account/service/firewall change, or collection
mutation was performed. Real folder paths will be supplied later.

## Emulator

The existing Android emulator required a cold boot after its saved snapshot
failed. It had no active network interface, so the temporary loopback test
server was connected through an ADB reverse mapping. This verifies local app to
server behavior, not physical home Wi-Fi or Windows connectivity.

Connection, album browsing, matching and empty search results were visually
inspected against the real temporary Navidrome API. The synthetic account was
entered through the UI; no password was added to source or screenshots.

“Play all” created a two-track queue. MediaSession reported PLAYING at 8 seconds,
then at 52 seconds after the app went to the background. PlaybackService was a
foreground media-playback service with a notification. A system media Next
command advanced to the FLAC-source second track and MediaSession remained
PLAYING without an error. The queue/player UI was visually inspected. Installing the final debug APK over
the test build restored the two-track queue, current FLAC-source item and
position in a paused state. The saved account also reloaded and browsed albums
across that restart.

The emulator's backup-excluded account file contained neither the synthetic
password nor the plain server URL. Its persisted two-track queue contained only
`home-music://` references, retained the second-track index and listening
position, and contained no authenticated stream URL or password. Disconnect
removed the synthetic credential file and cleared the MediaSession queue
(state NONE, size zero). The ADB mapping was removed and the temporary
Navidrome process was stopped; port 14533 no longer had a listener.

## Delivery gates

Windows installation/service/firewall changes and physical Android installation
need separate authorization. Real collection indexing and format/tag/CUE review
await the folder paths. Physical home Wi-Fi, lock-screen, background battery
behavior and interruptions still require device acceptance.
