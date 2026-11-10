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
import android.widget.Toast
import dev.continuumsync.bridge.data.ClipboardGuard
import dev.continuumsync.bridge.data.SyncRepository
import org.json.JSONObject

class ClipboardAccessibilityService : AccessibilityService() {

    private lateinit var clipboardManager: ClipboardManager
    private var lastSyncedHash: String = ""
    private var lastReadAt: Long = 0L

    override fun onServiceConnected() {
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val isCopyAction = event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED

        if (isCopyAction) {
            val now = System.currentTimeMillis()
            if (now - lastReadAt < 900L) return
            lastReadAt = now
            tryReadAndPushClipboard()
        }
    }

    private fun tryReadAndPushClipboard() {
        val clip = clipboardManager.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
        if (text.isBlank()) return
        if (ClipboardGuard.shouldIgnore(text)) return

        val currentHash = ClipboardGuard.sha256(text)
        if (currentHash == lastSyncedHash) return
        lastSyncedHash = currentHash

        val payload = JSONObject()
            .put("text", text)
            .put("sourceDevice", SyncRepository.prefs.role)
        SyncRepository.postEventAsync("CLIPBOARD_SYNC", payload)
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
}

class ClipboardTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboardManager.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
        if (text.isNullOrBlank()) {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            return
        }

        ClipboardGuard.mark(text)
        val payload = JSONObject()
            .put("text", text)
            .put("sourceDevice", SyncRepository.prefs.role)
        SyncRepository.postEventAsync("CLIPBOARD_SYNC", payload)
        Toast.makeText(this, "Clipboard sent to paired phone", Toast.LENGTH_SHORT).show()
    }
}
