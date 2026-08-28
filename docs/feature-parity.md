# OpenGroove feature parity

Every product feature has one shared behavior contract and two platform acceptance gates. A feature is complete only when both applications pass, unless a platform difference is explicitly documented.

| Capability | Shared contract | Android | iOS | Remaining gate |
|---|---|---|---|---|
| Licensed music discovery | Track model, language scopes and fail-closed direct-playback policy | Wikimedia Commons plus optional Jamendo adapter | Wikimedia Commons plus optional Jamendo adapter built and unit-tested on iOS Simulator | Live catalog checks and attribution review on both physical phones |
| Official music handoffs | External-only boundary | YouTube Music, YouTube, Spotify, QQ Music and NetEase | Same five official HTTPS search handoffs built and URL-tested | Open each installed-app/web fallback on both physical phones |
| Licensed music playback | HTTPS stream plus public license evidence | Media3 service and seek controls | AVPlayer, seek and system commands built on iOS Simulator | Physical background, interruption and system-control checks on both phones |
| Local playlists | Track identity and provider/license metadata | Room persistence | Local Codable persistence built and unit-tested on iOS Simulator | Persistence/lifecycle checks on both physical phones |
| Internet radio discovery | Station validity and browse vocabulary | Implemented | Built and unit-tested on iOS Simulator | Physical iPhone search and browsing |
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

## Current iOS build evidence

- Xcode 26.6 built the Debug application and passed tests for an iPhone 15 simulator running iOS 26.5.
- The Xcode build phase linked the Kotlin `OpenGrooveShared` framework, and Swift resolved the shared playback-policy API.
- Thirteen Swift tests passed in the iOS Simulator test bundle: five music catalog/handoff/playlist/policy tests, five podcast catalog/feed/persistence/control-contract tests, and three radio directory/recent-store tests.
- The built application installed and launched in the simulator, and the initial SwiftUI shell rendered without a crash.
- Discover, official handoffs, Library/create-playlist, Podcasts and direct publisher-RSS screens were visually checked in the iPhone 15 simulator.
- This is simulator evidence only. Signing, installation, playback, background behavior, system controls and lifecycle acceptance on the target physical iPhone 15 remain open.

## Pre-device hardening evidence (2026-08-28)

- The ARM64 iPhoneOS application and all 16 Swift test functions compile and link with `build-for-testing`; no simulator was used for this check.
- Xcode static analysis passes for the generic physical-iOS target, and application validation no longer reports an incomplete iPad orientation declaration.
- A macOS executable compiled directly from the production podcast model, parser and store sources passed duration validation, nested RSS text parsing, bounded-cache and persistence smoke checks.
- Player end notifications are scoped to their owning `AVPlayerItem`; player switches also re-establish the correct audio-session mode and remote-command ownership.
- These checks prove source and device-target build readiness only. Physical playback, lifecycle and operating-system integration remain open.

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
