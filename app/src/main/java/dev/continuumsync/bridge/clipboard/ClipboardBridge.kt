package dev.continuumsync.bridge.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import dev.continuumsync.bridge.data.ClipboardApplier
import dev.continuumsync.bridge.data.ClipboardGuard
import dev.continuumsync.bridge.data.ClipboardReader
import dev.continuumsync.bridge.data.SyncRepository
import org.json.JSONObject

/**
 * Android 10+ blocks clipboard access for apps that do not have window focus.
 * Accessibility services are not exempt on stock Android, so this bridge briefly
 * shows a transparent, focusable 2x2 overlay whenever it needs to read or write
 * the clipboard in the background. While our own UI is visible it reads/writes
 * directly without an overlay. Requires the "Display over other apps" permission.
 */
object ClipboardBridge {

    private const val TAG = "ContinuumClipboard"
    private const val FALLBACK_POLL_MS = 6_000L
    private const val IME_POLL_MS = 1_000L
    private const val OVERLAY_SETTLE_MS = 220L
    private const val REPEAT_GUARD_MS = 300L

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var started = false

    @Volatile
    private var appVisible = false

    @Volatile
    private var busy = false

    private var lastSentHash = ""
    private var lastCaptureAt = 0L
    private var pendingWrite: String? = null

    private val clipboardManager: ClipboardManager?
        get() = appContext?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    private val changeListener = ClipboardManager.OnPrimaryClipChangedListener {
        requestCapture(320L)
    }

    private val readRunnable = Runnable { captureClipboard() }

    private val poller = object : Runnable {
        override fun run() {
            if (!started) return
            captureClipboard()
            // While an input method is open, captureClipboard only uses the
            // direct/accessibility read and never the focus overlay. Check
            // more often so sync resumes as soon as the keyboard closes.
            val delay = if (isImeVisible()) IME_POLL_MS else FALLBACK_POLL_MS
            handler.postDelayed(this, delay)
        }
    }

    private fun isImeVisible(): Boolean =
        ClipboardAccessibilityService.isImeVisible() == true

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    fun start(context: Context) {
        init(context)
        if (started) return
        started = true
        try {
            clipManager()?.addPrimaryClipChangedListener(changeListener)
        } catch (error: Exception) {
            Log.w(TAG, "clipboard listener failed: ${error.message}")
        }
        handler.removeCallbacks(poller)
        handler.postDelayed({
            if (started) {
                val existing = readFromAccessibility() ?: readDirect()
                if (!existing.isNullOrBlank()) {
                    lastSentHash = ClipboardGuard.sha256(existing)
                }
            }
        }, 700L)
        handler.postDelayed(poller, 1_500L)
    }

    fun stop() {
        started = false
        handler.removeCallbacks(poller)
        handler.removeCallbacks(readRunnable)
        try {
            clipManager()?.removePrimaryClipChangedListener(changeListener)
        } catch (_: Exception) {
        }
    }

    fun setAppVisible(visible: Boolean) {
        appVisible = visible
        if (visible) requestCapture(0L)
    }

    /** Called by accessibility events and the clipboard change listener. */
    fun requestCapture(delayMs: Long = 250L) {
        if (!started) return
        val now = SystemClock.uptimeMillis()
        if (now - lastCaptureAt < REPEAT_GUARD_MS) return
        lastCaptureAt = now
        handler.removeCallbacks(readRunnable)
        handler.postDelayed(readRunnable, delayMs)
    }

    /**
     * Reads the local clipboard and, if it changed, pushes it to the room.
     * Uses the accessibility service first, then a direct read, then the overlay.
     */
    private fun captureClipboard() {
        if (!started || busy) return
        val direct = readFromAccessibility() ?: readDirect()
        if (!direct.isNullOrBlank()) {
            acceptOutgoing(direct)
            return
        }
        if (appVisible) return
        if (isImeVisible()) return
        if (!canDrawOverlay()) return

        withClipboardFocus(
            action = { manager ->
                val text = manager.primaryClip
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)
                    ?.coerceToText(appContext)
                    ?.toString()
                if (!text.isNullOrBlank()) acceptOutgoing(text)
            }
        )
    }

    private fun acceptOutgoing(text: String) {
        if (text.isBlank()) return
        if (ClipboardGuard.shouldIgnore(text)) return
        val hash = ClipboardGuard.sha256(text)
        if (hash == lastSentHash) return
        lastSentHash = hash
        try {
            val payload = JSONObject()
                .put("text", text)
                .put("sourceDevice", SyncRepository.prefs.role)
            SyncRepository.postEventAsync("CLIPBOARD_SYNC", payload)
        } catch (error: Exception) {
            Log.w(TAG, "clipboard push failed: ${error.message}")
        }
    }

    /** Applies clipboard text received from the other phone. */
    fun applyRemote(context: Context, text: String) {
        init(context)
        applyRemote(text)
    }

    /** Applies clipboard text received from the other phone. */
    fun applyRemote(text: String) {
        if (text.isBlank()) return
        val context = appContext ?: return
        init(context)
        val hash = ClipboardGuard.sha256(text)
        ClipboardGuard.mark(text)
        lastSentHash = hash
        handler.post {
            if (busy) {
                pendingWrite = text
            } else {
                performWrite(text)
            }
        }
    }

    private fun performWrite(text: String) {
        if (appVisible) {
            if (writeDirect(text)) return
        }
        // On some ROMs accessibility services can write directly. Verify the
        // write before falling back to the focus overlay.
        if (ClipboardApplier.apply?.invoke(text) == true) {
            val applied = readFromAccessibility() ?: readDirect()
            if (applied == text) return
        }
        if (canDrawOverlay()) {
            withClipboardFocus(
                action = { manager -> manager.setPrimaryClip(buildClip(text)) },
                onDone = {
                    val next = pendingWrite
                    pendingWrite = null
                    if (next != null) performWrite(next)
                }
            )
            return
        }
        writeDirect(text)
    }

    private fun readFromAccessibility(): String? {
        return try {
            ClipboardReader.read?.invoke()
        } catch (_: Exception) {
            null
        }
    }

    private fun readDirect(): String? {
        return try {
            clipManager()?.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(appContext)
                ?.toString()
        } catch (error: Exception) {
            Log.w(TAG, "direct clipboard read failed: ${error.message}")
            null
        }
    }

    private fun writeDirect(text: String): Boolean {
        return try {
            clipManager()?.setPrimaryClip(buildClip(text)) ?: return false
            true
        } catch (error: Exception) {
            Log.w(TAG, "direct clipboard write failed: ${error.message}")
            false
        }
    }

    private fun buildClip(text: String): ClipData {
        val clip = ClipData.newPlainText("ContinuumSync", text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false)
            }
        }
        return clip
    }

    fun canDrawOverlay(): Boolean {
        val context = appContext ?: return false
        return Settings.canDrawOverlays(context)
    }

    /**
     * Adds a tiny transparent overlay which takes window focus, so the clipboard
     * service treats this app as focused. The overlay is removed immediately
     * after the action completes.
     */
    private fun withClipboardFocus(
        action: (ClipboardManager) -> Unit,
        onDone: (() -> Unit)? = null
    ) {
        val context = appContext ?: run {
            onDone?.invoke()
            return
        }
        val manager = clipManager() ?: run {
            onDone?.invoke()
            return
        }
        if (!canDrawOverlay()) {
            onDone?.invoke()
            return
        }
        if (busy) {
            onDone?.invoke()
            return
        }
        busy = true

        val windowManager =
            context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (windowManager == null) {
            busy = false
            onDone?.invoke()
            return
        }

        var overlay: View? = null
        try {
            val view = View(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                isFocusable = true
                isFocusableInTouchMode = true
            }
            overlay = view
            val params = WindowManager.LayoutParams(
                2,
                2,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    // Keep the current input method visible while this window
                    // takes focus only long enough to read the clipboard.
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
                softInputMode =
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED or
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            }
            windowManager.addView(view, params)
            view.requestFocus()

            var finished = false
            fun finish() {
                if (finished) return
                finished = true
                try {
                    action(manager)
                } catch (error: Exception) {
                    Log.w(TAG, "clipboard action failed: ${error.message}")
                } finally {
                    try {
                        windowManager.removeView(view)
                    } catch (_: Exception) {
                    }
                    busy = false
                    onDone?.invoke()
                }
            }

            // Read as soon as the window actually owns focus. This keeps the
            // focus steal to roughly one frame instead of a fixed delay, so
            // typing is not interrupted on devices where the IME check is
            // unavailable.
            view.viewTreeObserver.addOnWindowFocusChangeListener { hasFocus ->
                if (hasFocus) view.post { finish() }
            }
            // Safety net for ROMs that do not deliver the focus callback.
            view.postDelayed({ finish() }, OVERLAY_SETTLE_MS)
        } catch (error: Exception) {
            Log.w(TAG, "focus overlay failed: ${error.message}")
            if (overlay != null) {
                try {
                    windowManager.removeView(overlay)
                } catch (_: Exception) {
                }
            }
            busy = false
            onDone?.invoke()
        }
    }

    private fun clipManager(): ClipboardManager? = clipboardManager
}
