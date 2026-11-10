# ContinuumSync ProGuard rules.
# Compose, AndroidX and kotlinx-coroutines ship their own consumer rules.
# Keep the notification listener and accessibility service entry points.
-keep class dev.continuumsync.bridge.notifications.SyncNotificationListenerService { *; }
-keep class dev.continuumsync.bridge.clipboard.ClipboardAccessibilityService { *; }
-keep class dev.continuumsync.bridge.clipboard.ClipboardTileService { *; }
-keep class dev.continuumsync.bridge.service.SyncForegroundService { *; }
