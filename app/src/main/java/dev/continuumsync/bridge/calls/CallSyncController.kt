package dev.continuumsync.bridge.calls

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telecom.TelecomManager
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
    private var currentNumber: String = ""
    private var currentName: String = ""
    private var answered = false
    private var pendingEndStatus: String? = null
    private var lastState: Int = TelephonyManager.CALL_STATE_IDLE
    private var registered = false

    private val phoneStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            if (intent?.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
            val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
                TelephonyManager.EXTRA_STATE_RINGING ->
                    handleState(TelephonyManager.CALL_STATE_RINGING, number)
                TelephonyManager.EXTRA_STATE_OFFHOOK ->
                    handleState(TelephonyManager.CALL_STATE_OFFHOOK, number)
                TelephonyManager.EXTRA_STATE_IDLE ->
                    handleState(TelephonyManager.CALL_STATE_IDLE, number)
            }
        }
    }

    fun registerCallStateObserver() {
        if (registered) return
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return

        try {
            ContextCompat.registerReceiver(
                context,
                phoneStateReceiver,
                IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED),
                ContextCompat.RECEIVER_EXPORTED
            )
            registered = true
        } catch (error: Exception) {
            Log.w(TAG, "call observer failed: ${error.message}")
        }
    }

    fun unregister() {
        if (!registered) return
        try {
            context.unregisterReceiver(phoneStateReceiver)
        } catch (_: Exception) {
        }
        registered = false
    }

    private fun handleState(state: Int, number: String?) {
        val previous = lastState
        val cleanNumber = number?.trim().orEmpty()
        if (state == previous && (cleanNumber.isBlank() || cleanNumber == currentNumber)) {
            return
        }
        lastState = state

        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                if (previous != TelephonyManager.CALL_STATE_RINGING || currentCallId.isBlank()) {
                    currentCallId = "call-${System.currentTimeMillis()}"
                    currentNumber = ""
                    currentName = ""
                    answered = false
                    pendingEndStatus = null
                }
                if (cleanNumber.isNotBlank() && cleanNumber != currentNumber) {
                    currentNumber = cleanNumber
                    currentName = resolveContactName(cleanNumber)
                    postCall("ringing")
                } else if (previous != TelephonyManager.CALL_STATE_RINGING) {
                    postCall("ringing")
                }
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                answered = true
                if (currentCallId.isBlank()) {
                    currentCallId = "call-${System.currentTimeMillis()}"
                }
                postCall("active-on-origin")
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (currentCallId.isNotBlank()) {
                    val status = pendingEndStatus ?: if (
                        !answered && previous == TelephonyManager.CALL_STATE_RINGING
                    ) {
                        "missed"
                    } else {
                        "ended"
                    }
                    postCall(status)
                }
                currentCallId = ""
                currentNumber = ""
                currentName = ""
                answered = false
                pendingEndStatus = null
            }
        }
    }

    private fun postCall(status: String) {
        val payload = JSONObject()
            .put("callId", currentCallId.ifBlank { "call-${System.currentTimeMillis()}" })
            .put("originDevice", SyncRepository.prefs.role)
            .put("callerName", currentName.ifBlank { "Unknown caller" })
            .put("callerNumber", currentNumber)
            .put("status", status)
        SyncRepository.postEventAsync("CALL_ACTION", payload)
    }

    private fun resolveContactName(number: String): String {
        if (number.isBlank()) return ""
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return ""
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
            } ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    fun answerCall() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ANSWER_PHONE_CALLS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            Log.w(TAG, "ANSWER_PHONE_CALLS not granted")
            return
        }
        try {
            telecomManager.acceptRingingCall()
            audioManager.mode = AudioManager.MODE_IN_CALL
            audioManager.isSpeakerphoneOn = true
            pendingEndStatus = null
            Log.i(TAG, "accepted ringing call")
        } catch (error: Exception) {
            Log.w(TAG, "acceptRingingCall failed: ${error.message}")
        }
    }

    fun endCall(reject: Boolean = false) {
        pendingEndStatus = if (reject) "rejected" else "ended"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                telecomManager.endCall()
            }
        } catch (error: Exception) {
            Log.w(TAG, "endCall failed: ${error.message}")
        }
        if (reject) {
            try {
                telecomManager.silenceRinger()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private const val TAG = "ContinuumCalls"
    }
}
