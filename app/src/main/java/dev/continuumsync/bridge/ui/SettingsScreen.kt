package dev.continuumsync.bridge.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.continuumsync.bridge.data.ConnectionState
import dev.continuumsync.bridge.data.DeviceIdentity
import dev.continuumsync.bridge.data.ROLE_A
import dev.continuumsync.bridge.data.ROLE_B
import dev.continuumsync.bridge.data.SyncPrefs
import dev.continuumsync.bridge.ui.theme.Amber
import dev.continuumsync.bridge.ui.theme.Card
import dev.continuumsync.bridge.ui.theme.CardSoft
import dev.continuumsync.bridge.ui.theme.Danger
import dev.continuumsync.bridge.ui.theme.Mint
import dev.continuumsync.bridge.ui.theme.Primary
import dev.continuumsync.bridge.ui.theme.PrimarySoft
import dev.continuumsync.bridge.ui.theme.Stroke
import dev.continuumsync.bridge.ui.theme.TextHi
import dev.continuumsync.bridge.ui.theme.TextLow
import dev.continuumsync.bridge.ui.theme.TextMid

@Composable
fun SettingsScreen(
    initialRoom: String,
    initialBaseUrl: String,
    initialRole: String,
    initialAutoDiscover: Boolean,
    connection: ConnectionState,
    peers: Int,
    hostUrl: String?,
    joinedHost: String,
    notificationsGranted: Boolean,
    overlayGranted: Boolean,
    accessibilityGranted: Boolean,
    callsGranted: Boolean,
    telephonyAvailable: Boolean,
    batteryUnrestricted: Boolean,
    onSave: (room: String, baseUrl: String, role: String, autoDiscover: Boolean) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onRequestCalls: () -> Unit,
    onTestAlert: () -> Unit,
    onSyncClipboard: () -> Unit,
    onTestCall: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenAutoLaunch: () -> Unit
) {
    var room by remember(initialRoom) { mutableStateOf(initialRoom) }
    var baseUrl by remember(initialBaseUrl) { mutableStateOf(initialBaseUrl) }
    var role by remember(initialRole) { mutableStateOf(initialRole) }
    var autoDiscover by remember(initialAutoDiscover) { mutableStateOf(initialAutoDiscover) }

    val permissionTotal = if (telephonyAvailable) 4 else 3
    val grantedCount = listOf(
        notificationsGranted,
        overlayGranted,
        accessibilityGranted,
        if (telephonyAvailable) callsGranted else true
    ).count { it }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier
                .widthIn(max = 780.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ScreenHeader(
                    title = "Settings",
                    subtitle = "Keep the bridge dependable."
                ) {
                    StatusPill(connection = connection, peers = peers)
                }
            }

            item {
                SoftCard(padding = PaddingValues(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Connection",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextHi
                        )
                        Spacer(Modifier.weight(1f))
                        Pill(
                            text = if (role == ROLE_A) "Host" else "Joiner",
                            color = Primary
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = room,
                        onValueChange = { room = it },
                        label = { Text("Room code") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = settingsFieldColors()
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "THIS DEVICE IS",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextLow
                    )
                    Spacer(Modifier.height(7.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ChoiceCard(
                            title = "Host",
                            subtitle = "This device hosts the room",
                            selected = role == ROLE_A,
                            onClick = { role = ROLE_A },
                            modifier = Modifier.weight(1f)
                        )
                        ChoiceCard(
                            title = "Join",
                            subtitle = "Connect to the other device",
                            selected = role == ROLE_B,
                            onClick = { role = ROLE_B },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (role == ROLE_B) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it },
                            label = { Text("Host address (optional)") },
                            placeholder = { Text("Found automatically on Wi-Fi") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = settingsFieldColors()
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { autoDiscover = !autoDiscover }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Switch(
                                checked = autoDiscover,
                                onCheckedChange = { autoDiscover = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Primary,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = TextLow.copy(alpha = 0.45f)
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Find the host automatically",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextHi
                                )
                                Text(
                                    "Recommended on the same Wi-Fi",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextLow
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "The QR code on the Devices tab points the other device at this phone. Keep both on the same Wi-Fi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextLow
                        )
                        if (!hostUrl.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(CardSoft)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.Wifi,
                                    contentDescription = null,
                                    tint = Mint,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    hostUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMid
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when {
                                role == ROLE_A -> "Hosting room ${SyncPrefs.normalizeRoom(room)}"
                                joinedHost.isNotBlank() -> "Joining $joinedHost"
                                else -> "Looking for the host on Wi-Fi"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextLow,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { onSave(room, baseUrl, role, autoDiscover) },
                            enabled = room.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Primary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(13.dp)
                        ) {
                            Text("Save")
                        }
                    }
                }
            }

            item {
                SoftCard(padding = PaddingValues(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Permissions",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextHi
                        )
                        Spacer(Modifier.weight(1f))
                        Pill(
                            text = "$grantedCount/$permissionTotal",
                            color = if (grantedCount == permissionTotal) Mint else Amber
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tap any row to open the matching Android setting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLow
                    )
                    Spacer(Modifier.height(8.dp))
                    PermissionRow(
                        icon = Icons.Rounded.NotificationsActive,
                        title = "Notification access",
                        subtitle = "Mirror, reply and dismiss alerts",
                        granted = notificationsGranted,
                        onClick = onOpenNotificationAccess
                    )
                    HorizontalDivider(
                        color = Stroke,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                    PermissionRow(
                        icon = Icons.Rounded.ContentCopy,
                        title = "Display over other apps",
                        subtitle = "Instant clipboard in every app",
                        granted = overlayGranted,
                        onClick = onOpenOverlay
                    )
                    HorizontalDivider(
                        color = Stroke,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                    PermissionRow(
                        icon = Icons.Rounded.Accessibility,
                        title = "Smooth clipboard",
                        subtitle = "Avoids interrupting typing · optional",
                        granted = accessibilityGranted,
                        onClick = onOpenAccessibility
                    )
                    if (telephonyAvailable) {
                        HorizontalDivider(
                            color = Stroke,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                        PermissionRow(
                            icon = Icons.Rounded.Phone,
                            title = "Call control",
                            subtitle = "Caller identity · answer and decline",
                            granted = callsGranted,
                            onClick = onRequestCalls
                        )
                    } else {
                        HorizontalDivider(
                            color = Stroke,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                        SettingRow(
                            icon = Icons.Rounded.Phone,
                            title = "Call control",
                            subtitle = "Not needed on this tablet — there is no SIM",
                            trailing = {
                                Pill("Tablet", Primary)
                            }
                        )
                    }
                }
            }

            item {
                SoftCard(padding = PaddingValues(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Stay connected",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextHi
                        )
                        Spacer(Modifier.weight(1f))
                        Pill(
                            text = if (batteryUnrestricted) "Ready" else "Needs a tap",
                            color = if (batteryUnrestricted) Mint else Amber
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    SettingRow(
                        icon = Icons.Rounded.BatteryFull,
                        title = "Battery optimization",
                        subtitle = if (batteryUnrestricted) {
                            "Unrestricted · the bridge stays awake"
                        } else {
                            "Optimized · Android may pause syncing"
                        },
                        onClick = onOpenBattery,
                        trailing = {
                            TextButton(onClick = onOpenBattery) {
                                Text(
                                    if (batteryUnrestricted) "Settings" else "Fix",
                                    color = if (batteryUnrestricted) TextMid else Primary
                                )
                            }
                        }
                    )
                    HorizontalDivider(
                        color = Stroke,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                    SettingRow(
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        title = "OnePlus auto-launch",
                        subtitle = "Allow background starts on Nord 2 5G and Pad 2",
                        onClick = onOpenAutoLaunch,
                        trailing = {
                            TextButton(onClick = onOpenAutoLaunch) {
                                Text("Open", color = Primary)
                            }
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(PrimarySoft)
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "OnePlus: Settings → Battery → Battery optimization → ContinuumSync → Don't optimize. Then Settings → Apps → Auto-launch → allow ContinuumSync.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMid
                        )
                    }
                }
            }

            item {
                SoftCard(padding = PaddingValues(18.dp)) {
                    Text(
                        "Test the bridge",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextHi
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Send something to the room and watch it appear on the other device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLow
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        ToolAction(
                            icon = Icons.Rounded.Notifications,
                            label = "Test alert",
                            color = Primary,
                            onClick = onTestAlert,
                            modifier = Modifier.weight(1f)
                        )
                        ToolAction(
                            icon = Icons.Rounded.ContentPaste,
                            label = "Clipboard",
                            color = Mint,
                            onClick = onSyncClipboard,
                            modifier = Modifier.weight(1f)
                        )
                        ToolAction(
                            icon = Icons.Rounded.Call,
                            label = "Test call",
                            color = Amber,
                            onClick = onTestCall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                SoftCard(padding = PaddingValues(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(PrimarySoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Wifi,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "ContinuumSync 2.2.0",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextHi
                            )
                            Text(
                                "Direct Wi-Fi · no account, cloud or computer",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextLow
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "${DeviceIdentity.displayName} · two-way notification, clipboard and call bridge",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLow,
                        textAlign = TextAlign.Start
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolAction(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .clip(RoundedCornerShape(15.dp))
            .background(CardSoft)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(color.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextHi)
    }
}

@Composable
private fun settingsFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextHi,
    unfocusedTextColor = TextHi,
    focusedBorderColor = Primary,
    unfocusedBorderColor = Stroke,
    focusedLabelColor = Primary,
    unfocusedLabelColor = TextLow,
    cursorColor = Primary,
    focusedContainerColor = Card,
    unfocusedContainerColor = Card,
    focusedPlaceholderColor = TextLow,
    unfocusedPlaceholderColor = TextLow
)
