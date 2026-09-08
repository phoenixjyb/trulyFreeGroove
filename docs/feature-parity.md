# OpenGroove feature parity

Every product feature has one shared behavior contract and two platform acceptance gates. A feature is complete only when both applications pass, unless a platform difference is explicitly documented.

The current parity milestone intentionally excludes Android's optional YouTube search/watch screen at the owner's direction. Its known platform difference remains recorded below, but it is not part of the work described by the queue and Chinese-radio evidence.

| Capability | Shared contract | Android | iOS | Remaining gate |
|---|---|---|---|---|
| Licensed music discovery | Track model, language scopes and fail-closed direct-playback policy | Wikimedia Commons plus optional Jamendo adapter | Wikimedia Commons plus optional Jamendo adapter built and unit-tested on iOS Simulator | Live catalog checks and attribution review on both physical phones |
| Official music handoffs | External-only boundary | YouTube Music, YouTube, Spotify, QQ Music and NetEase | Same five official HTTPS search handoffs built and URL-tested | Open each installed-app/web fallback on both physical phones |
| Official YouTube search and watch | Canonical video reference, embeddable-only policy, visible-player boundary | Data API v3 adapter, saved references and official IFrame player built; API key and device acceptance open | External handoff only | Add the `WKWebView` adapter, then verify real searches, visible playback, lifecycle pause, links, ads and restrictions on both phones |
| Licensed music playback | HTTPS stream plus public license evidence | Media3 service and seek controls | AVPlayer, seek and system commands built on iOS Simulator | Physical background, interruption and system-control checks on both phones |
| Local playlists | Track identity and provider/license metadata | Room persistence | Local Codable persistence built and unit-tested on iOS Simulator | Persistence/lifecycle checks on both physical phones |
| Music queue and playlist playback | Play-all ordering, current item, position, shuffle and repeat semantics | Android Media3 queue, Play next/end, automatic advance, reorder/remove, shuffle/repeat and service-owned restoration built and unit-tested | AVPlayer queue, Play next/end, automatic advance, reorder/remove, shuffle/repeat and app-owned restoration built and unit-tested | Physical playback, process-relaunch, background and system-control acceptance on both phones |
| Internet radio discovery | Station validity and browse vocabulary | Implemented | Built and unit-tested on iOS Simulator | Physical iPhone search and browsing |
| Chinese radio discovery | Directory-backed region/language filters; no bundled station URLs | Shortcuts for 中国大陆, 香港粤语, 台湾省 and 全球中文 plus Chinese/Cantonese filters built and unit-tested | Same shortcuts, language filters and 台湾省 display mapping built, unit-tested and live-directory checked on iPhone 15 Simulator | Physical search, playback and persistence acceptance on both phones |
| Radio playback | Public HTTP(S) stream boundary | Media3 service | AVPlayer and system commands built on iOS Simulator | Physical iPhone background, controls and HLS |
| Saved and recent stations | Station identity and 20-item recent cap | Room | Local Codable stores built and unit-tested on iOS Simulator | Physical persistence and lifecycle review on both phones |
| Podcast discovery and feeds | Models, publisher-feed identity and search matching | Implemented; device acceptance open | Apple directory, direct RSS/Atom and publisher metadata built and unit-tested on iOS Simulator | Live directory/feed checks on both physical phones |
| Podcast library and inbox | Subscription, episode and progress semantics | Room persistence | Local Codable persistence, subscriptions, show search and Unplayed inbox built and unit-tested on iOS Simulator | Persistence/lifecycle checks on both physical phones |
| Podcast playback | Public publisher enclosure policy, queue, 0.75×–2× speed and 15–60 minute timer | Media3 service | AVPlayer queue, resume, automatic advance, speed, sleep timer and system commands built on iOS Simulator | Physical background audio, controls, timer and interruption checks on both phones |
| Podcast metadata refresh | Metadata-only, approximately 12-hour cadence | WorkManager periodic job | BGAppRefreshTask request; opportunistic system scheduling | Observe refresh on physical Android and iPhone under normal power/network conditions |
| Theme | Product design tokens | System light/dark | System light/dark; simulator shell visually checked | Physical light/dark comparison |

## Android reliability hardening evidence (2026-08-28)

- Podcast completion now uses the shared rule requiring the later of 90% played or entry into the final 30 seconds; an unknown duration never completes an episode.
- `PlaybackService` owns background-safe podcast progress checkpoints every 15 seconds and on pause, completion and episode transitions.
- Publisher feeds preserve nested description text, reject malformed durations and responses larger than 8 MiB, and retain at most 250 episodes per feed.
- Room retains at most ten unsubscribed podcast feed caches, while subscribed shows remain protected from pruning.
- Android unit tests, lint and debug APK assembly pass locally: 28 tests, zero failures.
- No emulator was used. The debug APK installed and launched without a fatal exception on a physical Samsung SM-X520, but streaming, background playback, notification and lock-screen controls, queue advance, playback speed, sleep timer, refresh and relaunch persistence still require hands-on acceptance.

## Android YouTube Watch implementation evidence

- YouTube results use the official Data API v3 and are filtered to canonical, embeddable, syndicated video references before display.
- Playback uses YouTube's visible IFrame player inside the operating-system WebView with the Android application ID supplied as the required HTTPS referrer identity.
- The player does not autoplay and is removed when its screen or app becomes hidden, then reloaded only after the app is visible again; OpenGroove never passes YouTube audiovisual content to Media3.
- Saved video metadata is local-only and is refreshed or deleted after 30 days through the official API.
- Android unit tests, lint and debug APK assembly pass locally: 33 tests, zero failures.
- No credential is committed. A key-enabled APK was installed in place on the physical Samsung SM-S9280, the Room 1-to-2 database opened, and a live search returned embeddable results.
- No emulator was used. The official player rendered its thumbnail, branding and controls, and the background/foreground cycle removed and reloaded it without a fatal exception. YouTube then required "Sign in to confirm that you're not a bot". The browser-backed Custom Tab fallback was installed and an official YouTube URL opened in an Edge Custom Tab with integrated browser controls. Human acceptance of the signed-in close/back flow, audiovisual playback and full-screen behavior remains open.

## Music queue implementation evidence (updated 2026-09-08)

- A playlist can start one Media3 timeline at any playable track or through a dedicated Play all action; automatic advancement and notification/lock-screen next/previous commands stay owned by `PlaybackService`.
- Licensed discovery and playlist tracks can be inserted next or at the end. The full music player can jump, reorder, remove or clear queued tracks and cycle shuffle, repeat-all and repeat-one.
- The service checkpoints the active music queue, current item, position, shuffle and repeat state. Restoration revalidates every track against the existing direct-playback policy and fails closed instead of restoring external-only or unlicensed media.
- Focused JVM tests cover queue round-trip, position/mode retention, authorization filtering and malformed state. Physical Android playback, automatic advance, queue edits, process relaunch and system controls remain open acceptance gates.
- iOS commit `83e518c` implements the same play-all, Play next/end, automatic-advance, editable-queue, shuffle, repeat and restoration contract with AVPlayer. Restoration rechecks both local HTTPS/license evidence and the shared Kotlin playback policy before constructing a paused player item.
- Swift tests cover queue round-trip, invalid-track filtering and index remapping, the 500-item bound, queue edits, repeat wrapping and no-repeat shuffle traversal. The iOS Simulator build/run passed; physical playback, queue edits, relaunch restoration and lock-screen controls remain open acceptance gates.

## Chinese radio implementation evidence (updated 2026-09-08)

- The Radio Browser search adapter accepts a language together with an optional country code, while retaining broken-station filtering, popularity ordering and pagination.
- Quick filters map 中国大陆 to `CN`, 香港粤语 to `HK` plus `cantonese`, 台湾省 to `TW`, and 全球中文 to `chinese`. The requested 台湾省 wording is a product display label; the ISO-style directory code remains unchanged.
- Live directory checks returned currently reachable samples for all four query shapes. This is catalog evidence only: `lastcheckok=1` does not prove that a programme is genuinely live or that every stream will play on the target phone.
- Android and shared JVM tests cover combined country/language parameters, Chinese metadata preservation, the 台湾省 label/code boundary and the Hong Kong Cantonese preset.
- iOS commit `83e518c` uses the same four presets and directory values. Swift tests cover combined country/language query normalization plus the 台湾省 and Hong Kong Cantonese boundaries; a live iPhone 15 Simulator run displayed the shortcuts and returned Hong Kong Cantonese directory results.
- Those live results prove directory discovery only. Physical search, audio playback, save/recent persistence and lifecycle acceptance remain open on both platforms.

## Current iOS build evidence

- Xcode 26.6 built the Debug application and passed tests for an iPhone 15 simulator running iOS 26.5.
- The Xcode build phase linked the Kotlin `OpenGrooveShared` framework, and Swift resolved the shared playback-policy API.
- Twenty-three Swift tests passed in the iOS Simulator test bundle: ten music catalog/handoff/playlist/policy/queue tests, eight podcast catalog/feed/persistence/control-contract tests, and five radio directory/filter/recent-store tests.
- The built application installed and launched in the simulator, and the initial SwiftUI shell rendered without a crash.
- Discover, official handoffs, Library/create-playlist, Podcasts and direct publisher-RSS screens were visually checked in the iPhone 15 simulator.
- Simulator rendering and live directory discovery do not prove physical playback, background behavior, system controls or lifecycle acceptance on the target iPhone 15.

## Pre-device hardening evidence (2026-08-28)

- The ARM64 iPhoneOS application and all 16 Swift test functions compile and link with `build-for-testing`; no simulator was used for this check.
- Xcode static analysis passes for the generic physical-iOS target, and application validation no longer reports an incomplete iPad orientation declaration.
- A macOS executable compiled directly from the production podcast model, parser and store sources passed duration validation, nested RSS text parsing, bounded-cache and persistence smoke checks.
- Player end notifications are scoped to their owning `AVPlayerItem`; player switches also re-establish the correct audio-session mode and remote-command ownership.
- These checks prove source and device-target build readiness only. Physical playback, lifecycle and operating-system integration remain open.

## Physical iPhone development integration evidence (2026-09-08)

- Xcode 26.6 generated, built and automatically signed OpenGroove 0.3.0 (1) from source commit `83e518c` for ARM64 iPhoneOS using a machine-local development-team override.
- The signed app installed and launched on a paired, wired iPhone 16 Plus running iOS 26.6.1 with Developer Mode enabled; CoreDevice then reported the OpenGroove process running.
- `scripts/run-ios-device.sh` now provides the repeatable generate, sign, build, install and launch path without committing an Apple team or device identifier.
- This closes development signing, installation and process-launch readiness for the new queue and Chinese-radio source on the attached iPhone 16 Plus only. Visible-screen acceptance, live music/podcast/radio playback, queue edits and restoration, background audio, lock-screen controls, interruptions, persistence and the specified iPhone 15 hardware gate remain open.

## Next physical iPhone acceptance run

1. Record the installed commit and app version, then launch in both light and dark appearance.
2. Play music, then a podcast, then radio, and repeat in reverse; verify only the active player responds or appears in Control Center.
3. Lock the phone and exercise play, pause, seek, next and previous where each content type supports them.
4. Verify podcast resume, automatic queue advance, speed, sleep timer and progress after foreground/background transitions.
5. Exercise Chinese, English and Cantonese searches, direct publisher feeds, official-platform handoffs, radio HLS, saved stations and persistence after relaunch.
6. Capture device logs for any termination, playback failure or background-task issue before changing the source again.

## Definition of done

1. Portable behavior lives in `shared/src/commonMain` and is covered in `commonTest`.
2. Android and iOS platform adapters implement the same use case.
3. Provider authorization and licensing boundaries fail closed on both platforms.
4. Unit, lint/static, and build checks pass for both platforms.
5. Streaming, background playback, system controls and lifecycle behavior are checked on physical Android and iPhone hardware.
6. Any intentional platform difference is recorded in this file.
