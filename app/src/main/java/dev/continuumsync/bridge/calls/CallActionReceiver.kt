package dev.continuumsync.bridge.calls

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import dev.continuumsync.bridge.data.CallSnapshot
import dev.continuumsync.bridge.data.SyncRepository
import dev.continuumsync.bridge.service.SyncForegroundService

/**
 * Handles Answer / Decline / End from the call notification and the in-app
 * call screen. If the call belongs to the other phone, the action is relayed
 * through the room; if it belongs to this phone, Telecom is called directly.
 */
class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        SyncRepository.init(context.applicationContext)

        val callId = intent.getStringExtra(EXTRA_CALL_ID).orEmpty()
        val origin = intent.getStringExtra(EXTRA_ORIGIN)
            ?: SyncRepository.snapshot.value?.activeCall?.originDevice
            ?: SyncRepository.prefs.role
        val call = CallSnapshot(
            callId = callId,
            originDevice = origin,
            callerName = intent.getStringExtra(EXTRA_NAME).orEmpty(),
            callerNumber = intent.getStringExtra(EXTRA_NUMBER).orEmpty(),
            status = SyncRepository.snapshot.value?.activeCall?.status ?: "ringing"
        )
        val isLocalCall = origin == SyncRepository.prefs.role

        when (action) {
            ACTION_ANSWER -> {
                if (isLocalCall) {
                    CallSyncController(context.applicationContext).answerCall()
                } else {
                    SyncRepository.respondToCall(call, "answer-requested")
                }
            }
            ACTION_DECLINE -> {
                if (isLocalCall) {
                    SyncRepository.respondToCall(call, "rejected")
                    CallSyncController(context.applicationContext).endCall(reject = true)
                } else {
                    SyncRepository.respondToCall(call, "rejected")
                }
            }
            ACTION_END -> {
                if (isLocalCall) {
                    SyncRepository.respondToCall(call, "ended")
                    CallSyncController(context.applicationContext).endCall()
                } else {
                    SyncRepository.respondToCall(call, "ended")
                }
            }
        }

        if (action == ACTION_DECLINE || action == ACTION_END) {
            NotificationManagerCompat.from(context)
                .cancel(SyncForegroundService.CALL_NOTIFICATION_ID)
        }
    }

    companion object {
        const val ACTION_ANSWER = "dev.continuumsync.bridge.CALL_ANSWER"
        const val ACTION_DECLINE = "dev.continuumsync.bridge.CALL_DECLINE"
        const val ACTION_END = "dev.continuumsync.bridge.CALL_END"

        const val EXTRA_CALL_ID = "call_id"
        const val EXTRA_ORIGIN = "call_origin"
        const val EXTRA_NAME = "call_name"
        const val EXTRA_NUMBER = "call_number"
    }
}
