# ContinuumSync Android Bridge

Native Android companion for the ContinuumSync dual-phone sync studio.

This repository builds a debug APK automatically with GitHub Actions.

## Download the APK

1. Open the **Actions** tab.
2. Select the latest **Build ContinuumSync APK** run.
3. Download the `ContinuumSync-Android-APK` artifact.
4. Unzip it and install `app-debug.apk` on your Android phone.

## Install on the phone

1. Copy `app-debug.apk` to the phone (or use `adb install -r app-debug.apk`).
2. Allow "Install unknown apps" for your file manager when prompted.
3. Open **ContinuumSync**.
4. Grant the three permission toggles shown in the app:
   - Notification listener access
   - Accessibility clipboard sync
   - Call answer/reject permissions

Minimum Android version: 9 (API 28). Target: Android 15 (API 35).

## Build locally

```bash
gradle assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
