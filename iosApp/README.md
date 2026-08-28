# OpenGroove for iOS

The iOS application uses native SwiftUI and AVFoundation while consuming the shared Kotlin Multiplatform domain and playback-policy framework.

## Requirements

- Full Xcode with the iOS 17 or newer SDK
- XcodeGen (`brew install xcodegen`)
- JDK 17 for the shared Gradle framework

## Generate and build

```bash
cd iosApp
xcodegen generate
open OpenGroove.xcodeproj
```

Choose an Apple development team in Signing & Capabilities, then run on an iPhone. The Xcode build phase invokes `:shared:embedAndSignAppleFrameworkForXcode` automatically.

Wikimedia Commons discovery works without a credential. Jamendo is optional; pass your own public client ID as the `JAMENDO_CLIENT_ID` Xcode build setting (for example in a private, untracked xcconfig or on the `xcodebuild` command line). Never commit it to the generated project.

The Swift-only radio model and directory tests can run without Xcode:

```bash
cd iosApp
swiftc OpenGroove/Models/RadioStation.swift OpenGroove/Services/RadioDirectory.swift \
  SmokeTests/RadioCoreSmoke.swift -o /tmp/opengroove-radio-smoke
/tmp/opengroove-radio-smoke
```

The native iOS counterpart now implements the same product use cases as Android: license-explicit Wikimedia Commons discovery, optional Jamendo discovery, five official-platform handoffs, local playlists, internet-radio discovery/saved/recent lists, publisher-feed podcasts, subscriptions and Unplayed inbox, and dedicated AVPlayer experiences for music, radio and podcasts. The shared Kotlin framework remains the fail-closed playback-policy authority.

Simulator builds and unit tests do not replace acceptance on a physical iPhone. Streaming, background playback, Control Center/lock-screen commands, interruptions, timers, refresh scheduling and persistence still need to be exercised on the target phone.
