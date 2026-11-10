package dev.continuumsync.bridge.data

import android.content.Context

class SyncPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("continuum_sync", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) {
            prefs.edit().putString(KEY_BASE_URL, normalizeBaseUrl(value)).apply()
        }

    var room: String
        get() = prefs.getString(KEY_ROOM, DEFAULT_ROOM) ?: DEFAULT_ROOM
        set(value) {
            prefs.edit().putString(KEY_ROOM, normalizeRoom(value)).apply()
        }

    var role: String
        get() {
            val value = prefs.getString(KEY_ROLE, ROLE_A) ?: ROLE_A
            return if (value == ROLE_B) ROLE_B else ROLE_A
        }
        set(value) {
            prefs.edit().putString(KEY_ROLE, if (value == ROLE_B) ROLE_B else ROLE_A).apply()
        }

    companion object {
        const val DEFAULT_BASE_URL = "http://192.168.31.166:3100"
        const val DEFAULT_ROOM = "482-910"

        private const val KEY_BASE_URL = "base_url"
        private const val KEY_ROOM = "room"
        private const val KEY_ROLE = "role"

        fun normalizeBaseUrl(raw: String): String {
            var value = raw.trim()
            if (value.isBlank()) value = DEFAULT_BASE_URL
            if (!value.startsWith("http://") && !value.startsWith("https://")) {
                value = "http://$value"
            }
            return value.trimEnd('/')
        }

        fun normalizeRoom(raw: String): String {
            val value = raw.trim()
            return value.ifBlank { DEFAULT_ROOM }
        }
    }
}
