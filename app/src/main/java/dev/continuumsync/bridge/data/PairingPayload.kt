package dev.continuumsync.bridge.data

import java.net.URLDecoder
import java.net.URLEncoder

data class PairingData(
    val room: String,
    val hostUrl: String,
    val name: String
)

/**
 * The QR code / share link payload. It carries everything the joining device
 * needs: the room, the host's Wi-Fi address and a friendly device name.
 */
object PairingPayload {

    private const val SCHEME = "continuumsync"

    fun build(room: String, hostUrl: String, name: String): String {
        val query = buildString {
            append("room=").append(encode(room))
            if (hostUrl.isNotBlank()) {
                append("&host=").append(encode(hostUrl))
            }
            if (name.isNotBlank()) {
                append("&name=").append(encode(name))
            }
        }
        return "$SCHEME://pair?$query"
    }

    fun parse(raw: String): PairingData? {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("$SCHEME://") && !trimmed.startsWith("$SCHEME:")) {
            return null
        }
        val query = trimmed.substringAfter('?', "")
        if (query.isBlank()) return null

        val values = mutableMapOf<String, String>()
        for (part in query.split('&')) {
            if (part.isBlank()) continue
            val key = part.substringBefore('=')
            val value = part.substringAfter('=', "")
            if (key.isNotBlank()) {
                values[key] = decode(value)
            }
        }

        val room = values["room"].orEmpty().trim()
        val host = values["host"].orEmpty().trim()
        if (room.isBlank() && host.isBlank()) return null
        return PairingData(
            room = room,
            hostUrl = host,
            name = values["name"].orEmpty().trim()
        )
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8")

    private fun decode(value: String): String = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (_: Exception) {
        value
    }
}
