package dev.continuumsync.bridge.notifications

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.continuumsync.bridge.crypto.E2ECryptoManager
import dev.continuumsync.bridge.transport.HybridTransportEngine
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

        val payloadJson = JSONObject().apply {
            put("type", "NOTIFICATION_POST")
            put("sbnKey", sbn.key)
            put("packageName", sbn.packageName)
            put("title", title)
            put("body", text)
            put("canReplyInline", replyAction != null)
            put("timestamp", sbn.postTime)
        }.toString()

        HybridTransportEngine.sendEncrypted(
            E2ECryptoManager.seal(payloadJson.toByteArray(Charsets.UTF_8))
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        replyActionsCache.remove(sbn.key)
        val cancelJson = JSONObject().apply {
            put("type", "NOTIFICATION_CANCEL")
            put("sbnKey", sbn.key)
        }.toString()

        HybridTransportEngine.sendEncrypted(
            E2ECryptoManager.seal(cancelJson.toByteArray(Charsets.UTF_8))
        )
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
        RemoteInput.addResultsToIntent(remoteInputs, fillInIntent, resultsBundle)
        action.actionIntent.send(applicationContext, 0, fillInIntent)
    }
}
