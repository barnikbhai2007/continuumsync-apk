package dev.continuumsync.bridge.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.continuumsync.bridge.data.SyncRepository

/** Dismissing a mirrored notification also dismisses the original on the source phone. */
class MirrorDismissReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        SyncRepository.init(context.applicationContext)
        SyncRepository.dismissNotification(id)
    }

    companion object {
        const val ACTION_DISMISS = "dev.continuumsync.bridge.MIRROR_DISMISS"
        const val EXTRA_ID = "mirror_id"
    }
}
