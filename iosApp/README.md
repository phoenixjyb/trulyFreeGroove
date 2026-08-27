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

The Swift-only radio model and directory tests can run without Xcode:

```bash
cd iosApp
swiftc OpenGroove/Models/RadioStation.swift OpenGroove/Services/RadioDirectory.swift \
  SmokeTests/RadioCoreSmoke.swift -o /tmp/opengroove-radio-smoke
/tmp/opengroove-radio-smoke
```

The first parity slice contains Radio Browser search, working-stream filtering, saved stations, AVPlayer/HLS playback, background audio configuration, a dedicated station player, previous/next switching, and Control Center/lock-screen commands. Music and podcast tabs are deliberately marked as the next parity slices rather than presenting incomplete playback.
