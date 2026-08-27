# OpenGroove feature parity

Every product feature has one shared behavior contract and two platform acceptance gates. A feature is complete only when both applications pass, unless a platform difference is explicitly documented.

| Capability | Shared contract | Android | iOS | Remaining gate |
|---|---|---|---|---|
| Licensed music discovery | Models and direct-playback policy | Implemented | Shell only | iOS catalog and official handoffs |
| Internet radio discovery | Station validity and browse vocabulary | Implemented | Implemented in source | Xcode build and physical iPhone search |
| Radio playback | Public HTTP(S) stream boundary | Media3 service | AVPlayer and system commands in source | Xcode build and physical iPhone background/HLS |
| Saved stations | Station identity | Room | Local Codable store in source | Xcode build and cross-platform behavior review |
| Podcasts | Models, feed identity and search matching | Implemented | Shell only | iOS feed, library and playback |
| Theme | Product design tokens | System light/dark | System light/dark | Visual comparison |

## Definition of done

1. Portable behavior lives in `shared/src/commonMain` and is covered in `commonTest`.
2. Android and iOS platform adapters implement the same use case.
3. Provider authorization and licensing boundaries fail closed on both platforms.
4. Unit, lint/static, and build checks pass for both platforms.
5. Streaming, background playback, system controls and lifecycle behavior are checked on physical Android and iPhone hardware.
6. Any intentional platform difference is recorded in this file.
