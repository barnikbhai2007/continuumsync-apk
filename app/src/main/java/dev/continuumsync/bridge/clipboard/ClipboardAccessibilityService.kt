package dev.continuumsync.bridge.clipboard

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.service.quicksettings.TileService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import dev.continuumsync.bridge.data.ClipboardApplier
import dev.continuumsync.bridge.data.ClipboardReader

/**
 * Accessibility access is optional now: it gives ClipboardBridge a direct
 * clipboard reader/writer on ROMs where accessibility services are exempt from
 * the Android 10 focus restriction. On stock Android the focus overlay in
 * ClipboardBridge is what makes background sync work.
 */
class ClipboardAccessibilityService : AccessibilityService() {

    private lateinit var clipboardManager: ClipboardManager

    override fun onServiceConnected() {
        super.onServiceConnected()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        instance = this
        ClipboardBridge.init(this)
        ClipboardBridge.start(this)
        ClipboardReader.read = { readClipboard() }
        ClipboardApplier.apply = { text -> applyClipboard(text) }
        Log.i(TAG, "accessibility clipboard bridge connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Clipboard changes are detected by ClipboardBridge's primary-clip listener.
        // Accessibility events are intentionally not used here because grabbing
        // window focus on every tap/selection would interrupt the user's typing.
    }

    private fun readClipboard(): String? {
        return try {
            clipboardManager.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(this)
                ?.toString()
        } catch (error: Exception) {
            Log.w(TAG, "clipboard read failed: ${error.message}")
            null
        }
    }

    private fun applyClipboard(text: String): Boolean {
        return try {
            val clip = ClipData.newPlainText("ContinuumSync", text)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                clip.description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false)
                }
            }
            clipboardManager.setPrimaryClip(clip)
            true
        } catch (error: Exception) {
            Log.w(TAG, "clipboard write failed: ${error.message}")
            false
        }
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        ClipboardReader.read = null
        ClipboardApplier.apply = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    companion object {
        private const val TAG = "ContinuumClipboard"

        @Volatile
        private var instance: ClipboardAccessibilityService? = null

        /**
         * Reports whether an input method window is on screen. Returns null when
         * the accessibility service is not connected or window inspection is
         * unavailable, so callers can fall back to the normal clipboard path.
         */
        fun isImeVisible(): Boolean? {
            val service = instance ?: return null
            return try {
                service.windows?.any {
                    it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
                }
            } catch (error: Exception) {
                Log.w(TAG, "IME visibility check failed: ${error.message}")
                null
            }
        }
    }
}

class ClipboardTileService : TileService() {
    override fun onClick() {
        super.onClick()
        ClipboardBridge.init(this)
        ClipboardBridge.start(this)
        ClipboardBridge.requestCapture(0L)
        Toast.makeText(this, "Clipboard sync requested", Toast.LENGTH_SHORT).show()
    }
}
