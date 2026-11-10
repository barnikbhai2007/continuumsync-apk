package dev.continuumsync.bridge.clipboard

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.service.quicksettings.TileService
import android.view.accessibility.AccessibilityEvent
import dev.continuumsync.bridge.crypto.E2ECryptoManager
import dev.continuumsync.bridge.transport.HybridTransportEngine
import org.json.JSONObject
import java.security.MessageDigest

class ClipboardAccessibilityService : AccessibilityService() {

    private lateinit var clipboardManager: ClipboardManager
    private var lastSyncedHash: String = ""

    override fun onServiceConnected() {
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val isCopyAction = event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED

        if (isCopyAction) {
            tryReadAndPushClipboard()
        }
    }

    private fun tryReadAndPushClipboard() {
        val clip = clipboardManager.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
        if (text.isBlank()) return

        val currentHash = sha256Hex(text)
        if (currentHash == lastSyncedHash) return
        lastSyncedHash = currentHash

        val json = JSONObject().apply {
            put("type", "CLIPBOARD_PUSH")
            put("text", text)
            put("sha256", currentHash)
            put("timestamp", System.currentTimeMillis())
        }.toString()

        HybridTransportEngine.sendEncrypted(
            E2ECryptoManager.seal(json.toByteArray(Charsets.UTF_8))
        )
    }

    fun applyIncomingClipboard(text: String, incomingHash: String) {
        lastSyncedHash = incomingHash
        val clipData = ClipData.newPlainText("ContinuumSync Peer Clip", text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clipData.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false)
            }
        }
        clipboardManager.setPrimaryClip(clipData)
    }

    override fun onInterrupt() {}

    private fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

class ClipboardTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboardManager.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() ?: return
        if (text.isBlank()) return

        val json = JSONObject().apply {
            put("type", "CLIPBOARD_PUSH")
            put("text", text)
            put("timestamp", System.currentTimeMillis())
        }.toString()

        HybridTransportEngine.sendEncrypted(
            E2ECryptoManager.seal(json.toByteArray(Charsets.UTF_8))
        )
    }
}
