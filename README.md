# OpenDash for INNOVV

[![CI](https://github.com/zoloft/innovv-app/actions/workflows/ci.yml/badge.svg)](https://github.com/zoloft/innovv-app/actions/workflows/ci.yml)
[![Release](https://github.com/zoloft/innovv-app/actions/workflows/release.yml/badge.svg)](https://github.com/zoloft/innovv-app/actions/workflows/release.yml)

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
- Automatic camera discovery: probes the current Wi-Fi for an INNOVV camera,
  scans for INNOVV SSIDs with dynamic suffixes (for example
  `5G_INNOVV_K5_...`), and presents a network picker when discovery cannot
  choose automatically.
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
- Debug request/response traffic logging, enabled by default in debug builds and
  controlled from Settings → Developer.

## Project layout

```
app/
├── androidApp/                     # Android application entry point (AGP 9)
│   └── src/main/                   # MainActivity, Application, manifest, resources
├── composeApp/                     # Shared KMP library (Android + iOS)
│   └── src/
│       ├── commonMain/             # UI, state models, repositories, demo data
│       ├── androidMain/            # Wi-Fi binder, Media3 player, file storage
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
- macOS + Xcode 26+ with the iOS 26 SDK for linking/running the iOS app.

```bash
# Android debug APK
./gradlew :androidApp:assembleDebug

# Shared KMP tests (Android host target)
./gradlew :composeApp:testAndroidHostTest

# Android lint
./gradlew :androidApp:lintDebug

# Compile the iOS Kotlin framework (Linux can compile the klib)
./gradlew :composeApp:compileKotlinIosArm64
```

The APK is written to
`androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

The project uses the AGP 9 structure: `composeApp` is a KMP library using
`com.android.kotlin.multiplatform.library`, and `androidApp` is a pure Android
application module. This avoids the Kotlin/AGP configuration conflict that
produces "Kotlin not configured" in Android Studio.

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
- `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION` on all Android versions;
- `NEARBY_WIFI_DEVICES` on Android 13+;
- `VIBRATE`.

Precise location is still required by Android for `WifiManager.startScan()` and
`getScanResults()` even on Android 13+, so the app requests it at launch. Without
it, the app can connect to the current network but cannot discover other
INNOVV SSIDs or read the current SSID. Cleartext HTTP is allowed only for
`192.168.1.254` through `network_security_config.xml`.

iOS: local network, motion (for the phone G-force fallback), and hotspot
configuration entitlements are declared in the Xcode project/Info.plist.

## Camera discovery

When you tap **Tap to connect**, the app:

1. Binds to the current Wi-Fi network and sends a heartbeat to
   `192.168.1.254`. If an INNOVV camera answers, it is used even when the SSID
   does not match a hard-coded name.
2. If no camera answers, scans the phone's Wi-Fi networks for SSIDs containing
   `INNOVV` (case-insensitive), so names such as `5G_INNOVV_K5_1234` work.
3. Connects automatically when exactly one INNOVV network is found.
4. Shows a network picker when there are multiple INNOVV networks, or when none
   are found. Secured networks prompt for a password before connecting.
5. Offers **Open Wi-Fi settings** when scanning is unavailable or the network
   must be joined manually (iOS cannot return arbitrary Wi-Fi scan results).

## Debug traffic logging

Debug builds log every SDK HTTP request, response body, streamed byte count, and
camera event. Logging is enabled by default and can be disabled immediately from
**Settings → Developer → Request logging**. The last 200 lines are available via
**View debug log**; the complete log is stored under the app-private
`INNOVV/debug/traffic.log` file and rotated at 2 MB.

Production builds start with logging disabled and do not show the Developer
section. Wi-Fi credential URLs and credential response bodies are redacted by
the SDK before they reach the logger.

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
- **iOS Wi-Fi scanning:** iOS does not expose arbitrary Wi-Fi scan results to
  third-party apps; use **Open Wi-Fi settings** and join the camera network
  manually.
- **K7:** the SDK and command mapping are K5-first.

## CI and releases

Two GitHub Actions workflows are included:

- `.github/workflows/ci.yml` runs on pushes to `main` and pull requests. It builds
  the Android debug APK, runs unit tests and lint, compiles the iOS Kotlin/Native
  framework, and builds an unsigned iOS device app.
- `.github/workflows/release.yml` runs when a tag matching `v*` is pushed. It builds
  the Android release APK/AAB, an installable debug APK, and an unsigned iOS device
  app, then publishes them to a GitHub Release.

Create a release with:

```bash
git tag v0.1.0
git push origin v0.1.0
```

The tag version becomes `versionName` (`v0.1.0` → `0.1.0`); the GitHub run number is
used as the Android `versionCode`.

### Android signing

The release workflow produces an unsigned release APK/AAB unless these repository
secrets are configured:

| Secret | Purpose |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded release keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias |
| `ANDROID_KEY_PASSWORD` | Key password |

A debug-signed APK is always included for testing.

### iOS signing and packaging

The workflow always attaches an unsigned iOS device `.app`. When Apple signing
secrets are configured, it also builds and exports a signed device IPA:

| Secret | Purpose |
|---|---|
| `IOS_CERTIFICATE_BASE64` | Base64-encoded Apple Distribution `.p12` |
| `IOS_CERTIFICATE_PASSWORD` | Certificate password |
| `IOS_PROVISIONING_PROFILE_BASE64` | Base64-encoded `.mobileprovision` |
| `APPLE_TEAM_ID` | Apple Developer Team ID |
| `IOS_PROVISIONING_PROFILE_SPECIFIER` | Optional profile name override |
| `IOS_CODE_SIGN_IDENTITY` | Optional identity override (default `Apple Distribution`) |
| `IOS_EXPORT_METHOD` | Optional export method (default `app-store-connect`) |

The provisioning profile must include the Hotspot Configuration entitlement used
by `NEHotspotConfiguration`. Unsigned iOS artifacts remain useful for inspection
and local signing, but are not installable on a physical device.

Both workflows clone `https://github.com/zoloft/innovv-sdk.git` into the sibling
`../sdk` directory expected by the Gradle composite build. If the SDK repository is
private, add a checkout token or change the clone URL.

## License

MIT. See [LICENSE](LICENSE). Inter is licensed under the SIL Open Font License;
see `composeApp/src/commonMain/composeResources/files/INTER-LICENSE.txt`.
