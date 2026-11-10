package dev.continuumsync.bridge.calls

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import dev.continuumsync.bridge.data.SyncRepository
import org.json.JSONObject

class CallSyncController(private val context: Context) {

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val telecomManager =
        context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var currentCallId: String = ""
    private var lastState: Int = -1
    private var registered = false

    fun registerCallStateObserver() {
        if (registered) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return

        try {
            telephonyManager.registerTelephonyCallback(
                context.mainExecutor,
                object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        if (state == lastState) return
                        lastState = state
                        when (state) {
                            TelephonyManager.CALL_STATE_RINGING -> {
                                currentCallId = "call-${System.currentTimeMillis()}"
                                postCall("ringing", "Incoming call", "")
                            }
                            TelephonyManager.CALL_STATE_OFFHOOK -> {
                                postCall("active-on-origin", "Call in progress", "")
                            }
                            TelephonyManager.CALL_STATE_IDLE -> {
                                if (currentCallId.isNotBlank()) {
                                    postCall("ended", "Call ended", "")
                                }
                            }
                        }
                    }
                }
            )
            registered = true
        } catch (error: Exception) {
            Log.w("ContinuumSync", "call observer failed: ${error.message}")
        }
    }

    private fun postCall(status: String, name: String, number: String) {
        val payload = JSONObject()
            .put("callId", currentCallId.ifBlank { "call-${System.currentTimeMillis()}" })
            .put("originDevice", SyncRepository.prefs.role)
            .put("callerName", name)
            .put("callerNumber", number)
            .put("status", status)
        SyncRepository.postEventAsync("CALL_ACTION", payload)
    }

    fun answerCall() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ANSWER_PHONE_CALLS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            Log.w("ContinuumSync", "ANSWER_PHONE_CALLS not granted")
            return
        }
        try {
            telecomManager.acceptRingingCall()
            audioManager.mode = AudioManager.MODE_IN_CALL
            audioManager.isSpeakerphoneOn = true
        } catch (error: Exception) {
            Log.w("ContinuumSync", "acceptRingingCall failed: ${error.message}")
        }
    }

    fun endCall() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                telecomManager.endCall()
            }
        } catch (error: Exception) {
            Log.w("ContinuumSync", "endCall failed: ${error.message}")
        }
    }
}
