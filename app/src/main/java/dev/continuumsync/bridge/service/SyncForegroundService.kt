package dev.continuumsync.bridge.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.ServiceCompat
import dev.continuumsync.bridge.MainActivity
import dev.continuumsync.bridge.R
import dev.continuumsync.bridge.calls.CallActionReceiver
import dev.continuumsync.bridge.calls.CallSyncController
import dev.continuumsync.bridge.clipboard.ClipboardBridge
import dev.continuumsync.bridge.data.CallSnapshot
import dev.continuumsync.bridge.data.RoomSnapshot
import dev.continuumsync.bridge.data.SyncNotification
import dev.continuumsync.bridge.data.SyncRepository
import dev.continuumsync.bridge.data.roleLabel
import dev.continuumsync.bridge.notifications.MirrorDismissReceiver
import dev.continuumsync.bridge.ui.IncomingCallActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class SyncForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null
    private lateinit var callController: CallSyncController

    private val seenNotifications = mutableSetOf<String>()
    private val appIconCache = mutableMapOf<String, Bitmap?>()
    private var primed = false
    private var handledActionKey = ""
    private var lastPeerCount = -1
    private var foregroundStarted = false
    private var lastCallKey = ""
    private var lastMissedCallId = ""

    override fun onCreate() {
        super.onCreate()
        createChannels()
        ClipboardBridge.init(this)
        ClipboardBridge.start(this)
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
        callController.unregister()
        scope.cancel()
        super.onDestroy()
    }

    private fun onSnapshot(snapshot: RoomSnapshot) {
        callController.registerCallStateObserver()
        if (snapshot.connectedPeers != lastPeerCount) {
            startOrUpdateForeground(snapshot.connectedPeers)
        }
        mirrorNotifications(snapshot)
        handleRemoteCall(snapshot.activeCall)
        syncCallUi(snapshot.activeCall)
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

        val whenMillis = timestampFor(item.timestamp)
        val openIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPending = PendingIntent.getActivity(
            this,
            item.id.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(this, MirrorDismissReceiver::class.java).apply {
            action = MirrorDismissReceiver.ACTION_DISMISS
            putExtra(MirrorDismissReceiver.EXTRA_ID, item.id)
        }
        val dismissPending = PendingIntent.getBroadcast(
            this,
            item.id.hashCode() xor 0x5A5A,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sender = Person.Builder()
            .setName(item.title.ifBlank { item.appName })
            .setImportant(true)
            .build()
        val me = Person.Builder().setName("ContinuumSync").build()
        val style = NotificationCompat.MessagingStyle(me)
            .setConversationTitle(item.appName)
            .addMessage(
                item.content.ifBlank { item.title },
                whenMillis,
                sender
            )

        val builder = NotificationCompat.Builder(this, CHANNEL_MIRROR)
            .setSmallIcon(R.drawable.ic_stat_sync)
            .setStyle(style)
            .setContentIntent(openPending)
            .setDeleteIntent(dismissPending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(whenMillis)
            .setShowWhen(true)
            .setOnlyAlertOnce(false)
            .addAction(
                R.drawable.ic_stat_sync,
                "Dismiss on both phones",
                dismissPending
            )

        appIconFor(item.appPackage)?.let { builder.setLargeIcon(it) }

        NotificationManagerCompat.from(this).notify(item.id.hashCode(), builder.build())
    }

    private fun appIconFor(packageName: String): Bitmap? {
        if (packageName.isBlank()) return null
        if (appIconCache.containsKey(packageName)) return appIconCache[packageName]
        val bitmap = try {
            val drawable = packageManager.getApplicationIcon(packageName)
            val size = (64 * resources.displayMetrics.density).toInt().coerceIn(72, 192)
            Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { target ->
                val canvas = Canvas(target)
                drawable.setBounds(0, 0, size, size)
                drawable.draw(canvas)
            }
        } catch (_: Exception) {
            null
        }
        appIconCache[packageName] = bitmap
        return bitmap
    }

    private fun syncCallUi(call: CallSnapshot) {
        val myRole = SyncRepository.prefs.role

        if (call.status == "idle") {
            cancelCallNotification()
            lastCallKey = ""
            return
        }

        // The source phone already has the system dialer in charge of the call.
        if (call.originDevice == myRole) {
            cancelCallNotification()
            return
        }

        if (call.isFinished) {
            if (call.status == "missed" && call.callId != lastMissedCallId) {
                lastMissedCallId = call.callId
                showMissedCall(call)
            } else {
                cancelCallNotification()
            }
            lastCallKey = ""
            return
        }

        if (!call.isRinging && !call.isActive) {
            cancelCallNotification()
            return
        }

        val key = "${call.callId}:${call.status}:${call.callerName}:${call.callerNumber}"
        if (key == lastCallKey) return
        lastCallKey = key
        showCallNotification(call)
    }

    private fun showCallNotification(call: CallSnapshot) {
        if (!canPostNotifications()) return
        val displayName = when {
            call.callerName.isNotBlank() && call.callerName != "Unknown caller" -> call.callerName
            call.callerNumber.isNotBlank() -> call.callerNumber
            else -> "Unknown caller"
        }
        val isRinging = call.isRinging
        val personBuilder = Person.Builder()
            .setName(displayName)
            .setImportant(true)
        if (call.callerNumber.isNotBlank()) {
            personBuilder.setUri("tel:${call.callerNumber}")
        }
        val person = personBuilder.build()

        val openIntent = Intent(this, IncomingCallActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            putExtra(IncomingCallActivity.EXTRA_CALL_ID, call.callId)
        }
        val openPending = PendingIntent.getActivity(
            this,
            ("open:" + call.callId).hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val answerPending = callActionPending(call, CallActionReceiver.ACTION_ANSWER)
        val declinePending = callActionPending(call, CallActionReceiver.ACTION_DECLINE)
        val endPending = callActionPending(call, CallActionReceiver.ACTION_END)

        val builder = NotificationCompat.Builder(this, CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentIntent(openPending)
            .setFullScreenIntent(openPending, isRinging)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setColor(0xFF2BD9A8.toInt())
            .setContentTitle(displayName)
            .setContentText(
                if (isRinging) {
                    "Incoming call · ${roleLabel(call.originDevice)}"
                } else {
                    "Call in progress · ${roleLabel(call.originDevice)}"
                }
            )

        if (call.callerNumber.isNotBlank() && call.callerNumber != displayName) {
            builder.setSubText(call.callerNumber)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setStyle(
                if (isRinging) {
                    NotificationCompat.CallStyle.forIncomingCall(
                        person,
                        declinePending,
                        answerPending
                    )
                } else {
                    NotificationCompat.CallStyle.forOngoingCall(person, endPending)
                }
            )
        } else {
            if (isRinging) {
                builder.addAction(R.drawable.ic_stat_call, "Decline", declinePending)
                builder.addAction(R.drawable.ic_stat_call, "Answer", answerPending)
            } else {
                builder.addAction(R.drawable.ic_stat_call, "End call", endPending)
            }
        }

        NotificationManagerCompat.from(this).notify(CALL_NOTIFICATION_ID, builder.build())
    }

    private fun callActionPending(
        call: CallSnapshot,
        action: String
    ): PendingIntent {
        val intent = Intent(this, CallActionReceiver::class.java).apply {
            this.action = action
            putExtra(CallActionReceiver.EXTRA_CALL_ID, call.callId)
            putExtra(CallActionReceiver.EXTRA_ORIGIN, call.originDevice)
            putExtra(CallActionReceiver.EXTRA_NAME, call.callerName)
            putExtra(CallActionReceiver.EXTRA_NUMBER, call.callerNumber)
        }
        return PendingIntent.getBroadcast(
            this,
            (action + call.callId).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showMissedCall(call: CallSnapshot) {
        if (!canPostNotifications()) return
        val displayName = when {
            call.callerName.isNotBlank() && call.callerName != "Unknown caller" -> call.callerName
            call.callerNumber.isNotBlank() -> call.callerNumber
            else -> "Unknown caller"
        }
        val openIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPending = PendingIntent.getActivity(
            this,
            ("missed:" + call.callId).hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_MIRROR)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle("Missed call")
            .setContentText("$displayName · on ${roleLabel(call.originDevice)}")
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
        call.callerNumber.takeIf { it.isNotBlank() && it != displayName }?.let {
            builder.setSubText(it)
        }
        NotificationManagerCompat.from(this).notify(CALL_NOTIFICATION_ID, builder.build())
    }

    private fun cancelCallNotification() {
        NotificationManagerCompat.from(this).cancel(CALL_NOTIFICATION_ID)
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    private fun handleRemoteCall(call: CallSnapshot) {
        val myRole = SyncRepository.prefs.role
        if (call.originDevice != myRole) return
        if (call.status == "idle") return

        val actionBy = call.actionTakenBy ?: return
        if (actionBy == myRole) return

        when (call.status) {
            "answer-requested" -> {
                val key = "${call.callId}:answer"
                if (handledActionKey != key) {
                    handledActionKey = key
                    callController.answerCall()
                }
            }
            "rejected" -> {
                val key = "${call.callId}:reject"
                if (handledActionKey != key) {
                    handledActionKey = key
                    callController.endCall(reject = true)
                }
            }
            "ended" -> {
                val key = "${call.callId}:end"
                if (handledActionKey != key) {
                    handledActionKey = key
                    callController.endCall()
                }
            }
        }
    }

    private fun startOrUpdateForeground(peers: Int) {
        lastPeerCount = peers
        val room = SyncRepository.prefs.room
        val role = roleLabel(SyncRepository.prefs.role)
        val status = if (peers >= 2) {
            "$peers phones linked · $role"
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
            description = "Persistent two-phone connection status"
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
        val calls = NotificationChannel(
            CHANNEL_CALLS,
            "Incoming calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Incoming calls relayed from your paired phone"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 900, 500, 900)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setShowBadge(true)
        }
        manager.createNotificationChannel(status)
        manager.createNotificationChannel(mirror)
        manager.createNotificationChannel(calls)
    }

    private fun timestampFor(raw: String): Long {
        return try {
            val time = LocalTime.parse(raw)
            LocalDate.now()
                .atTime(time)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    companion object {
        const val CHANNEL_STATUS = "continuum_status"
        const val CHANNEL_MIRROR = "continuum_mirror"
        const val CHANNEL_CALLS = "continuum_calls_v2"
        const val NOTIFICATION_ID = 4201
        const val CALL_NOTIFICATION_ID = 4202
    }
}
