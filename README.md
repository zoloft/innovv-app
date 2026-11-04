# INNOVV Open Cam

An open-source Kotlin Multiplatform replacement for the INNOVV K5/K7 dashcam
companion app. The UI is shared between Android and iOS with Compose
Multiplatform; the camera protocol is provided by the sibling
[`innovv-sdk`](../sdk) project.

The app is designed for motorcycle use: dark-only, high-contrast, large touch
targets, deterministic connection handling, and a live view that does not block
the UI thread.

## Status

This is a working v1 scaffold. It builds for Android and compiles for iOS. The
Android build can connect to a real camera through the SDK and stream RTSP with
Media3/ExoPlayer. The iOS shell is present, but RTSP playback is still a VLCKit
integration point; see [Known limitations](#known-limitations).

## Features

- Connected / connecting / disconnected dashboard states.
- Connection status pill, live RTSP surface, front/rear camera selector, REC
  timer, telemetry cards, and quick actions.
- Snapshot, Lock Event (best effort), Pause/Resume preview, and Settings.
- File Gallery with All / Loop / Parked / Protected / Photos filters, grouping
  of front/rear files, multi-select, batch download with MB/s progress, delete,
  and a clip preview with front/rear PiP.
- Full-screen Settings modal with Wi-Fi band, video resolution, loop interval,
  watermark options, G-sensor sensitivity, parking mode, SD format, firmware
  diagnostics, and a sticky Done / Apply footer.
- Pause-before-mutate settings apply: recording is stopped, settings are written
  and saved, then recording is restarted.
- Offline settings inspection from a persistent cache.
- Android Wi-Fi binding through `WifiNetworkSpecifier` +
  `ConnectivityManager.bindProcessToNetwork`.
- Demo mode in debug builds for exploring the connected UI without a camera.

## Project layout

```
app/
├── composeApp/                     # KMP app module (Android + iOS)
│   └── src/
│       ├── commonMain/             # UI, state models, repositories, demo data
│       ├── androidMain/            # Activity, Wi-Fi binder, Media3 player, files
│       ├── iosMain/                # iOS view controller, hotspot binder, files
│       └── commonTest/             # Formatter and state-model tests
├── iosApp/                         # SwiftUI shell + Xcode project
├── gradle/libs.versions.toml
└── settings.gradle.kts
```

The app consumes the SDK as a composite build:

```kotlin
includeBuild("../sdk") {
    dependencySubstitution {
        substitute(module("com.zoloft.innovv:innovv-sdk"))
            .using(project(":innovv-sdk"))
    }
}
```

For a standalone checkout, either keep the SDK next to the app or publish it to
Maven Local:

```bash
cd ../sdk
./gradlew :innovv-sdk:publishToMavenLocal
```

## Build

Requirements:

- JDK 17+ (JDK 21 is used in this workspace).
- Android SDK 37 for the Android target.
- macOS + Xcode 16+ for linking/running the iOS app.

```bash
# Android debug APK
./gradlew :composeApp:assembleDebug

# Android unit tests
./gradlew :composeApp:testDebugUnitTest

# Compile the iOS Kotlin framework (Linux can compile the klib)
./gradlew :composeApp:compileKotlinIosSimulatorArm64
```

The APK is written to
`composeApp/build/outputs/apk/debug/composeApp-debug.apk`.

### iOS

Open `iosApp/iosApp.xcodeproj` in Xcode on macOS. The project has a
`Compile Kotlin Framework` build phase that runs:

```bash
./gradlew :composeApp:embedAndSignAppleFrameworkForXcode
```

Set your development team in `iosApp/Configuration/Config.xcconfig`. The app
requires:

- the Hotspot Configuration entitlement for `NEHotspotConfiguration`;
- `NSLocalNetworkUsageDescription` (already in `Info.plist`);
- the local-networking ATS exception (already in `Info.plist`).

## Demo mode

Debug builds show an **or Explore demo mode** link on the disconnected
dashboard. It uses a deterministic camera simulator so every screen can be
reviewed without hardware. Release builds use the real SDK session only.

## Permissions

Android:

- `INTERNET`, `ACCESS_NETWORK_STATE`, `CHANGE_NETWORK_STATE`;
- `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`;
- `ACCESS_FINE_LOCATION` on Android 12 and below;
- `NEARBY_WIFI_DEVICES` on Android 13+;
- `VIBRATE`.

The app requests the Wi-Fi permission at launch. Cleartext HTTP is allowed only
for `192.168.1.254` through `network_security_config.xml`.

iOS: local network, motion (for the phone G-force fallback), and hotspot
configuration entitlements are declared in the Xcode project/Info.plist.

## Camera behavior notes

- The SDK targets the K5 protocol. K7 support is best-effort through the
  camera's advertised menus and firmware string.
- The app does not log Wi-Fi credentials.
- Lock Event is best effort. The K5 SDK does not expose a typed protect
  command, so the real session reports it as unsupported rather than pretending
  it succeeded.
- Live G-force is not exposed by the K5 SDK. The UI shows "Idle" until a
  platform/camera source is wired in; the design sample is not fabricated.
- Total SD capacity is not exposed by the SDK. The app shows free space when
  only `3017` is available.
- Gallery file-list truncation is surfaced as an error/refresh state; FTP bulk
  sync is out of scope for v1.

## Known limitations

- **iOS RTSP:** `PlatformLiveVideo.ios.kt` is an integration point for
  VLCKit/MobileVLCKit. AVPlayer does not support RTSP. The iOS UI compiles and
  runs, but the live surface remains a placeholder until VLCKit is linked.
- **Background downloads:** downloads are foreground-only in v1.
- **File list truncation:** the camera can truncate large `3015` responses; v1
  shows a retryable error rather than implementing FTP/TFTP.
- **K7:** the SDK and command mapping are K5-first.

## License

MIT. See [LICENSE](LICENSE). Inter is licensed under the SIL Open Font License;
see `composeApp/src/commonMain/composeResources/files/INTER-LICENSE.txt`.
