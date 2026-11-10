package dev.continuumsync.bridge.calls

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import dev.continuumsync.bridge.crypto.E2ECryptoManager
import dev.continuumsync.bridge.transport.HybridTransportEngine
import org.json.JSONObject

class CallSyncController(private val context: Context) {

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val telecomManager =
        context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun registerCallStateObserver() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPerm) return

            telephonyManager.registerTelephonyCallback(
                context.mainExecutor,
                object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        broadcastCallState(state)
                    }
                }
            )
        }
    }

    private fun broadcastCallState(state: Int) {
        val statusString = when (state) {
            TelephonyManager.CALL_STATE_RINGING -> "RINGING"
            TelephonyManager.CALL_STATE_OFFHOOK -> "ACTIVE"
            TelephonyManager.CALL_STATE_IDLE -> "IDLE"
            else -> "IDLE"
        }

        val json = JSONObject().apply {
            put("type", "CALL_STATE")
            put("state", statusString)
            put("timestamp", System.currentTimeMillis())
        }.toString()

        HybridTransportEngine.sendEncrypted(
            E2ECryptoManager.seal(json.toByteArray(Charsets.UTF_8))
        )
    }

    @SuppressLint("MissingPermission")
    fun executeRemoteCallCommand(command: String, enableSpeakerOnAnswer: Boolean = true) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ANSWER_PHONE_CALLS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        when (command) {
            "ANSWER_CALL" -> {
                telecomManager.acceptRingingCall()
                if (enableSpeakerOnAnswer) {
                    audioManager.mode = AudioManager.MODE_IN_CALL
                    audioManager.isSpeakerphoneOn = true
                }
            }
            "REJECT_OR_END_CALL" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    telecomManager.endCall()
                }
            }
        }
    }
}
