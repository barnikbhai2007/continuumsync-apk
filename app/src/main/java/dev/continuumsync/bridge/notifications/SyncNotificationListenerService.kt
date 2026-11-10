package dev.continuumsync.bridge.notifications

import android.app.Notification
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.continuumsync.bridge.data.SyncRepository
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class SyncNotificationListenerService : NotificationListenerService() {

    private val replyActionsCache = ConcurrentHashMap<String, Notification.Action>()

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
            .put("title", title)
            .put("content", text)

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
}
