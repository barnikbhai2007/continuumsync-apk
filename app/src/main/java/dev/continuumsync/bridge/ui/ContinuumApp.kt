package dev.continuumsync.bridge.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.continuumsync.bridge.R
import dev.continuumsync.bridge.data.CallSnapshot
import dev.continuumsync.bridge.data.ClipboardSnapshot
import dev.continuumsync.bridge.data.ConnectionState
import dev.continuumsync.bridge.data.ROLE_A
import dev.continuumsync.bridge.data.ROLE_B
import dev.continuumsync.bridge.data.SyncNotification
import dev.continuumsync.bridge.data.SyncPrefs
import dev.continuumsync.bridge.data.SyncRepository
import dev.continuumsync.bridge.data.peerRole
import dev.continuumsync.bridge.data.roleLabel
import dev.continuumsync.bridge.host.EmbeddedSyncServer
import dev.continuumsync.bridge.ui.theme.Amber
import dev.continuumsync.bridge.ui.theme.Bg
import dev.continuumsync.bridge.ui.theme.Card
import dev.continuumsync.bridge.ui.theme.CardSoft
import dev.continuumsync.bridge.ui.theme.Cyan
import dev.continuumsync.bridge.ui.theme.Danger
import dev.continuumsync.bridge.ui.theme.Mint
import dev.continuumsync.bridge.ui.theme.Primary
import dev.continuumsync.bridge.ui.theme.Stroke
import dev.continuumsync.bridge.ui.theme.TextHi
import dev.continuumsync.bridge.ui.theme.TextLow
import dev.continuumsync.bridge.ui.theme.TextMid
import dev.continuumsync.bridge.ui.theme.Violet
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContinuumApp() {
    val context = LocalContext.current
    val snapshot by SyncRepository.snapshot.collectAsStateWithLifecycle()
    val connection by SyncRepository.connection.collectAsStateWithLifecycle()
    val hostInfo by EmbeddedSyncServer.info.collectAsStateWithLifecycle()
    val joinedHost by SyncRepository.joinedHost.collectAsStateWithLifecycle()

    val prefs = SyncRepository.prefs
    var baseUrl by remember { mutableStateOf(prefs.baseUrl) }
    var room by remember { mutableStateOf(prefs.room) }
    var role by remember { mutableStateOf(prefs.role) }
    var showSettings by remember { mutableStateOf(false) }
    var permissionTick by remember { mutableIntStateOf(0) }

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

    val notificationsGranted = remember(permissionTick) { hasNotificationAccess(context) }
    val accessibilityGranted = remember(permissionTick) { hasAccessibilityAccess(context) }
    val callPermissions = remember { requiredCallPermissions() }
    val callsGranted = remember(permissionTick) { hasPermissions(context, callPermissions) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionTick += 1 }

    val peers = snapshot?.connectedPeers ?: 0
    val activeNotifications = snapshot?.notifications.orEmpty().filter { !it.dismissed }
    val activeCall = snapshot?.activeCall

    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Primary.copy(alpha = 0.20f), Color.Transparent)
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            TopBar(
                connection = connection,
                peers = peers,
                onSettings = { showSettings = true }
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 30.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    PairingCard(
                        room = room,
                        role = role,
                        baseUrl = baseUrl,
                        connection = connection,
                        peers = peers,
                        hostUrl = hostInfo.displayUrl,
                        hostError = hostInfo.error,
                        joinedHost = joinedHost,
                        onToggleRole = {
                            val next = if (role == ROLE_A) ROLE_B else ROLE_A
                            role = next
                            SyncRepository.setRole(next)
                            Toast.makeText(
                                context,
                                if (next == ROLE_A) {
                                    "This phone now hosts the room"
                                } else {
                                    "This phone now joins Phone A"
                                },
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onScan = {
                            SyncRepository.scanForHost()
                            Toast.makeText(context, "Scanning for Phone A...", Toast.LENGTH_SHORT)
                                .show()
                        },
                        onEdit = { showSettings = true }
                    )
                }

                item {
                    QuickActions(
                        onTestAlert = {
                            SyncRepository.sendTestAlert()
                            Toast.makeText(context, "Test alert sent to the room", Toast.LENGTH_SHORT).show()
                        },
                        onSyncClipboard = {
                            val text = readClipboard(context)
                            if (text.isNullOrBlank()) {
                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                            } else {
                                SyncRepository.syncClipboard(text)
                                Toast.makeText(context, "Clipboard sent to the room", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onTestCall = {
                            SyncRepository.sendTestCall()
                            Toast.makeText(context, "Test call ringing in the room", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                if (activeCall != null && activeCall.status != "idle" && !activeCall.isFinished) {
                    item {
                        CallCard(
                            call = activeCall,
                            myRole = role,
                            onAnswer = {
                                val status = if (activeCall.originDevice == role) {
                                    "active-on-origin"
                                } else {
                                    "active-bthfp-routed"
                                }
                                SyncRepository.respondToCall(activeCall, status)
                            },
                            onReject = { SyncRepository.respondToCall(activeCall, "rejected") },
                            onEnd = { SyncRepository.respondToCall(activeCall, "ended") }
                        )
                    }
                }

                item {
                    ClipboardCard(
                        clipboard = snapshot?.clipboard,
                        myRole = role,
                        onCopy = {
                            val text = snapshot?.clipboard?.text.orEmpty()
                            if (text.isNotBlank()) {
                                val manager =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                manager.setPrimaryClip(
                                    android.content.ClipData.newPlainText("ContinuumSync", text)
                                )
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                item {
                    FeedHeader(
                        count = activeNotifications.size,
                        updatedAt = snapshot?.updatedAt.orEmpty()
                    )
                }

                if (activeNotifications.isEmpty()) {
                    item { EmptyFeed(connection = connection) }
                } else {
                    items(activeNotifications, key = { it.id }) { item ->
                        NotificationCard(
                            item = item,
                            myRole = role,
                            onDismiss = { SyncRepository.dismissNotification(item.id) }
                        )
                    }
                }

                item {
                    PermissionsCard(
                        notificationsGranted = notificationsGranted,
                        accessibilityGranted = accessibilityGranted,
                        callsGranted = callsGranted,
                        onOpenNotifications = { openNotificationSettings(context) },
                        onOpenAccessibility = { openAccessibilitySettings(context) },
                        onRequestCalls = { permissionLauncher.launch(callPermissions) }
                    )
                }

                item {
                    Footer(
                        role = role,
                        baseUrl = baseUrl,
                        hostUrl = hostInfo.displayUrl,
                        joinedHost = joinedHost,
                        connection = connection,
                        onRetry = { SyncRepository.start() }
                    )
                }
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            initialUrl = baseUrl,
            initialRoom = room,
            initialRole = role,
            initialAutoDiscover = prefs.autoDiscover,
            onDismiss = { showSettings = false },
            onSave = { nextUrl, nextRoom, nextRole, nextAutoDiscover ->
                prefs.autoDiscover = nextAutoDiscover
                baseUrl = SyncPrefs.normalizeBaseUrl(nextUrl)
                room = SyncPrefs.normalizeRoom(nextRoom)
                role = nextRole
                SyncRepository.updateConfig(baseUrl, room, role)
                showSettings = false
                val message = if (role == ROLE_A) {
                    "Hosting room $room on this phone"
                } else {
                    "Joining room $room over Wi-Fi"
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun TopBar(connection: ConnectionState, peers: Int, onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_logo_badge),
            contentDescription = "ContinuumSync logo",
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text("ContinuumSync", style = MaterialTheme.typography.titleLarge, color = TextHi)
            Text("two phones · one flow", style = MaterialTheme.typography.bodyMedium, color = TextLow)
        }
        StatusPill(connection = connection, peers = peers)
        Spacer(Modifier.width(2.dp))
        IconButton(onClick = onSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = TextMid)
        }
    }
}

@Composable
private fun StatusPill(connection: ConnectionState, peers: Int) {
    val label: String
    val color: Color
    when {
        connection == ConnectionState.CONNECTED && peers >= 2 -> {
            label = "Linked"
            color = Mint
        }
        connection == ConnectionState.CONNECTED -> {
            label = "Waiting"
            color = Amber
        }
        connection == ConnectionState.CONNECTING -> {
            label = "Connecting"
            color = Primary
        }
        connection == ConnectionState.OFFLINE -> {
            label = "Offline"
            color = Danger
        }
        else -> {
            label = "Idle"
            color = TextLow
        }
    }

    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PulsingDot(color = color, animate = connection != ConnectionState.CONNECTED || peers < 2)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun PulsingDot(color: Color, animate: Boolean) {
    val transition = rememberInfiniteTransition(label = "dot")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (animate) 0.3f else 1f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "dotAlpha"
    )
    Box(
        Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
private fun PairingCard(
    room: String,
    role: String,
    baseUrl: String,
    connection: ConnectionState,
    peers: Int,
    hostUrl: String?,
    hostError: String?,
    joinedHost: String,
    onToggleRole: () -> Unit,
    onScan: () -> Unit,
    onEdit: () -> Unit
) {
    val context = LocalContext.current
    val isHost = role == ROLE_A
    val remoteLabel = joinedHost.ifBlank {
        if (isLoopbackAddress(baseUrl)) "" else hostLabel(baseUrl)
    }

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (isHost) "HOSTING ROOM" else "PAIRING ROOM",
                style = MaterialTheme.typography.labelSmall,
                color = TextLow
            )
            Spacer(Modifier.weight(1f))
            Pill(
                text = "This phone · ${if (isHost) "A · host" else "B"}",
                color = if (isHost) Mint else Primary
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            room,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 34.sp,
                letterSpacing = 3.sp
            ),
            color = TextHi
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (isHost) {
                "This phone owns the room. The other phone joins it directly over Wi-Fi - no computer needed."
            } else {
                "Joining the room hosted by Phone A on the same Wi-Fi."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextLow
        )

        Spacer(Modifier.height(16.dp))
        if (isHost) {
            if (hostUrl != null) {
                Text("HOST ADDRESS", style = MaterialTheme.typography.labelSmall, color = TextLow)
                Spacer(Modifier.height(7.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(13.dp))
                        .background(CardSoft)
                        .border(1.dp, Stroke, RoundedCornerShape(13.dp))
                        .padding(start = 13.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        hostUrl,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        ),
                        color = TextHi,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as ClipboardManager
                            manager.setPrimaryClip(
                                android.content.ClipData.newPlainText(
                                    "ContinuumSync host",
                                    hostUrl
                                )
                            )
                            Toast.makeText(context, "Host address copied", Toast.LENGTH_SHORT)
                                .show()
                        }
                    ) {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            contentDescription = "Copy host address",
                            tint = Mint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "On the other phone choose Phone B. It finds this address automatically, or you can type it in Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextLow
                )
            } else {
                Text(
                    hostError ?: "Connect this phone to Wi-Fi to start hosting the room.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Amber
                )
            }
        } else {
            Text("CONNECTED THROUGH", style = MaterialTheme.typography.labelSmall, color = TextLow)
            Spacer(Modifier.height(7.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(CardSoft)
                    .border(1.dp, Stroke, RoundedCornerShape(13.dp))
                    .padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    remoteLabel.ifBlank { "Searching for Phone A..." },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    ),
                    color = if (remoteLabel.isBlank()) Amber else TextHi,
                    modifier = Modifier.weight(1f)
                )
                if (remoteLabel.isBlank()) {
                    TextButton(onClick = onScan) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("Scan", color = Primary)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Keep both phones on the same Wi-Fi and Phone A open. If Phone A changes, tap Scan or enter its address in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextLow
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip(
                icon = Icons.Rounded.Devices,
                text = when {
                    peers >= 2 -> "$peers phones linked"
                    isHost -> "Waiting for Phone B"
                    else -> "Looking for Phone A"
                },
                color = if (peers >= 2) Mint else Amber
            )
            InfoChip(
                icon = Icons.Rounded.Wifi,
                text = if (isHost) {
                    if (hostUrl != null) "Hosting on Wi-Fi" else "No Wi-Fi"
                } else {
                    remoteLabel.ifBlank { "Same Wi-Fi" }
                },
                color = if (connection == ConnectionState.OFFLINE) Danger else TextMid
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onToggleRole,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Primary,
                    contentColor = Color(0xFF07132C)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Rounded.Smartphone,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(if (isHost) "Switch to Phone B" else "Switch to Phone A")
            }
            TextButton(onClick = onEdit) {
                Text("Settings", color = TextMid)
            }
        }
    }
}

@Composable
private fun QuickActions(
    onTestAlert: () -> Unit,
    onSyncClipboard: () -> Unit,
    onTestCall: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickAction(
            icon = Icons.Rounded.Notifications,
            label = "Test alert",
            color = Primary,
            onClick = onTestAlert,
            modifier = Modifier.weight(1f)
        )
        QuickAction(
            icon = Icons.Rounded.ContentPaste,
            label = "Clipboard",
            color = Mint,
            onClick = onSyncClipboard,
            modifier = Modifier.weight(1f)
        )
        QuickAction(
            icon = Icons.Rounded.Phone,
            label = "Test call",
            color = Amber,
            onClick = onTestCall,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Card)
            .border(1.dp, Stroke, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = TextHi)
    }
}

@Composable
private fun CallCard(
    call: CallSnapshot,
    myRole: String,
    onAnswer: () -> Unit,
    onReject: () -> Unit,
    onEnd: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2A1320), Card)))
            .border(1.dp, Danger.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Danger.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Phone, contentDescription = null, tint = Danger)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (call.isRinging) "INCOMING CALL" else "CALL IN PROGRESS",
                    style = MaterialTheme.typography.labelSmall,
                    color = Danger
                )
                Spacer(Modifier.height(2.dp))
                Text(call.callerName, style = MaterialTheme.typography.titleLarge, color = TextHi)
            }
            Pill(call.status.replace('-', ' '), Danger)
        }
        if (call.callerNumber.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(call.callerNumber, style = MaterialTheme.typography.bodyMedium, color = TextMid)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (call.isRinging) {
                Button(
                    onClick = onAnswer,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Mint,
                        contentColor = Color(0xFF04140F)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Answer")
                }
                OutlinedButton(
                    onClick = onReject,
                    border = BorderStroke(1.dp, Danger.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.CallEnd, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Reject")
                }
            } else {
                OutlinedButton(
                    onClick = onEnd,
                    border = BorderStroke(1.dp, Danger.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.CallEnd, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("End call")
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Control is relayed to ${roleLabel(call.originDevice)}." +
                if (call.originDevice == myRole) " This phone owns the call." else "",
            style = MaterialTheme.typography.bodyMedium,
            color = TextLow
        )
    }
}

@Composable
private fun ClipboardCard(
    clipboard: ClipboardSnapshot?,
    myRole: String,
    onCopy: () -> Unit
) {
    val text = clipboard?.text.orEmpty()
    val source = clipboard?.sourceDevice.orEmpty()

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ContentPaste, contentDescription = null, tint = Mint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Shared clipboard", style = MaterialTheme.typography.titleMedium, color = TextHi)
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    source.isBlank() -> "empty"
                    source == myRole -> "from this phone"
                    else -> "from ${roleLabel(source)}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = TextLow
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CardSoft)
                .border(1.dp, Stroke, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Text(
                text.ifBlank { "Copy something on either phone and it appears here." },
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = if (text.isBlank()) TextLow else TextHi,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (text.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (clipboard?.timestamp.isNullOrBlank()) "" else "Synced ${clipboard?.timestamp}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextLow
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCopy) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy", color = Mint)
                }
            }
        }
    }
}

@Composable
private fun FeedHeader(count: Int, updatedAt: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Live feed", style = MaterialTheme.typography.headlineMedium, color = TextHi)
        Spacer(Modifier.width(8.dp))
        Pill(text = count.toString(), color = Primary)
        Spacer(Modifier.weight(1f))
        if (updatedAt.isNotBlank()) {
            Text(
                "updated $updatedAt",
                style = MaterialTheme.typography.bodyMedium,
                color = TextLow
            )
        }
    }
}

@Composable
private fun EmptyFeed(connection: ConnectionState) {
    SectionCard {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Notifications, contentDescription = null, tint = Primary)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (connection == ConnectionState.CONNECTED) "Connected — waiting for alerts" else "Not connected yet",
                style = MaterialTheme.typography.titleMedium,
                color = TextHi
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "A notification on the other phone shows up here in real time.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextLow,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun NotificationCard(
    item: SyncNotification,
    myRole: String,
    onDismiss: () -> Unit
) {
    val accent = accentFor(item.appName)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Card)
            .border(1.dp, Stroke, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    item.appName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = accent
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.appName,
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "· ${if (item.originDevice == myRole) "this phone" else roleLabel(item.originDevice)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextLow,
                        maxLines = 1
                    )
                    Spacer(Modifier.weight(1f))
                    Text(item.timestamp, style = MaterialTheme.typography.bodyMedium, color = TextLow)
                }
                Spacer(Modifier.height(3.dp))
                Text(item.title, style = MaterialTheme.typography.titleMedium, color = TextHi)
                if (item.content.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        item.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMid,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!item.repliedText.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Reply sent: ${item.repliedText}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mint
                    )
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Dismiss on both phones", tint = TextLow, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun PermissionsCard(
    notificationsGranted: Boolean,
    accessibilityGranted: Boolean,
    callsGranted: Boolean,
    onOpenNotifications: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onRequestCalls: () -> Unit
) {
    val granted = listOf(notificationsGranted, accessibilityGranted, callsGranted).count { it }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Device access", style = MaterialTheme.typography.titleMedium, color = TextHi)
            Spacer(Modifier.weight(1f))
            Pill(
                text = "$granted/3",
                color = if (granted == 3) Mint else Amber
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Three non-root permissions power the bridge. Tap a row to manage it.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextLow
        )
        Spacer(Modifier.height(10.dp))
        PermissionRow(
            icon = Icons.Rounded.Notifications,
            title = "Notification mirroring",
            subtitle = "Mirror and dismiss alerts",
            granted = notificationsGranted,
            onClick = onOpenNotifications
        )
        HorizontalDivider(color = Stroke, modifier = Modifier.padding(vertical = 10.dp))
        PermissionRow(
            icon = Icons.Rounded.Accessibility,
            title = "Clipboard capture",
            subtitle = "Detect copies via Accessibility",
            granted = accessibilityGranted,
            onClick = onOpenAccessibility
        )
        HorizontalDivider(color = Stroke, modifier = Modifier.padding(vertical = 10.dp))
        PermissionRow(
            icon = Icons.Rounded.Phone,
            title = "Call control",
            subtitle = "Answer and reject calls",
            granted = callsGranted,
            onClick = onRequestCalls
        )
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CardSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = TextMid, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextHi)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextLow)
        }
        Icon(
            if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = if (granted) Mint else Amber,
            modifier = Modifier.size(19.dp)
        )
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TextLow, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Footer(
    role: String,
    baseUrl: String,
    hostUrl: String?,
    joinedHost: String,
    connection: ConnectionState,
    onRetry: () -> Unit
) {
    val endpoint = if (role == ROLE_A) {
        hostUrl?.let { "hosting ${hostLabel(it)}" } ?: "not on Wi-Fi"
    } else {
        joinedHost.takeIf { it.isNotBlank() }?.let { "joined ${hostLabel(it)}" }
            ?: "searching for Phone A"
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "ContinuumSync 2.1.0 · $endpoint",
            style = MaterialTheme.typography.bodyMedium,
            color = TextLow,
            textAlign = TextAlign.Center
        )
        if (connection == ConnectionState.OFFLINE) {
            TextButton(onClick = onRetry) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reconnect", color = Primary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    initialUrl: String,
    initialRoom: String,
    initialRole: String,
    initialAutoDiscover: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Boolean) -> Unit
) {
    val context = LocalContext.current
    var url by remember { mutableStateOf(initialUrl) }
    var roomCode by remember { mutableStateOf(initialRoom) }
    var role by remember { mutableStateOf(initialRole) }
    var autoDiscover by remember { mutableStateOf(initialAutoDiscover) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Card,
        contentColor = TextHi,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Stroke) }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp)
                .navigationBarsPadding()
        ) {
            Text("Connection settings", style = MaterialTheme.typography.headlineMedium, color = TextHi)
            Spacer(Modifier.height(4.dp))
            Text(
                if (role == ROLE_A) {
                    "Phone A hosts the room itself. Both phones only need the same room code and Wi-Fi network."
                } else {
                    "Phone B joins the room hosted by Phone A on the same Wi-Fi. No computer required."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextLow
            )
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = roomCode,
                onValueChange = { roomCode = it },
                label = { Text("Room code") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors()
            )
            Spacer(Modifier.height(12.dp))
            if (role == ROLE_B) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Phone A address (optional)") },
                    placeholder = { Text("Found automatically on Wi-Fi") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors()
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Switch(
                        checked = autoDiscover,
                        onCheckedChange = { autoDiscover = it },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF07132C),
                            checkedTrackColor = Primary
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Find Phone A automatically",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMid
                    )
                }
                TextButton(
                    onClick = {
                        SyncRepository.scanForHost()
                        onDismiss()
                        Toast.makeText(context, "Scanning Wi-Fi for Phone A...", Toast.LENGTH_SHORT)
                            .show()
                    }
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Scan for Phone A now", color = Primary)
                }
            } else {
                Text(
                    "This phone opens a small sync server automatically. Phone B will find it over Wi-Fi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextLow
                )
            }
            Spacer(Modifier.height(18.dp))
            Text("THIS PHONE IS", style = MaterialTheme.typography.labelSmall, color = TextLow)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RoleOption(
                    label = "Phone A · Host",
                    selected = role == ROLE_A,
                    onClick = { role = ROLE_A },
                    modifier = Modifier.weight(1f)
                )
                RoleOption(
                    label = "Phone B · Join",
                    selected = role == ROLE_B,
                    onClick = { role = ROLE_B },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        url = SyncPrefs.DEFAULT_BASE_URL
                        roomCode = SyncPrefs.DEFAULT_ROOM
                        autoDiscover = true
                    }
                ) {
                    Text("Reset defaults", color = TextMid)
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onSave(url, roomCode, role, autoDiscover) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        contentColor = Color(0xFF07132C)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save & reconnect")
                }
            }
        }
    }
}

@Composable
private fun RoleOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Primary.copy(alpha = 0.16f) else CardSoft)
            .border(
                1.dp,
                if (selected) Primary.copy(alpha = 0.55f) else Stroke,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (selected) Primary else TextLow)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) TextHi else TextMid
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextHi,
    unfocusedTextColor = TextHi,
    focusedBorderColor = Primary,
    unfocusedBorderColor = Stroke,
    focusedLabelColor = Primary,
    unfocusedLabelColor = TextLow,
    cursorColor = Primary,
    focusedContainerColor = CardSoft,
    unfocusedContainerColor = CardSoft
)

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Card)
            .border(1.dp, Stroke, RoundedCornerShape(22.dp))
            .padding(18.dp),
        content = content
    )
}

@Composable
private fun Pill(text: String, color: Color) {
    Text(
        text,
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}

@Composable
private fun InfoChip(icon: ImageVector, text: String, color: Color) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(CardSoft)
            .border(1.dp, Stroke, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun accentFor(name: String): Color {
    val palette = listOf(Primary, Mint, Amber, Violet, Cyan, Danger)
    val index = (name.hashCode().absoluteValue) % palette.size
    return palette[index]
}

private fun hostLabel(url: String): String =
    url.removePrefix("https://").removePrefix("http://").trimEnd('/')

private fun isLoopbackAddress(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains("127.0.0.1") ||
        lower.contains("localhost") ||
        lower.contains("0.0.0.0")
}

private fun hasNotificationAccess(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

private fun hasAccessibilityAccess(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.resolveInfo.serviceInfo.packageName == context.packageName }
}

private fun requiredCallPermissions(): Array<String> {
    val permissions = mutableListOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ANSWER_PHONE_CALLS
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
    }
    return permissions.toTypedArray()
}

private fun hasPermissions(context: Context, permissions: Array<String>): Boolean =
    permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

private fun readClipboard(context: Context): String? {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return manager.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
