package dev.continuumsync.bridge.data

data class SyncNotification(
    val id: String,
    val originDevice: String,
    val appName: String,
    val appPackage: String = "",
    val title: String,
    val content: String,
    val timestamp: String,
    val dismissed: Boolean,
    val repliedText: String? = null,
    val repliedBy: String? = null,
    val canReply: Boolean = false
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
    val isFinished: Boolean get() =
        status == "rejected" || status == "ended" || status == "missed"
}

data class PeerDevice(
    val role: String = ROLE_A,
    val name: String = "",
    val model: String = "",
    val online: Boolean = true
) {
    val label: String
        get() = name.ifBlank {
            when {
                model.isNotBlank() -> model
                role == ROLE_B -> "Phone B"
                else -> "Phone A"
            }
        }
}

data class RoomSnapshot(
    val room: String = "",
    val updatedAt: String = "",
    val connectedPeers: Int = 0,
    val clipboard: ClipboardSnapshot = ClipboardSnapshot(),
    val activeCall: CallSnapshot = CallSnapshot(),
    val notifications: List<SyncNotification> = emptyList(),
    val peers: List<PeerDevice> = emptyList()
)

enum class ConnectionState { IDLE, CONNECTING, CONNECTED, OFFLINE }

const val ROLE_A = "phone-a"
const val ROLE_B = "phone-b"

fun roleLabel(role: String): String = if (role == ROLE_B) "Phone B" else "Phone A"
fun peerRole(role: String): String = if (role == ROLE_B) ROLE_A else ROLE_B
