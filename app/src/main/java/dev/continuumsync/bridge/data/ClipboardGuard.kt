package dev.continuumsync.bridge.data

import android.os.Handler
import android.os.Looper
import java.security.MessageDigest

object ClipboardGuard {

    @Volatile
    private var lastHash: String? = null

    @Volatile
    private var lastAt: Long = 0L

    fun mark(text: String) {
        lastHash = sha256(text)
        lastAt = System.currentTimeMillis()
    }

    fun shouldIgnore(text: String): Boolean {
        val hash = sha256(text)
        return hash == lastHash && System.currentTimeMillis() - lastAt < 5000L
    }

    fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

object MainThread {
    private val handler = Handler(Looper.getMainLooper())
    fun post(block: () -> Unit) {
        handler.post(block)
    }
}
