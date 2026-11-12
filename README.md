# ContinuumSync for Android

A native two-device bridge for [ContinuumSync](https://github.com/barnikbhai2007/notificationsync). Link two Android devices and keep notifications, the clipboard, and call controls in sync in real time.

## Download

**[Download the latest APK](https://github.com/barnikbhai2007/continuumsync-apk/releases/latest)**

The APK is built automatically by GitHub Actions on every push to `main`. From 2.0.1 onward it is signed with a stable project key, so newer builds install over the previous one. Android will ask you to allow installs from your browser or file manager.

- Current: 2.2.0 (versionCode 6)
- Minimum: Android 9 (API 28)
- Target: Android 15 (API 35)
- Size: about 3.1 MB

## What the app does

- **Scan-once QR pairing** — the hosting device shows a QR code on the Devices tab; the other scans it and connects directly over Wi-Fi. Pairing links and a typed code are also supported.
- **Phone-hosted pairing** — one device hosts the room itself and the other joins it directly over the same Wi-Fi. No PC or web server required.
- **Automatic discovery** — the joining device can still find the host by itself, and the host address can be typed manually.
- **Pairing room** — both devices use the same room (default `482-910`). One is the host, the other joins.
- **Live connection status** — the Alerts header shows when the peer device is actually linked, not just whether this device has permissions.
- **Notification feed** — alerts appear in a searchable feed with device and app filters and a detail sheet for every notification.
- **Notification mirroring** — notifications from the other device appear in the live feed and as Android notifications, with the source app icon. Dismissing one clears it on both devices.
- **Remote replies** — reply to a mirrored notification from the other device wherever the source app supports inline replies.
- **Shared clipboard** — copy on either device and the text appears on the other within a second. Works in the background on Android 10+ through a brief focus overlay ("Display over other apps").
- **Incoming calls** — the device without the SIM rings full-screen with the caller's name and number, plus working Answer and Decline buttons that control the real call on the SIM device.
- **Tablet ready** — works on the OnePlus Pad 2 and other tablets; SIM-only permissions and call control are hidden when there is no telephony.
- **Foreground service** — the bridge keeps running when the app is in the background, with a persistent status notification.

## Set it up

1. Install the APK on both devices and connect both to the same Wi-Fi network.
2. Open **Devices** on the device that should host. It shows a QR code, a pairing code, and the host address (for example `http://192.168.1.23:3100`).
3. On the other device open **Devices → Scan QR code** and scan the code. It connects directly to the host over Wi-Fi. You can also share the pairing link or type the code under **Enter code**.
4. Grant the device-access permissions on both devices:
   - Notification listener access (mirror, reply and dismiss alerts)
   - **Display over other apps** (instant clipboard sync on Android 10+)
   - Accessibility service (optional clipboard backup)
   - Phone, call log, and contact permissions (only on the device with the SIM — caller name and call control)
5. The status pill turns **Linked** when both devices are connected. Use **Test alert**, **Clipboard**, and **Test call** under Settings to verify the bridge.

> Both devices must be on the same Wi-Fi network, and the host must stay open or running its foreground service. Networks that isolate clients (guest Wi-Fi, some routers) block device-to-device traffic; use the normal Wi-Fi network or a hotspot instead.

> **Clipboard note:** Android 10+ blocks background clipboard access for every app, including accessibility services. ContinuumSync shows a 2×2 px focusable overlay for ~220 ms each time it reads or writes the clipboard, which requires the "Display over other apps" permission on both devices.

> **Call note:** call audio stays on the device that owns the SIM. The other device mirrors the caller and provides Answer, Decline and End controls.

## Build from source

The build runs on GitHub Actions using JDK 17 and Gradle 8.9:

```bash
gradle assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`.

## Project layout

```
app/src/main/java/dev/continuumsync/bridge/
├── data/          sync repository, SSE stream, preferences
├── host/          embedded HTTP/SSE server + Wi-Fi discovery
├── notifications/ notification listener service
├── clipboard/     accessibility clipboard service + Quick Settings tile
├── calls/         call state observer and answer/reject control
├── service/       persistent foreground sync service
└── ui/            Jetpack Compose dashboard
```
