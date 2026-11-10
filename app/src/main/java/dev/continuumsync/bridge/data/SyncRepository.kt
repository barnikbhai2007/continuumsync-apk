package dev.continuumsync.bridge.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.coroutineContext

object SyncRepository {

    private const val TAG = "ContinuumSync"

    private lateinit var appContext: Context
    lateinit var prefs: SyncPrefs
        private set

    @Volatile
    private var initialized = false

    private val _snapshot = kotlinx.coroutines.flow.MutableStateFlow<RoomSnapshot?>(null)
    val snapshot = _snapshot

    private val _connection = kotlinx.coroutines.flow.MutableStateFlow(ConnectionState.IDLE)
    val connection = _connection

    private val _configVersion = kotlinx.coroutines.flow.MutableStateFlow(0)
    val configVersion = _configVersion

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var streamJob: Job? = null

    @Volatile
    private var activeConnection: HttpURLConnection? = null

    @Volatile
    private var lastAppliedClip: String = ""

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            appContext = context.applicationContext
            prefs = SyncPrefs(appContext)
            initialized = true
            start()
        }
    }

    fun start() {
        if (!initialized) return
        streamJob?.cancel()
        try {
            activeConnection?.disconnect()
        } catch (_: Exception) {
        }
        _snapshot.value = null
        _connection.value = ConnectionState.CONNECTING
        streamJob = scope.launch { streamLoop() }
    }

    fun updateConfig(baseUrl: String, room: String, role: String) {
        prefs.baseUrl = baseUrl
        prefs.room = room
        prefs.role = role
        _configVersion.value += 1
        start()
    }

    fun setRole(role: String) {
        prefs.role = role
        _configVersion.value += 1
        start()
    }

    private suspend fun streamLoop() {
        while (coroutineContext.isActive) {
            val base = SyncPrefs.normalizeBaseUrl(prefs.baseUrl)
            val room = SyncPrefs.normalizeRoom(prefs.room)
            var connection: HttpURLConnection? = null
            var connectedOnce = false

            try {
                val url = URL(
                    "$base/api/sync/stream?room=${URLEncoder.encode(room, "UTF-8")}" +
                        "&device=${URLEncoder.encode(prefs.role, "UTF-8")}"
                )
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 30000
                    useCaches = false
                    setRequestProperty("Accept", "text/event-stream")
                    setRequestProperty("Cache-Control", "no-cache")
                }
                activeConnection = connection
                connection.connect()
                connectedOnce = true
                _connection.value = ConnectionState.CONNECTED

                val reader = connection.inputStream.bufferedReader()
                val buffer = StringBuilder()

                while (coroutineContext.isActive) {
                    val line = try {
                        reader.readLine()
                    } catch (_: SocketTimeoutException) {
                        null
                    }
                    if (line == null) break
                    when {
                        line.startsWith("data:") -> buffer.append(line.removePrefix("data:").trim())
                        line.isBlank() && buffer.isNotEmpty() -> {
                            parseAndApply(buffer.toString())
                            buffer.setLength(0)
                        }
                    }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                Log.w(TAG, "sync stream error: ${error.message}")
                if (!connectedOnce) {
                    _connection.value = ConnectionState.OFFLINE
                    delay(3000)
                }
            } finally {
                try {
                    connection?.disconnect()
                } catch (_: Exception) {
                }
                if (activeConnection === connection) activeConnection = null
            }

            if (coroutineContext.isActive) delay(if (connectedOnce) 800 else 0)
        }
    }

    private fun parseAndApply(raw: String) {
        try {
            val root = JSONObject(raw)
            val notifications = mutableListOf<SyncNotification>()
            val array = root.optJSONArray("notifications")
            if (array != null) {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    notifications.add(
                        SyncNotification(
                            id = item.optString("id"),
                            originDevice = item.optString("originDevice", ROLE_A),
                            appName = item.optString("appName", "Notification"),
                            title = item.optString("title"),
                            content = item.optString("content"),
                            timestamp = item.optString("timestamp"),
                            dismissed = item.optBoolean("dismissed", false),
                            repliedText = item.optString("repliedText").ifBlank { null }
                        )
                    )
                }
            }

            val clipboardJson = root.optJSONObject("clipboard")
            val clipboard = ClipboardSnapshot(
                text = clipboardJson?.optString("text").orEmpty(),
                sourceDevice = clipboardJson?.optString("sourceDevice", ROLE_A) ?: ROLE_A,
                timestamp = clipboardJson?.optString("timestamp").orEmpty()
            )

            val callJson = root.optJSONObject("activeCall")
            val call = CallSnapshot(
                callId = callJson?.optString("callId").orEmpty(),
                originDevice = callJson?.optString("originDevice", ROLE_A) ?: ROLE_A,
                callerName = callJson?.optString("callerName", "Unknown caller")
                    ?: "Unknown caller",
                callerNumber = callJson?.optString("callerNumber").orEmpty(),
                status = callJson?.optString("status", "idle") ?: "idle",
                actionTakenBy = callJson?.optString("actionTakenBy").orEmpty().ifBlank { null },
                timestamp = callJson?.optString("timestamp").orEmpty()
            )

            val parsed = RoomSnapshot(
                room = root.optString("room"),
                updatedAt = root.optString("updatedAt"),
                connectedPeers = root.optInt("connectedPeers", 0),
                clipboard = clipboard,
                activeCall = call,
                notifications = notifications
            )

            _snapshot.value = parsed
            _connection.value = ConnectionState.CONNECTED
            maybeApplyClipboard(clipboard)
        } catch (error: Exception) {
            Log.w(TAG, "could not parse room state: ${error.message}")
        }
    }

    private fun maybeApplyClipboard(clipboard: ClipboardSnapshot) {
        if (clipboard.text.isBlank()) return
        if (clipboard.sourceDevice == prefs.role) return
        if (clipboard.text == lastAppliedClip) return

        lastAppliedClip = clipboard.text
        ClipboardGuard.mark(clipboard.text)
        MainThread.post {
            try {
                val manager =
                    appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                manager.setPrimaryClip(ClipData.newPlainText("ContinuumSync", clipboard.text))
            } catch (error: Exception) {
                Log.w(TAG, "clipboard write failed: ${error.message}")
            }
        }
    }

    suspend fun postEvent(eventType: String, payload: JSONObject): Boolean =
        withContext(Dispatchers.IO) {
            if (!initialized) return@withContext false
            val base = SyncPrefs.normalizeBaseUrl(prefs.baseUrl)
            val room = SyncPrefs.normalizeRoom(prefs.room)
            try {
                val connection = (URL("$base/api/sync/event").openConnection() as HttpURLConnection)
                    .apply {
                        requestMethod = "POST"
                        doOutput = true
                        connectTimeout = 6000
                        readTimeout = 8000
                        setRequestProperty("Content-Type", "application/json")
                    }
                val body = JSONObject()
                    .put("room", room)
                    .put("eventType", eventType)
                    .put("payload", payload)
                    .toString()
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                connection.disconnect()
                code in 200..299
            } catch (error: IOException) {
                Log.w(TAG, "post $eventType failed: ${error.message}")
                false
            } catch (error: Exception) {
                Log.w(TAG, "post $eventType failed: ${error.message}")
                false
            }
        }

    fun postEventAsync(eventType: String, payload: JSONObject) {
        if (!initialized) return
        scope.launch { postEvent(eventType, payload) }
    }

    fun syncClipboard(text: String) {
        if (text.isBlank()) return
        lastAppliedClip = text
        ClipboardGuard.mark(text)
        val payload = JSONObject()
            .put("text", text)
            .put("sourceDevice", prefs.role)
        postEventAsync("CLIPBOARD_SYNC", payload)
    }

    fun dismissNotification(id: String) {
        if (id.isBlank()) return
        postEventAsync("NOTIFICATION_DISMISS", JSONObject().put("id", id))
    }

    fun sendTestAlert() {
        val payload = JSONObject()
            .put("id", "test-${prefs.role}-${System.currentTimeMillis()}")
            .put("originDevice", prefs.role)
            .put("appName", "ContinuumSync")
            .put("title", "Test alert from ${roleLabel(prefs.role)}")
            .put("content", "If this appears on the other phone, the bridge is live.")
        postEventAsync("NOTIFICATION_POST", payload)
    }

    fun sendTestCall() {
        val payload = JSONObject()
            .put("callId", "call-${System.currentTimeMillis()}")
            .put("originDevice", prefs.role)
            .put("callerName", "Test Caller")
            .put("callerNumber", "+1 (415) 555-0134")
            .put("status", "ringing")
        postEventAsync("CALL_ACTION", payload)
    }

    fun respondToCall(call: CallSnapshot, status: String) {
        val payload = JSONObject()
            .put("callId", call.callId.ifBlank { "call-${System.currentTimeMillis()}" })
            .put("originDevice", call.originDevice.ifBlank { prefs.role })
            .put("callerName", call.callerName)
            .put("callerNumber", call.callerNumber)
            .put("status", status)
            .put("actionTakenBy", prefs.role)
        postEventAsync("CALL_ACTION", payload)
    }
}
