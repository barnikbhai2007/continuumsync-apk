# ContinuumSync for Android

A native two-phone bridge for [ContinuumSync](https://github.com/barnikbhai2007/notificationsync). Link two Android phones and keep notifications, the clipboard, and call controls in sync in real time.

## Download

**[Download the latest APK](https://github.com/barnikbhai2007/continuumsync-apk/releases/latest)**

The APK is built automatically by GitHub Actions on every push to `main`. From 2.0.1 onward it is signed with a stable project key, so newer builds install over the previous one. Android will ask you to allow installs from your browser or file manager.

- Minimum: Android 9 (API 28)
- Target: Android 15 (API 35)
- Size: about 2 MB

## What the app does

- **Phone-hosted pairing** — one phone hosts the room itself and the other joins it directly over the same Wi-Fi. No PC or web server required.
- **Automatic discovery** — Phone B finds Phone A on the local network by itself. You can also type the host address manually.
- **Pairing room** — both phones use the same room (default `482-910`). One phone is Phone A (host), the other Phone B (join).
- **Live connection status** — the dashboard shows when the peer phone is actually linked, not just whether this phone has permissions.
- **Notification mirroring** — notifications from the other phone appear in a live feed and as Android notifications. Dismissing one clears it on both phones.
- **Shared clipboard** — copy on either phone and the text appears on the other. A Quick Settings tile pushes the clipboard manually.
- **Call controls** — incoming calls show up with Answer, Reject, and End actions that are relayed to the phone that owns the call.
- **Foreground service** — the bridge keeps running when the app is in the background, with a persistent status notification.

## Set it up

1. Install the APK on both phones and connect both to the same Wi-Fi network.
2. Open the app on **Phone A** and leave it on the default role. Phone A hosts the room automatically and shows a host address such as `http://192.168.1.23:3100`.
3. On **Phone B**, tap **Switch to Phone B**. It finds Phone A automatically over Wi-Fi. If it does not, tap **Scan** or enter the host address in **Settings**.
4. Grant the three device-access permissions on both phones:
   - Notification listener access (mirror and dismiss alerts)
   - Accessibility service (clipboard capture)
   - Phone and Bluetooth permissions (call control)
5. The status pill turns **Linked** when both phones are connected. Use **Test alert**, **Clipboard**, and **Test call** to verify the bridge.

> Both phones must be on the same Wi-Fi network, and Phone A must be open or running its foreground service. Networks that isolate clients (guest Wi-Fi, some routers) block phone-to-phone traffic; use the normal Wi-Fi network or a hotspot instead.

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
