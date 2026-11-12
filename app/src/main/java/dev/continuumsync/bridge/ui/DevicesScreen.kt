package dev.continuumsync.bridge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.continuumsync.bridge.data.ConnectionState
import dev.continuumsync.bridge.data.DeviceIdentity
import dev.continuumsync.bridge.data.PairingPayload
import dev.continuumsync.bridge.data.PeerDevice
import dev.continuumsync.bridge.data.ROLE_A
import dev.continuumsync.bridge.data.peerRole
import dev.continuumsync.bridge.data.roleLabel
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
fun DevicesScreen(
    room: String,
    role: String,
    baseUrl: String,
    connection: ConnectionState,
    peers: Int,
    peerDevices: List<PeerDevice>,
    hostUrl: String?,
    hostError: String?,
    joinedHost: String,
    onScanQr: () -> Unit,
    onCopyRoom: () -> Unit,
    onShareCode: () -> Unit,
    onCopyHost: (String) -> Unit,
    onApplyManual: (String, String) -> Unit,
    onToggleRole: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val isHost = role == ROLE_A
    val otherRole = peerRole(role)
    val otherDevices = peerDevices.filter { it.role != role }.ifEmpty {
        if (peers >= 2) {
            listOf(
                PeerDevice(
                    role = otherRole,
                    name = roleLabel(otherRole),
                    online = true
                )
            )
        } else {
            emptyList()
        }
    }
    var showManual by remember { mutableStateOf(false) }
    val pairingPayload = if (hostUrl.isNullOrBlank()) "" else {
        PairingPayload.build(room, hostUrl, DeviceIdentity.displayName)
    }

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
                    title = "Devices",
                    subtitle = "Pair once over Wi-Fi. No computer."
                ) {
                    StatusPill(connection = connection, peers = peers)
                }
            }

            item {
                SoftCard(padding = PaddingValues(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(Mint.copy(alpha = 0.13f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Link,
                                contentDescription = null,
                                tint = Mint,
                                modifier = Modifier.size(23.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Pair a device",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextHi
                            )
                            Text(
                                "Scan once and the devices stay connected",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextLow
                            )
                        }
                        Pill("QR", Primary)
                    }

                    Spacer(Modifier.height(16.dp))

                    if (isHost && !hostUrl.isNullOrBlank()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(CardSoft)
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            QrCodePanel(payload = pairingPayload, size = 214.dp)
                        }
                        Spacer(Modifier.height(16.dp))
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "PAIRING CODE",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextLow
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                room,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 30.sp,
                                    letterSpacing = 4.sp
                                ),
                                color = Primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Open ContinuumSync on your other device → Devices → Scan QR code.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextLow,
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onCopyRoom,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Primary,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(Modifier.width(7.dp))
                                Text("Copy code")
                            }
                            OutlinedButton(
                                onClick = onShareCode,
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, Stroke),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextHi),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(Modifier.width(7.dp))
                                Text("Share")
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = onScanQr, modifier = Modifier.fillMaxWidth()) {
                            Icon(
                                Icons.Rounded.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text("Scan a code instead", color = Primary)
                        }
                    } else if (isHost) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(Amber.copy(alpha = 0.09f))
                                .border(
                                    1.dp,
                                    Amber.copy(alpha = 0.25f),
                                    RoundedCornerShape(18.dp)
                                )
                                .padding(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = Amber,
                                    modifier = Modifier.size(19.dp)
                                )
                                Spacer(Modifier.width(9.dp))
                                Column {
                                    Text(
                                        "No Wi-Fi address yet",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextHi
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        hostError
                                            ?: "Connect this device to Wi-Fi so the other device can find it.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMid
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(CardSoft)
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Room code",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextLow
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                room,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = Primary
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Rounded.ContentCopy,
                                contentDescription = "Copy room code",
                                tint = Primary,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable(onClick = onCopyRoom)
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onRetry,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Primary,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(Modifier.width(7.dp))
                                Text("Retry Wi-Fi")
                            }
                            OutlinedButton(
                                onClick = onScanQr,
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, Stroke),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextHi),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.QrCodeScanner,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(Modifier.width(7.dp))
                                Text("Scan")
                            }
                        }
                    } else {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(PrimarySoft)
                                .padding(22.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Card),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.QrCode2,
                                        contentDescription = null,
                                        tint = Primary,
                                        modifier = Modifier.size(31.dp)
                                    )
                                }
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    "Scan the QR code on the phone that hosts the room",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextHi,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Pairing is instant and stays connected over your Wi-Fi.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextLow,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onScanQr,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Primary,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.QrCodeScanner,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(7.dp))
                                Text("Scan QR code")
                            }
                            OutlinedButton(
                                onClick = { showManual = true },
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, Stroke),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextHi),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Enter code")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (joinedHost.isNotBlank()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(CardSoft)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.Wifi,
                                    contentDescription = null,
                                    tint = if (connection == ConnectionState.CONNECTED) Mint else Amber,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    joinedHost,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = TextMid,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        TextButton(
                            onClick = onToggleRole,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Host on this device instead", color = Primary)
                        }
                    }
                }
            }

            item {
                SoftCard(padding = PaddingValues(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Paired devices",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextHi
                        )
                        Spacer(Modifier.weight(1f))
                        Pill("${1 + otherDevices.size}", Primary)
                    }
                    Spacer(Modifier.height(10.dp))
                    DeviceRow(
                        name = DeviceIdentity.displayName,
                        subtitle = "This device · ${if (isHost) "Host" else "Joiner"}",
                        online = true,
                        primary = true
                    )
                    otherDevices.forEach { device ->
                        HorizontalDivider(
                            color = Stroke,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        DeviceRow(
                            name = device.label,
                            subtitle = if (device.online) {
                                "Two-way sync · connected over Wi-Fi"
                            } else {
                                "Waiting to reconnect"
                            },
                            online = device.online,
                            primary = false
                        )
                    }
                    if (otherDevices.isEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(CardSoft)
                                .padding(15.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Devices,
                                    contentDescription = null,
                                    tint = TextLow,
                                    modifier = Modifier.size(21.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "No second device yet",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextHi
                                    )
                                    Text(
                                        if (isHost) {
                                            "Ask the other device to scan your QR code."
                                        } else {
                                            "Scan the QR code on the hosting device."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextLow
                                    )
                                }
                            }
                        }
                    }
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
                            text = when {
                                peers >= 2 -> "2 devices"
                                connection == ConnectionState.CONNECTED -> "Waiting"
                                connection == ConnectionState.CONNECTING -> "Connecting"
                                else -> "Offline"
                            },
                            color = when {
                                peers >= 2 -> Mint
                                connection == ConnectionState.OFFLINE -> Danger
                                connection == ConnectionState.CONNECTED -> Amber
                                else -> Primary
                            }
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    DetailRow(label = "Room", value = room)
                    DetailRow(
                        label = "Mode",
                        value = if (isHost) "Hosting on this device" else "Joining another device"
                    )
                    DetailRow(
                        label = "Address",
                        value = if (isHost) {
                            hostUrl ?: "No Wi-Fi address"
                        } else {
                            joinedHost.ifBlank { baseUrl }
                        },
                        onCopy = if (isHost && hostUrl != null) {
                            { onCopyHost(hostUrl) }
                        } else {
                            null
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onRetry,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, Stroke),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextHi),
                            shape = RoundedCornerShape(13.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Refresh")
                        }
                        OutlinedButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, Stroke),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextHi),
                            shape = RoundedCornerShape(13.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Settings")
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Card)
                        .border(1.dp, Stroke, RoundedCornerShape(18.dp))
                        .padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Mint.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Wifi,
                            contentDescription = null,
                            tint = Mint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(11.dp))
                    Text(
                        "Both devices need to be on the same Wi-Fi. No account, cloud or computer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMid
                    )
                }
            }
        }
    }

    if (showManual) {
        ManualPairDialog(
            initialRoom = room,
            initialHost = joinedHost.ifBlank { baseUrl },
            onDismiss = { showManual = false },
            onConnect = { manualRoom, manualHost ->
                showManual = false
                onApplyManual(manualRoom, manualHost)
            }
        )
    }
}

@Composable
private fun DeviceRow(
    name: String,
    subtitle: String,
    online: Boolean,
    primary: Boolean
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(if (primary) PrimarySoft else Mint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Smartphone,
                contentDescription = null,
                tint = if (primary) Primary else Mint,
                modifier = Modifier.size(23.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                color = TextHi,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextLow,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (online) Mint else Amber)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                if (online) "Online" else "Away",
                style = MaterialTheme.typography.labelMedium,
                color = if (online) Mint else Amber
            )
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    onCopy: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextLow,
            modifier = Modifier.width(74.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextHi,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (onCopy != null) {
            Icon(
                Icons.Rounded.ContentCopy,
                contentDescription = "Copy address",
                tint = Primary,
                modifier = Modifier
                    .size(19.dp)
                    .clickable(onClick = onCopy)
            )
        }
    }
}

@Composable
private fun ManualPairDialog(
    initialRoom: String,
    initialHost: String,
    onDismiss: () -> Unit,
    onConnect: (String, String) -> Unit
) {
    var manualRoom by remember { mutableStateOf(initialRoom) }
    var manualHost by remember { mutableStateOf(initialHost) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Card,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                "Enter pairing code",
                style = MaterialTheme.typography.titleLarge,
                color = TextHi
            )
        },
        text = {
            Column {
                Text(
                    "Type the code shown on the hosting device. The address is optional and is filled in automatically when both devices are on the same Wi-Fi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextLow
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = manualRoom,
                    onValueChange = { manualRoom = it },
                    label = { Text("Pairing code") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = manualFieldColors()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = manualHost,
                    onValueChange = { manualHost = it },
                    label = { Text("Host address (optional)") },
                    placeholder = { Text("192.168.1.20:3100") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = manualFieldColors()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConnect(manualRoom, manualHost) },
                enabled = manualRoom.isNotBlank() || manualHost.isNotBlank()
            ) {
                Text("Connect", color = Primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMid)
            }
        }
    )
}

@Composable
private fun manualFieldColors() = OutlinedTextFieldDefaults.colors(
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
