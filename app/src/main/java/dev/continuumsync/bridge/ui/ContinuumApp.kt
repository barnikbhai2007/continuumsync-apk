package dev.continuumsync.bridge.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dev.continuumsync.bridge.calls.CallSyncController
import dev.continuumsync.bridge.data.CallSnapshot
import dev.continuumsync.bridge.data.DeviceIdentity
import dev.continuumsync.bridge.data.PairingPayload
import dev.continuumsync.bridge.data.ROLE_A
import dev.continuumsync.bridge.data.ROLE_B
import dev.continuumsync.bridge.data.SyncPrefs
import dev.continuumsync.bridge.data.SyncRepository
import dev.continuumsync.bridge.host.EmbeddedSyncServer
import dev.continuumsync.bridge.ui.theme.Bg
import dev.continuumsync.bridge.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContinuumApp(initialPairingLink: String? = null) {
    val context = LocalContext.current
    val snapshot by SyncRepository.snapshot.collectAsStateWithLifecycle()
    val connection by SyncRepository.connection.collectAsStateWithLifecycle()
    val hostInfo by EmbeddedSyncServer.info.collectAsStateWithLifecycle()
    val joinedHost by SyncRepository.joinedHost.collectAsStateWithLifecycle()

    val prefs = SyncRepository.prefs
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var baseUrl by remember { mutableStateOf(prefs.baseUrl) }
    var room by remember { mutableStateOf(prefs.room) }
    var role by remember { mutableStateOf(prefs.role) }
    var autoDiscover by remember { mutableStateOf(prefs.autoDiscover) }
    var permissionTick by remember { mutableIntStateOf(0) }
    var minimizedCallId by remember { mutableStateOf("") }
    var selectedNotificationId by rememberSaveable { mutableStateOf<String?>(null) }
    var handledPairingLink by remember { mutableStateOf<String?>(null) }

    val notificationsGranted = remember(permissionTick) { hasNotificationAccess(context) }
    val accessibilityGranted = remember(permissionTick) { hasAccessibilityAccess(context) }
    val overlayGranted = remember(permissionTick) { Settings.canDrawOverlays(context) }
    val telephonyAvailable = remember { DeviceIdentity.hasTelephony(context) }
    val callPermissions = remember(telephonyAvailable) {
        requiredCallPermissions(telephonyAvailable)
    }
    val callsGranted = remember(permissionTick, telephonyAvailable) {
        hasPermissions(context, callPermissions)
    }
    val batteryUnrestricted = remember(permissionTick) {
        isIgnoringBatteryOptimizations(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionTick += 1
                EmbeddedSyncServer.refreshAddress()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun applyPairing(raw: String) {
        val payload = PairingPayload.parse(raw)
        if (payload == null) {
            showToast(context, "That code is not a ContinuumSync pairing code")
            return
        }
        val ownUrl = hostInfo.displayUrl
        if (role == ROLE_A && ownUrl != null && payload.hostUrl.isNotBlank() &&
            SyncPrefs.normalizeBaseUrl(payload.hostUrl) == SyncPrefs.normalizeBaseUrl(ownUrl)
        ) {
            showToast(context, "That is this device's own code — scan it on the other device")
            return
        }
        if (payload.room.isNotBlank()) {
            room = SyncPrefs.normalizeRoom(payload.room)
        }
        if (payload.hostUrl.isNotBlank()) {
            baseUrl = SyncPrefs.normalizeBaseUrl(payload.hostUrl)
            autoDiscover = false
            prefs.autoDiscover = false
        } else {
            autoDiscover = true
            prefs.autoDiscover = true
        }
        role = ROLE_B
        SyncRepository.updateConfig(baseUrl, room, role)
        tab = 1
        val name = payload.name.ifBlank { "your other device" }
        showToast(context, "Paired with $name")
    }

    fun answerCall(call: CallSnapshot) {
        minimizedCallId = call.callId
        if (call.originDevice == role) {
            CallSyncController(context.applicationContext).answerCall()
            SyncRepository.respondToCall(call, "active-on-origin")
        } else {
            SyncRepository.respondToCall(call, "answer-requested")
        }
    }

    fun rejectCall(call: CallSnapshot) {
        minimizedCallId = call.callId
        if (call.originDevice == role) {
            CallSyncController(context.applicationContext).endCall(reject = true)
        }
        SyncRepository.respondToCall(call, "rejected")
    }

    fun endCall(call: CallSnapshot) {
        minimizedCallId = call.callId
        if (call.originDevice == role) {
            CallSyncController(context.applicationContext).endCall()
        }
        SyncRepository.respondToCall(call, "ended")
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (!contents.isNullOrBlank()) {
            applyPairing(contents)
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scanLauncher.launch(scanOptions())
        } else {
            showToast(context, "Camera permission is needed to scan the code")
        }
    }
    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionTick += 1 }

    val startScan: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            scanLauncher.launch(scanOptions())
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(initialPairingLink) {
        val link = initialPairingLink
        if (!link.isNullOrBlank() && link != handledPairingLink) {
            handledPairingLink = link
            applyPairing(link)
        }
    }

    LaunchedEffect(selectedNotificationId, snapshot) {
        val id = selectedNotificationId ?: return@LaunchedEffect
        if (snapshot?.notifications?.none { it.id == id } == true) {
            selectedNotificationId = null
        }
    }

    val peers = snapshot?.connectedPeers ?: 0
    val peerDevices = snapshot?.peers.orEmpty()
    val activeCall = snapshot?.activeCall

    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                ContinuumBottomBar(selected = tab, onSelect = { tab = it })
            }
        ) { innerPadding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Primary.copy(alpha = 0.075f), Color.Transparent)
                            )
                        )
                )

                when (tab) {
                    0 -> FeedScreen(
                        snapshot = snapshot,
                        connection = connection,
                        myRole = role,
                        peerDevices = peerDevices,
                        notificationsGranted = notificationsGranted,
                        overlayGranted = overlayGranted,
                        onDismiss = { item ->
                            SyncRepository.dismissNotification(item)
                            showToast(context, "Dismissed on both devices")
                        },
                        onOpenDetail = { item -> selectedNotificationId = item.id },
                        onAnswerCall = { call -> answerCall(call) },
                        onRejectCall = { call -> rejectCall(call) },
                        onEndCall = { call -> endCall(call) },
                        onRefresh = {
                            SyncRepository.start()
                            EmbeddedSyncServer.refreshAddress()
                            showToast(context, "Reconnecting…")
                        },
                        onOpenNotificationAccess = {
                            openNotificationSettings(context)
                        },
                        onOpenOverlay = { openOverlaySettings(context) },
                        onOpenDevices = { tab = 1 }
                    )

                    1 -> DevicesScreen(
                        room = room,
                        role = role,
                        baseUrl = baseUrl,
                        connection = connection,
                        peers = peers,
                        peerDevices = peerDevices,
                        hostUrl = hostInfo.displayUrl,
                        hostError = hostInfo.error,
                        joinedHost = joinedHost,
                        onScanQr = startScan,
                        onCopyRoom = {
                            copyToClipboard(context, "ContinuumSync pairing code", room)
                            showToast(context, "Pairing code copied")
                        },
                        onShareCode = {
                            val host = hostInfo.displayUrl
                            if (host.isNullOrBlank()) {
                                showToast(context, "Connect this device to Wi-Fi first")
                            } else {
                                val payload = PairingPayload.build(
                                    room,
                                    host,
                                    DeviceIdentity.displayName
                                )
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "ContinuumSync pairing")
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Pair ContinuumSync instantly:\n$payload"
                                    )
                                }
                                context.startActivity(
                                    Intent.createChooser(share, "Share pairing code")
                                )
                            }
                        },
                        onCopyHost = { host ->
                            copyToClipboard(context, "ContinuumSync host", host)
                            showToast(context, "Host address copied")
                        },
                        onApplyManual = { manualRoom, manualHost ->
                            room = SyncPrefs.normalizeRoom(manualRoom)
                            if (manualHost.isNotBlank()) {
                                baseUrl = SyncPrefs.normalizeBaseUrl(manualHost)
                                autoDiscover = false
                            } else {
                                autoDiscover = true
                            }
                            prefs.autoDiscover = autoDiscover
                            role = ROLE_B
                            SyncRepository.updateConfig(baseUrl, room, role)
                            showToast(context, "Connecting to room $room")
                        },
                        onToggleRole = {
                            val next = if (role == ROLE_A) ROLE_B else ROLE_A
                            role = next
                            if (next == ROLE_B) {
                                autoDiscover = true
                                prefs.autoDiscover = true
                            }
                            SyncRepository.setRole(next)
                            showToast(
                                context,
                                if (next == ROLE_A) {
                                    "This device now hosts the room"
                                } else {
                                    "Looking for the host on Wi-Fi"
                                }
                            )
                        },
                        onRetry = {
                            SyncRepository.start()
                            EmbeddedSyncServer.refreshAddress()
                            showToast(context, "Reconnecting…")
                        },
                        onOpenSettings = { tab = 2 }
                    )

                    else -> SettingsScreen(
                        initialRoom = room,
                        initialBaseUrl = baseUrl,
                        initialRole = role,
                        initialAutoDiscover = autoDiscover,
                        connection = connection,
                        peers = peers,
                        hostUrl = hostInfo.displayUrl,
                        joinedHost = joinedHost,
                        notificationsGranted = notificationsGranted,
                        overlayGranted = overlayGranted,
                        accessibilityGranted = accessibilityGranted,
                        callsGranted = callsGranted,
                        telephonyAvailable = telephonyAvailable,
                        batteryUnrestricted = batteryUnrestricted,
                        onSave = { nextRoom, nextUrl, nextRole, nextAuto ->
                            room = SyncPrefs.normalizeRoom(nextRoom)
                            baseUrl = SyncPrefs.normalizeBaseUrl(nextUrl)
                            role = nextRole
                            autoDiscover = nextAuto
                            prefs.autoDiscover = nextAuto
                            SyncRepository.updateConfig(baseUrl, room, role)
                            showToast(
                                context,
                                if (role == ROLE_A) {
                                    "Hosting room $room"
                                } else {
                                    "Joining room $room"
                                }
                            )
                        },
                        onOpenNotificationAccess = {
                            openNotificationSettings(context)
                        },
                        onOpenOverlay = { openOverlaySettings(context) },
                        onOpenAccessibility = {
                            openAccessibilitySettings(context)
                        },
                        onRequestCalls = {
                            if (callPermissions.isEmpty()) {
                                showToast(context, "This device has no phone radio")
                            } else {
                                callPermissionLauncher.launch(callPermissions)
                            }
                        },
                        onTestAlert = {
                            SyncRepository.sendTestAlert()
                            showToast(context, "Test alert sent to the room")
                        },
                        onSyncClipboard = {
                            val text = readClipboard(context)
                            if (text.isNullOrBlank()) {
                                showToast(context, "Clipboard is empty")
                            } else {
                                SyncRepository.syncClipboard(text)
                                showToast(context, "Clipboard sent to the room")
                            }
                        },
                        onTestCall = {
                            SyncRepository.sendTestCall()
                            showToast(context, "Test call ringing in the room")
                        },
                        onOpenBattery = { requestIgnoreBatteryOptimizations(context) },
                        onOpenAutoLaunch = { openAutoLaunchSettings(context) }
                    )
                }
            }
        }

        val ringingCall = activeCall?.takeIf {
            it.isRinging && it.callId != minimizedCallId
        }
        if (ringingCall != null) {
            CallScreen(
                call = ringingCall,
                myRole = role,
                onAnswer = { answerCall(ringingCall) },
                onDecline = { rejectCall(ringingCall) },
                onEnd = { endCall(ringingCall) },
                onClose = { minimizedCallId = ringingCall.callId }
            )
        }

        val selected = selectedNotificationId?.let { id ->
            snapshot?.notifications?.firstOrNull { it.id == id }
        }
        if (selected != null) {
            NotificationDetailSheet(
                item = selected,
                myRole = role,
                peerDevices = peerDevices,
                onDismissRequest = { selectedNotificationId = null },
                onReply = { text ->
                    SyncRepository.replyToNotification(selected, text)
                    selectedNotificationId = null
                    showToast(context, "Reply sent")
                },
                onDismissBoth = {
                    SyncRepository.dismissNotification(selected)
                    selectedNotificationId = null
                    showToast(context, "Dismissed on both devices")
                }
            )
        }
    }
}

private fun scanOptions(): ScanOptions = ScanOptions().apply {
    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
    setPrompt("Scan the QR code on the other device")
    setBeepEnabled(false)
    setOrientationLocked(false)
    setBarcodeImageEnabled(false)
}
