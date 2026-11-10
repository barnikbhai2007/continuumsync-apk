package dev.continuumsync.bridge.host

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import dev.continuumsync.bridge.data.CallSnapshot
import dev.continuumsync.bridge.data.ClipboardSnapshot
import dev.continuumsync.bridge.data.ROLE_A
import dev.continuumsync.bridge.data.ROLE_B
import dev.continuumsync.bridge.data.SyncNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.coroutineContext

data class HostInfo(
    val port: Int? = null,
    val address: String? = null,
    val error: String? = null
) {
    val displayUrl: String?
        get() = if (port != null && !address.isNullOrBlank()) "http://$address:$port" else null
}

/**
 * A tiny HTTP/SSE server that runs on the phone that owns the room.
 * It speaks the same API as the ContinuumSync web server, so the rest of the
 * Android app does not need to know whether the room is local or remote.
 */
object EmbeddedSyncServer {

    private const val TAG = "ContinuumSyncHost"
    const val DISCOVERY_PORT = 3102
    private const val FIRST_PORT = 3100
    private const val LAST_PORT = 3110
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss")

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateLock = Any()
    private val writeLock = Any()

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var heartbeatJob: Job? = null
    private var discoveryJob: Job? = null
    private var discoverySocket: DatagramSocket? = null

    private var roomName: String = ""
    private var port: Int = 0

    private val clients = mutableSetOf<HostClient>()
    private val notifications = mutableListOf<SyncNotification>()
    private var clipboard = ClipboardSnapshot()
    private var activeCall = CallSnapshot()
    private var updatedAt = now()

    private val _info = MutableStateFlow(HostInfo())
    val info = _info

    private data class HostClient(
        val socket: Socket,
        val output: OutputStream,
        val device: String?
    )

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun isRunning(): Boolean = serverSocket?.isClosed == false

    suspend fun ensureStarted(room: String): Int? = withContext(Dispatchers.IO) {
        val normalized = room.trim().ifBlank { "482-910" }
        synchronized(stateLock) {
            val socket = serverSocket
            if (socket != null && !socket.isClosed && roomName == normalized) {
                return@withContext port
            }
        }
        stop()
        start(normalized)
        port.takeIf { it > 0 }
    }

    fun stop() {
        try {
            acceptJob?.cancel()
        } catch (_: Exception) {
        }
        try {
            heartbeatJob?.cancel()
        } catch (_: Exception) {
        }
        try {
            discoveryJob?.cancel()
        } catch (_: Exception) {
        }
        acceptJob = null
        heartbeatJob = null
        discoveryJob = null

        try {
            discoverySocket?.close()
        } catch (_: Exception) {
        }
        discoverySocket = null

        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        port = 0
        roomName = ""

        val stale = synchronized(stateLock) {
            val copy = clients.toList()
            clients.clear()
            notifications.clear()
            clipboard = ClipboardSnapshot()
            activeCall = CallSnapshot()
            copy
        }
        for (client in stale) {
            try {
                client.socket.close()
            } catch (_: Exception) {
            }
        }
        _info.value = HostInfo()
    }

    fun refreshAddress() {
        val current = _info.value
        if (current.port != null) {
            _info.value = current.copy(address = localIpv4())
        }
    }

    private fun start(room: String) {
        val socket = bindServerSocket()
        if (socket == null) {
            _info.value = HostInfo(error = "No free port on this phone")
            return
        }

        serverSocket = socket
        port = socket.localPort
        roomName = room
        synchronized(stateLock) {
            clients.clear()
            notifications.clear()
            clipboard = ClipboardSnapshot()
            activeCall = CallSnapshot()
            updatedAt = now()
        }
        _info.value = HostInfo(port = port, address = localIpv4())
        acceptJob = scope.launch { acceptLoop(socket) }
        heartbeatJob = scope.launch { heartbeatLoop() }
        startDiscoveryResponder(room, port)
        Log.i(TAG, "Hosting room $room on port $port")
    }

    private fun bindServerSocket(): ServerSocket? {
        for (candidate in FIRST_PORT..LAST_PORT) {
            try {
                return ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress("0.0.0.0", candidate))
                }
            } catch (_: IOException) {
            }
        }
        return null
    }

    private suspend fun acceptLoop(socket: ServerSocket) {
        while (coroutineContext.isActive) {
            val client = try {
                socket.accept()
            } catch (_: Exception) {
                break
            }
            scope.launch { handleClient(client) }
        }
    }

    private fun handleClient(socket: Socket) {
        var keepOpen = false
        try {
            socket.soTimeout = 15000
            val input = BufferedInputStream(socket.getInputStream())
            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val target = parts[1]
            val headers = readHeaders(input)
            val path = target.substringBefore('?')
            val query = parseQuery(target.substringAfter('?', ""))
            val output = socket.getOutputStream()

            when {
                method == "OPTIONS" -> respond(output, 204, "", "text/plain")
                method == "GET" && path == "/api/sync/state" ->
                    respond(output, 200, stateJson(), "application/json")
                method == "GET" && path == "/api/sync/info" ->
                    respond(output, 200, infoJson(), "application/json")
                method == "GET" && path == "/api/sync/stream" -> {
                    keepOpen = true
                    val device = normalizeDevice(query["device"])
                    registerStream(socket, output, device)
                }
                method == "POST" && path == "/api/sync/event" -> {
                    val length = headers["content-length"]?.toIntOrNull() ?: 0
                    val body = readBody(input, length)
                    respond(output, 200, applyEvent(body), "application/json")
                }
                else -> respond(
                    output,
                    404,
                    """{"error":"not found"}""",
                    "application/json"
                )
            }
        } catch (error: Exception) {
            Log.w(TAG, "request failed: ${error.message}")
        } finally {
            if (!keepOpen) {
                try {
                    socket.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun registerStream(socket: Socket, output: OutputStream, device: String?) {
        val client = HostClient(socket, output, device)
        synchronized(stateLock) { clients.add(client) }

        val headers = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/event-stream\r\n")
            append("Cache-Control: no-cache\r\n")
            append("Connection: keep-alive\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("\r\n")
        }
        try {
            output.write(headers.toByteArray(Charsets.UTF_8))
            output.flush()
        } catch (_: Exception) {
            removeClient(client)
            return
        }
        broadcastState()
    }

    private suspend fun heartbeatLoop() {
        while (coroutineContext.isActive) {
            try {
                delay(12000)
            } catch (_: Exception) {
                break
            }
            broadcastRaw(": ping\n\n")
        }
    }

    private fun applyEvent(body: String): String {
        val root = try {
            JSONObject(body)
        } catch (_: Exception) {
            return """{"ok":false,"error":"invalid json"}"""
        }
        if (root.optString("room").isNotBlank() && root.optString("room") != roomName) {
            return """{"ok":false,"error":"wrong room"}"""
        }
        val type = root.optString("eventType")
        val payload = root.optJSONObject("payload") ?: JSONObject()

        synchronized(stateLock) {
            updatedAt = now()
            when (type) {
                "CLIPBOARD_SYNC" -> {
                    val text = payload.optString("text")
                    if (text.isNotBlank()) {
                        clipboard = ClipboardSnapshot(
                            text = text,
                            sourceDevice = normalizeDevice(payload.optString("sourceDevice")) ?: ROLE_A,
                            timestamp = updatedAt
                        )
                    }
                }
                "NOTIFICATION_POST" -> {
                    val title = payload.optString("title")
                    if (title.isNotBlank()) {
                        val id = payload.optString("id")
                            .ifBlank { "live-notif-${System.currentTimeMillis()}" }
                        if (notifications.none { it.id == id }) {
                            notifications.add(
                                0,
                                SyncNotification(
                                    id = id,
                                    originDevice = normalizeDevice(payload.optString("originDevice")) ?: ROLE_A,
                                    appName = payload.optString("appName").ifBlank { "Notification" },
                                    title = title,
                                    content = payload.optString("content"),
                                    timestamp = updatedAt,
                                    dismissed = false
                                )
                            )
                            trimNotifications()
                        }
                    }
                }
                "NOTIFICATION_DISMISS" -> {
                    val id = payload.optString("id")
                    if (id.isNotBlank()) {
                        val index = notifications.indexOfFirst { it.id == id }
                        if (index >= 0) {
                            notifications[index] = notifications[index].copy(dismissed = true)
                        }
                    }
                }
                "NOTIFICATION_REPLY" -> {
                    val id = payload.optString("id")
                    val reply = payload.optString("replyText")
                    if (id.isNotBlank() && reply.isNotBlank()) {
                        val index = notifications.indexOfFirst { it.id == id }
                        if (index >= 0) {
                            notifications[index] = notifications[index].copy(repliedText = reply)
                        }
                    }
                }
                "CALL_ACTION" -> {
                    val status = payload.optString("status")
                    if (status.isNotBlank()) {
                        val current = activeCall
                        activeCall = CallSnapshot(
                            callId = payload.optString("callId")
                                .ifBlank { current.callId.ifBlank { "call-${System.currentTimeMillis()}" } },
                            originDevice = normalizeDevice(payload.optString("originDevice"))
                                ?: current.originDevice.ifBlank { ROLE_A },
                            callerName = payload.optString("callerName")
                                .ifBlank { current.callerName.ifBlank { "Unknown caller" } },
                            callerNumber = payload.optString("callerNumber")
                                .ifBlank { current.callerNumber },
                            status = status,
                            actionTakenBy = normalizeDevice(payload.optString("actionTakenBy")),
                            timestamp = updatedAt
                        )
                    }
                }
            }
        }
        broadcastState()
        return """{"ok":true}"""
    }

    private fun trimNotifications() {
        if (notifications.size > 60) {
            val removeCount = notifications.size - 60
            repeat(removeCount) {
                if (notifications.isNotEmpty()) notifications.removeAt(notifications.lastIndex)
            }
        }
    }

    private fun broadcastState() {
        synchronized(writeLock) {
            val payload = synchronized(stateLock) { "data: ${stateJsonLocked()}\n\n" }
            writeToAll(payload)
        }
    }

    private fun broadcastRaw(payload: String) {
        synchronized(writeLock) { writeToAll(payload) }
    }

    private fun writeToAll(payload: String) {
        val snapshot = synchronized(stateLock) { clients.toList() }
        if (snapshot.isEmpty()) return
        val bytes = payload.toByteArray(Charsets.UTF_8)
        val dead = mutableListOf<HostClient>()
        for (client in snapshot) {
            try {
                synchronized(client) {
                    client.output.write(bytes)
                    client.output.flush()
                }
            } catch (_: Exception) {
                dead.add(client)
            }
        }
        for (client in dead) removeClient(client)
    }

    private fun removeClient(client: HostClient) {
        val removed = synchronized(stateLock) { clients.remove(client) }
        try {
            client.socket.close()
        } catch (_: Exception) {
        }
        if (removed) {
            // The peer count changed, so everyone gets a fresh snapshot.
            broadcastState()
        }
    }

    private fun stateJson(): String = synchronized(stateLock) { stateJsonLocked() }

    private fun stateJsonLocked(): String {
        val root = JSONObject()
        root.put("room", roomName)
        root.put("updatedAt", updatedAt)
        root.put("connectedPeers", linkedDeviceCountLocked())

        root.put(
            "clipboard",
            JSONObject()
                .put("text", clipboard.text)
                .put("sourceDevice", clipboard.sourceDevice)
                .put("timestamp", clipboard.timestamp)
        )

        val call = JSONObject()
            .put("callId", activeCall.callId)
            .put("originDevice", activeCall.originDevice)
            .put("callerName", activeCall.callerName)
            .put("callerNumber", activeCall.callerNumber)
            .put("status", activeCall.status)
            .put("timestamp", activeCall.timestamp)
        activeCall.actionTakenBy?.let { call.put("actionTakenBy", it) }
        root.put("activeCall", call)

        val array = JSONArray()
        for (item in notifications) {
            val json = JSONObject()
                .put("id", item.id)
                .put("originDevice", item.originDevice)
                .put("appName", item.appName)
                .put("title", item.title)
                .put("content", item.content)
                .put("timestamp", item.timestamp)
                .put("dismissed", item.dismissed)
            item.repliedText?.let { json.put("repliedText", it) }
            array.put(json)
        }
        root.put("notifications", array)
        root.put("relayedCiphertexts", JSONArray())
        return root.toString()
    }

    private fun linkedDeviceCountLocked(): Int {
        val devices = mutableSetOf<String>()
        for (client in clients) {
            val device = client.device ?: continue
            devices.add(device)
        }
        return devices.size
    }

    private fun infoJson(): String {
        val current = _info.value
        return JSONObject()
            .put("app", "ContinuumSync")
            .put("room", roomName)
            .put("port", port)
            .put("address", current.address ?: "")
            .put("connectedPeers", synchronized(stateLock) { linkedDeviceCountLocked() })
            .toString()
    }

    private fun respond(
        output: OutputStream,
        code: Int,
        body: String,
        contentType: String
    ) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (code) {
            200 -> "OK"
            204 -> "No Content"
            404 -> "Not Found"
            else -> "OK"
        }
        val header = buildString {
            append("HTTP/1.1 $code $reason\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Content-Type\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        output.write(header.toByteArray(Charsets.UTF_8))
        if (bytes.isNotEmpty()) output.write(bytes)
        output.flush()
    }

    private fun readHeaders(input: InputStream): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        var total = 0
        while (total < 16384) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            total += line.length
            val colon = line.indexOf(':')
            if (colon > 0) {
                headers[line.substring(0, colon).trim().lowercase()] =
                    line.substring(colon + 1).trim()
            }
        }
        return headers
    }

    private fun readBody(input: InputStream, length: Int): String {
        if (length <= 0) return ""
        val safeLength = length.coerceAtMost(1_048_576)
        val bytes = ByteArray(safeLength)
        var read = 0
        while (read < safeLength) {
            val count = input.read(bytes, read, safeLength - read)
            if (count <= 0) break
            read += count
        }
        return String(bytes, 0, read, Charsets.UTF_8)
    }

    private fun readLine(input: InputStream): String? {
        val builder = StringBuilder()
        while (builder.length < 8192) {
            val value = input.read()
            if (value == -1) return if (builder.isEmpty()) null else builder.toString()
            if (value == '\n'.code) return builder.toString().trimEnd('\r')
            builder.append(value.toChar())
        }
        return builder.toString()
    }

    private fun parseQuery(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        for (part in raw.split('&')) {
            if (part.isBlank()) continue
            val key = part.substringBefore('=').trim()
            val value = part.substringAfter('=', "").trim()
            if (key.isNotEmpty()) result[key] = value
        }
        return result
    }

    private fun normalizeDevice(raw: String?): String? {
        val value = raw?.trim()?.lowercase()
        return when (value) {
            ROLE_A -> ROLE_A
            ROLE_B -> ROLE_B
            else -> null
        }
    }

    private fun localIpv4(): String? {
        if (!::appContext.isInitialized) return null
        return try {
            val manager = appContext.getSystemService(ConnectivityManager::class.java)
                ?: return null
            val network = manager.activeNetwork ?: return null
            val properties = manager.getLinkProperties(network) ?: return null
            properties.linkAddresses
                .asSequence()
                .map { it.address }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress
        } catch (_: Exception) {
            null
        }
    }

    private fun startDiscoveryResponder(room: String, httpPort: Int) {
        discoveryJob = scope.launch {
            val socket = try {
                DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(DISCOVERY_PORT))
                }
            } catch (_: Exception) {
                return@launch
            }
            discoverySocket = socket
            val buffer = ByteArray(512)
            while (isActive) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val message = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    if (message == "CONTINUUM_DISCOVER:$room") {
                        val reply = "CONTINUUM_HOST:$room:$httpPort"
                        val bytes = reply.toByteArray(Charsets.UTF_8)
                        socket.send(
                            DatagramPacket(bytes, bytes.size, packet.address, packet.port)
                        )
                    }
                } catch (_: SocketException) {
                    break
                } catch (_: Exception) {
                    // Ignore malformed packets and keep listening.
                }
            }
        }
    }

    private fun now(): String = LocalTime.now().format(timeFormat)
}
