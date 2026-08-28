# OpenGroove feature parity

Every product feature has one shared behavior contract and two platform acceptance gates. A feature is complete only when both applications pass, unless a platform difference is explicitly documented.

| Capability | Shared contract | Android | iOS | Remaining gate |
|---|---|---|---|---|
| Licensed music discovery | Models and direct-playback policy | Implemented | Shell only | iOS catalog and official handoffs |
| Internet radio discovery | Station validity and browse vocabulary | Implemented | Built and unit-tested on iOS Simulator | Physical iPhone search and browsing |
| Radio playback | Public HTTP(S) stream boundary | Media3 service | AVPlayer and system commands built on iOS Simulator | Physical iPhone background, controls and HLS |
| Saved stations | Station identity | Room | Local Codable store built on iOS Simulator | Physical persistence and cross-platform behavior review |
| Podcasts | Models, feed identity and search matching | Implemented | Shell only | iOS feed, library and playback |
| Theme | Product design tokens | System light/dark | System light/dark; simulator shell visually checked | Physical light/dark comparison |

## Current iOS build evidence

- Xcode 26.6 built the Debug application and passed tests for an iPhone 15 simulator running iOS 26.5.
- The Xcode build phase linked the Kotlin `OpenGrooveShared` framework, and Swift resolved the shared playback-policy API.
- Both Swift radio-directory tests passed in the iOS Simulator test bundle.
- The built application installed and launched in the simulator, and the initial SwiftUI shell rendered without a crash.
- This is simulator evidence only. Signing, installation, playback, background behavior, system controls and lifecycle acceptance on the target physical iPhone 15 remain open.

## Definition of done

1. Portable behavior lives in `shared/src/commonMain` and is covered in `commonTest`.
2. Android and iOS platform adapters implement the same use case.
3. Provider authorization and licensing boundaries fail closed on both platforms.
4. Unit, lint/static, and build checks pass for both platforms.
5. Streaming, background playback, system controls and lifecycle behavior are checked on physical Android and iPhone hardware.
6. Any intentional platform difference is recorded in this file.
