package dev.continuumsync.bridge.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import dev.continuumsync.bridge.MainActivity
import dev.continuumsync.bridge.R
import dev.continuumsync.bridge.calls.CallSyncController
import dev.continuumsync.bridge.data.CallSnapshot
import dev.continuumsync.bridge.data.RoomSnapshot
import dev.continuumsync.bridge.data.SyncNotification
import dev.continuumsync.bridge.data.SyncRepository
import dev.continuumsync.bridge.data.roleLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SyncForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null
    private lateinit var callController: CallSyncController

    private val seenNotifications = mutableSetOf<String>()
    private var primed = false
    private var handledCallKey = ""
    private var lastPeerCount = -1
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        createChannels()
        callController = CallSyncController(this)
        callController.registerCallStateObserver()
        startOrUpdateForeground(0)

        observeJob = scope.launch {
            SyncRepository.snapshot.collect { snapshot ->
                if (snapshot != null) onSnapshot(snapshot)
            }
        }
        scope.launch {
            SyncRepository.configVersion.collect {
                startOrUpdateForeground(lastPeerCount.coerceAtLeast(0))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startOrUpdateForeground(lastPeerCount.coerceAtLeast(0))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observeJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun onSnapshot(snapshot: RoomSnapshot) {
        if (snapshot.connectedPeers != lastPeerCount) {
            startOrUpdateForeground(snapshot.connectedPeers)
        }
        mirrorNotifications(snapshot)
        handleRemoteCall(snapshot.activeCall)
    }

    private fun mirrorNotifications(snapshot: RoomSnapshot) {
        val myRole = SyncRepository.prefs.role
        val active = snapshot.notifications.filter { !it.dismissed && it.originDevice != myRole }
        val activeIds = active.map { it.id }.toSet()

        if (!primed) {
            seenNotifications.addAll(activeIds)
            primed = true
        } else {
            for (item in active) {
                if (seenNotifications.add(item.id)) {
                    showMirrorNotification(item)
                }
            }
        }

        val stale = seenNotifications - activeIds
        for (id in stale) {
            NotificationManagerCompat.from(this).cancel(id.hashCode())
            seenNotifications.remove(id)
        }
    }

    private fun showMirrorNotification(item: SyncNotification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pending = PendingIntent.getActivity(
            this,
            item.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body = if (item.content.isBlank()) item.title else "${item.title}\n${item.content}"
        val notification = NotificationCompat.Builder(this, CHANNEL_MIRROR)
            .setSmallIcon(R.drawable.ic_stat_sync)
            .setContentTitle(item.appName)
            .setContentText(item.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(this).notify(item.id.hashCode(), notification)
    }

    private fun handleRemoteCall(call: CallSnapshot) {
        val myRole = SyncRepository.prefs.role
        if (call.originDevice != myRole) return
        if (call.status == "idle") return

        val key = "${call.callId}:${call.status}:${call.actionTakenBy ?: "-"}"
        if (key == handledCallKey) return

        when {
            call.status == "active-bthfp-routed" &&
                call.actionTakenBy != null && call.actionTakenBy != myRole -> {
                handledCallKey = key
                callController.answerCall()
            }
            call.status == "active-on-origin" && call.actionTakenBy == myRole -> {
                handledCallKey = key
                callController.answerCall()
            }
            call.status == "rejected" &&
                call.actionTakenBy != null && call.actionTakenBy != myRole -> {
                handledCallKey = key
                callController.endCall()
            }
            call.status == "rejected" && call.actionTakenBy == myRole -> {
                handledCallKey = key
                callController.endCall()
            }
        }
    }

    private fun startOrUpdateForeground(peers: Int) {
        lastPeerCount = peers
        val room = SyncRepository.prefs.room
        val role = roleLabel(SyncRepository.prefs.role)
        val status = if (peers >= 2) {
            "$peers devices linked · $role"
        } else {
            "Waiting for the other phone · $role"
        }

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_sync)
            .setContentTitle("Room $room")
            .setContentText(status)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent)
            .build()

        if (!foregroundStarted) {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            foregroundStarted = true
        } else {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        val status = NotificationChannel(
            CHANNEL_STATUS,
            "Sync status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent dual-phone connection status"
            setShowBadge(false)
        }
        val mirror = NotificationChannel(
            CHANNEL_MIRROR,
            "Mirrored alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications mirrored from your paired phone"
            enableVibration(true)
        }
        manager.createNotificationChannel(status)
        manager.createNotificationChannel(mirror)
    }

    companion object {
        const val CHANNEL_STATUS = "continuum_status"
        const val CHANNEL_MIRROR = "continuum_mirror"
        const val NOTIFICATION_ID = 4201
    }
}
