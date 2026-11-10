package dev.continuumsync.bridge.ui

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.continuumsync.bridge.calls.CallSyncController
import dev.continuumsync.bridge.clipboard.ClipboardBridge
import dev.continuumsync.bridge.data.CallSnapshot
import dev.continuumsync.bridge.data.SyncRepository
import dev.continuumsync.bridge.service.SyncForegroundService
import dev.continuumsync.bridge.ui.theme.Bg
import dev.continuumsync.bridge.ui.theme.ContinuumTheme
import kotlinx.coroutines.delay

/**
 * Full-screen incoming-call screen shown on the phone that does not own the SIM
 * call. The phone with the SIM answers the real call when the user taps Answer.
 */
class IncomingCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SyncRepository.init(applicationContext)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent),
            navigationBarStyle = SystemBarStyle.dark(Color.Transparent)
        )

        setContent {
            ContinuumTheme {
                CallScreenHost(onFinish = { finish() })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ClipboardBridge.setAppVisible(true)
    }

    override fun onPause() {
        ClipboardBridge.setAppVisible(false)
        super.onPause()
    }

    @Composable
    private fun CallScreenHost(onFinish: () -> Unit) {
        val snapshot by SyncRepository.snapshot.collectAsStateWithLifecycle()
        val call = snapshot?.activeCall

        LaunchedEffect(call?.callId, call?.status) {
            if (snapshot != null && (call == null || call.status == "idle" || call.isFinished)) {
                delay(2600)
                onFinish()
            }
        }

        if (call == null || call.status == "idle") {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Bg),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF2BD9A8))
            }
            return
        }

        CallScreen(
            call = call,
            myRole = SyncRepository.prefs.role,
            onAnswer = { answer(call) },
            onDecline = { decline(call, onFinish) },
            onEnd = { end(call, onFinish) },
            onClose = onFinish
        )
    }

    private fun answer(call: CallSnapshot) {
        if (call.originDevice == SyncRepository.prefs.role) {
            CallSyncController(applicationContext).answerCall()
        } else {
            SyncRepository.respondToCall(call, "answer-requested")
        }
    }

    private fun decline(call: CallSnapshot, onFinish: () -> Unit) {
        val isLocal = call.originDevice == SyncRepository.prefs.role
        if (isLocal) {
            SyncRepository.respondToCall(call, "rejected")
            CallSyncController(applicationContext).endCall(reject = true)
        } else {
            SyncRepository.respondToCall(call, "rejected")
        }
        NotificationManagerCompat.from(this).cancel(SyncForegroundService.CALL_NOTIFICATION_ID)
        onFinish()
    }

    private fun end(call: CallSnapshot, onFinish: () -> Unit) {
        val isLocal = call.originDevice == SyncRepository.prefs.role
        if (isLocal) {
            SyncRepository.respondToCall(call, "ended")
            CallSyncController(applicationContext).endCall()
        } else {
            SyncRepository.respondToCall(call, "ended")
        }
        NotificationManagerCompat.from(this).cancel(SyncForegroundService.CALL_NOTIFICATION_ID)
        onFinish()
    }

    companion object {
        const val EXTRA_CALL_ID = "incoming_call_id"
    }
}
