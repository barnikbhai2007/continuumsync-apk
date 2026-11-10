# ContinuumSync for Android

A native two-phone bridge for [ContinuumSync](https://github.com/barnikbhai2007/notificationsync). Link two Android phones and keep notifications, the clipboard, and call controls in sync in real time.

## Download

**[Download the latest APK](https://github.com/barnikbhai2007/continuumsync-apk/releases/latest)**

The APK is built automatically by GitHub Actions on every push to `main`. It is debug-signed, so Android will ask you to allow installs from your browser or file manager.

- Minimum: Android 9 (API 28)
- Target: Android 15 (API 35)

## What the app does

- **Pairing room** — both phones join the same room (default `482-910`). One phone is Phone A, the other Phone B.
- **Live connection status** — the dashboard shows when the peer phone is actually linked, not just whether this phone has permissions.
- **Notification mirroring** — notifications from the other phone appear in a live feed and as Android notifications. Dismissing one clears it on both phones.
- **Shared clipboard** — copy on either phone and the text appears on the other. A Quick Settings tile pushes the clipboard manually.
- **Call controls** — incoming calls show up with Answer, Reject, and End actions that are relayed to the phone that owns the call.
- **Foreground service** — the bridge keeps running when the app is in the background, with a persistent status notification.

## Set it up

1. Install the APK on both phones.
2. On the phone running the ContinuumSync web app, make sure the sync server is reachable on your Wi-Fi (default port `3100`).
3. Open the app and tap **Settings** to set:
   - **Sync server URL** — for example `http://192.168.1.20:3100`
   - **Room code** — the same room on both phones
   - **This phone is** — Phone A on one, Phone B on the other
4. Grant the three device-access permissions:
   - Notification listener access (mirror and dismiss alerts)
   - Accessibility service (clipboard capture)
   - Phone and Bluetooth permissions (call control)
5. The status pill turns **Linked** when both phones are connected. Use **Test alert**, **Clipboard**, and **Test call** to verify the bridge.

> Both phones need to reach the same sync server. On a home network, use the computer's LAN IP rather than `localhost`.

## Build from source

The build runs on GitHub Actions using JDK 17 and Gradle 8.9:

```bash
gradle assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Project layout

```
app/src/main/java/dev/continuumsync/bridge/
├── data/          sync repository, SSE stream, preferences
├── notifications/ notification listener service
├── clipboard/     accessibility clipboard service + Quick Settings tile
├── calls/         call state observer and answer/reject control
├── service/       persistent foreground sync service
└── ui/            Jetpack Compose dashboard
```
