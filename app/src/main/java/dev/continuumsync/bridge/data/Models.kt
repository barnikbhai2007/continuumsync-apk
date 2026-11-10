package dev.continuumsync.bridge.data

data class SyncNotification(
    val id: String,
    val originDevice: String,
    val appName: String,
    val title: String,
    val content: String,
    val timestamp: String,
    val dismissed: Boolean,
    val repliedText: String? = null
)

data class ClipboardSnapshot(
    val text: String = "",
    val sourceDevice: String = "phone-a",
    val timestamp: String = ""
)

data class CallSnapshot(
    val callId: String = "",
    val originDevice: String = "phone-a",
    val callerName: String = "Unknown caller",
    val callerNumber: String = "",
    val status: String = "idle",
    val actionTakenBy: String? = null,
    val timestamp: String = ""
) {
    val isRinging: Boolean get() = status == "ringing"
    val isActive: Boolean get() = status.startsWith("active")
    val isFinished: Boolean get() = status == "rejected" || status == "ended"
}

data class RoomSnapshot(
    val room: String = "",
    val updatedAt: String = "",
    val connectedPeers: Int = 0,
    val clipboard: ClipboardSnapshot = ClipboardSnapshot(),
    val activeCall: CallSnapshot = CallSnapshot(),
    val notifications: List<SyncNotification> = emptyList()
)

enum class ConnectionState { IDLE, CONNECTING, CONNECTED, OFFLINE }

const val ROLE_A = "phone-a"
const val ROLE_B = "phone-b"

fun roleLabel(role: String): String = if (role == ROLE_B) "Phone B" else "Phone A"
fun peerRole(role: String): String = if (role == ROLE_B) ROLE_A else ROLE_B
