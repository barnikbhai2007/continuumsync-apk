package dev.continuumsync.bridge.notifications

import android.app.Notification
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.continuumsync.bridge.data.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class SyncNotificationListenerService : NotificationListenerService() {

    private val replyActionsCache = ConcurrentHashMap<String, Notification.Action>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handledRemoteDismissals = mutableSetOf<String>()
    private val handledRemoteReplies = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        scope.launch {
            SyncRepository.snapshot.collect { snapshot ->
                if (snapshot == null) return@collect
                val myRole = SyncRepository.prefs.role
                for (item in snapshot.notifications) {
                    if (item.originDevice != myRole) continue

                    if (item.dismissed) {
                        if (!handledRemoteDismissals.add(item.id)) continue
                        val sbnKey = item.id.substringBeforeLast("@")
                        if (sbnKey.isNotBlank()) {
                            try {
                                cancelNotification(sbnKey)
                            } catch (_: Exception) {
                            }
                        }
                        continue
                    }

                    val reply = item.repliedText
                    val replyBy = item.repliedBy
                    if (!reply.isNullOrBlank() && !replyBy.isNullOrBlank() && replyBy != myRole) {
                        val token = "${item.id}|$replyBy|$reply"
                        if (handledRemoteReplies.add(token)) {
                            val sbnKey = item.id.substringBeforeLast("@")
                            if (sbnKey.isNotBlank()) {
                                try {
                                    handleRemoteInlineReply(sbnKey, reply)
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (activeInstance === this) activeInstance = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        if (sbn.isOngoing && (sbn.notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) return

        val extras = sbn.notification.extras
        if (extras.getBoolean("EXTRA_CONTINUUM_MIRRORED", false)) return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: ""

        val replyAction = sbn.notification.actions?.firstOrNull { action ->
            action.remoteInputs?.isNotEmpty() == true
        }
        if (replyAction != null) {
            replyActionsCache[sbn.key] = replyAction
        }

        val payload = JSONObject()
            .put("id", "${sbn.key}@${sbn.postTime}")
            .put("originDevice", SyncRepository.prefs.role)
            .put("appName", appLabel(sbn.packageName))
            .put("appPackage", sbn.packageName)
            .put("title", title)
            .put("content", text)
            .put("canReply", replyAction != null)

        SyncRepository.postEventAsync("NOTIFICATION_POST", payload)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        replyActionsCache.remove(sbn.key)

        val payload = JSONObject().put("id", "${sbn.key}@${sbn.postTime}")
        SyncRepository.postEventAsync("NOTIFICATION_DISMISS", payload)
    }

    private fun appLabel(pkg: String): String = try {
        val info = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (_: Exception) {
        pkg.substringAfterLast('.')
    }

    fun handleRemoteDismissCommand(sbnKey: String) {
        cancelNotification(sbnKey)
    }

    fun handleRemoteInlineReply(sbnKey: String, replyText: String) {
        val action = replyActionsCache[sbnKey] ?: return
        val remoteInputs = action.remoteInputs ?: return
        val fillInIntent = Intent()
        val resultsBundle = Bundle()
        for (remoteInput in remoteInputs) {
            resultsBundle.putCharSequence(remoteInput.resultKey, replyText)
        }
        android.app.RemoteInput.addResultsToIntent(remoteInputs, fillInIntent, resultsBundle)
        action.actionIntent.send(applicationContext, 0, fillInIntent)
    }

    companion object {
        @Volatile
        private var activeInstance: SyncNotificationListenerService? = null

        fun sendReplyFromThisDevice(sbnKey: String, replyText: String) {
            activeInstance?.handleRemoteInlineReply(sbnKey, replyText)
        }

        fun dismissFromThisDevice(sbnKey: String) {
            activeInstance?.handleRemoteDismissCommand(sbnKey)
        }
    }
}
