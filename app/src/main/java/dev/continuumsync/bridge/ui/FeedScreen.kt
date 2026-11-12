package dev.continuumsync.bridge.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.continuumsync.bridge.data.CallSnapshot
import dev.continuumsync.bridge.data.ConnectionState
import dev.continuumsync.bridge.data.PeerDevice
import dev.continuumsync.bridge.data.RoomSnapshot
import dev.continuumsync.bridge.data.SyncNotification
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
fun FeedScreen(
    snapshot: RoomSnapshot?,
    connection: ConnectionState,
    myRole: String,
    peerDevices: List<PeerDevice>,
    notificationsGranted: Boolean,
    overlayGranted: Boolean,
    onDismiss: (SyncNotification) -> Unit,
    onOpenDetail: (SyncNotification) -> Unit,
    onAnswerCall: (CallSnapshot) -> Unit,
    onRejectCall: (CallSnapshot) -> Unit,
    onEndCall: (CallSnapshot) -> Unit,
    onRefresh: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenDevices: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var roleFilter by rememberSaveable { mutableStateOf("") }
    var appFilter by rememberSaveable { mutableStateOf("") }

    val peers = snapshot?.connectedPeers ?: 0
    val allNotifications = snapshot?.notifications.orEmpty().filter { !it.dismissed }
    val activeCall = snapshot?.activeCall
    val otherDevices = peerDevices.filter { it.role != myRole }
    val apps = allNotifications.map { it.appName }.filter { it.isNotBlank() }.distinct().sorted()

    val visible = allNotifications.filter { item ->
        (roleFilter.isBlank() || item.originDevice == roleFilter) &&
            (appFilter.isBlank() || item.appName == appFilter) &&
            (query.isBlank() || listOf(item.title, item.content, item.appName)
                .any { it.contains(query.trim(), ignoreCase = true) })
    }

    val subtitle = when {
        connection == ConnectionState.CONNECTED && peers >= 2 ->
            "$peers devices linked · synced live"
        connection == ConnectionState.CONNECTED -> "Waiting for your other device"
        connection == ConnectionState.CONNECTING -> "Connecting over Wi-Fi…"
        connection == ConnectionState.OFFLINE -> "Not connected · check Wi-Fi"
        else -> "Two devices, one calm feed"
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier
                .widthIn(max = 780.dp)
                .fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = 4.dp,
                bottom = 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ScreenHeader(title = "Notifications", subtitle = subtitle) {
                    StatusPill(connection = connection, peers = peers)
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = onRefresh) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "Reconnect",
                            tint = TextMid
                        )
                    }
                }
            }

            if (!notificationsGranted) {
                item {
                    ActionBanner(
                        icon = Icons.Rounded.NotificationsActive,
                        title = "Turn on notification access",
                        body = "Mirror alerts from this device and let the other one reply.",
                        actionLabel = "Open",
                        color = Amber,
                        onClick = onOpenNotificationAccess
                    )
                }
            }

            if (!overlayGranted) {
                item {
                    ActionBanner(
                        icon = Icons.Rounded.ContentCopy,
                        title = "Instant clipboard needs one tap",
                        body = "Allow display over other apps so copies sync in any app.",
                        actionLabel = "Allow",
                        color = Primary,
                        onClick = onOpenOverlay
                    )
                }
            }

            if (connection == ConnectionState.OFFLINE) {
                item {
                    ActionBanner(
                        icon = Icons.Rounded.Refresh,
                        title = "Device not connected",
                        body = "Keep both devices on the same Wi-Fi, then reconnect.",
                        actionLabel = "Retry",
                        color = Danger,
                        onClick = onRefresh
                    )
                }
            }

            if (activeCall != null && activeCall.status != "idle" && !activeCall.isFinished) {
                item {
                    ActiveCallCard(
                        call = activeCall,
                        myRole = myRole,
                        peerDevices = peerDevices,
                        onAnswer = { onAnswerCall(activeCall) },
                        onReject = { onRejectCall(activeCall) },
                        onEnd = { onEndCall(activeCall) }
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    placeholder = {
                        Text(
                            "Search notifications",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextLow
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = null,
                            tint = TextLow,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Clear search",
                                    tint = TextLow,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Card,
                        unfocusedContainerColor = Card,
                        focusedBorderColor = Primary.copy(alpha = 0.55f),
                        unfocusedBorderColor = Stroke,
                        focusedTextColor = TextHi,
                        unfocusedTextColor = TextHi,
                        cursorColor = Primary
                    )
                )
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = roleFilter.isBlank(),
                        onClick = { roleFilter = "" },
                        label = { Text("All devices") },
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(
                            1.dp,
                            if (roleFilter.isBlank()) Primary.copy(alpha = 0.3f) else Stroke
                        ),
                        colors = filterChipColors()
                    )
                    FilterChip(
                        selected = roleFilter == myRole,
                        onClick = { roleFilter = myRole },
                        label = { Text("This device") },
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(
                            1.dp,
                            if (roleFilter == myRole) Primary.copy(alpha = 0.3f) else Stroke
                        ),
                        colors = filterChipColors()
                    )
                    otherDevices.forEach { device ->
                        FilterChip(
                            selected = roleFilter == device.role,
                            onClick = { roleFilter = device.role },
                            label = { Text(device.label, maxLines = 1) },
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(
                                1.dp,
                                if (roleFilter == device.role) Primary.copy(alpha = 0.3f) else Stroke
                            ),
                            colors = filterChipColors()
                        )
                    }
                }
            }

            if (apps.size > 1 || appFilter.isNotBlank()) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = appFilter.isBlank(),
                            onClick = { appFilter = "" },
                            label = { Text("All apps") },
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(
                                1.dp,
                                if (appFilter.isBlank()) Primary.copy(alpha = 0.3f) else Stroke
                            ),
                            colors = filterChipColors()
                        )
                        apps.forEach { app ->
                            FilterChip(
                                selected = appFilter == app,
                                onClick = { appFilter = app },
                                label = { Text(app, maxLines = 1) },
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(
                                    1.dp,
                                    if (appFilter == app) Primary.copy(alpha = 0.3f) else Stroke
                                ),
                                colors = filterChipColors()
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        when {
                            visible.isEmpty() -> "No notifications"
                            visible.size == 1 -> "1 notification"
                            else -> "${visible.size} notifications"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = TextLow
                    )
                    Spacer(Modifier.weight(1f))
                    if (roleFilter.isNotBlank() || appFilter.isNotBlank() || query.isNotBlank()) {
                        TextButton(
                            onClick = {
                                roleFilter = ""
                                appFilter = ""
                                query = ""
                            }
                        ) {
                            Text("Clear filters", color = Primary)
                        }
                    }
                }
            }

            if (visible.isEmpty()) {
                item {
                    EmptyFeed(
                        hasFilters = roleFilter.isNotBlank() || appFilter.isNotBlank() || query.isNotBlank(),
                        connected = connection == ConnectionState.CONNECTED,
                        onOpenDevices = onOpenDevices
                    )
                }
            } else {
                items(visible, key = { it.id }) { item ->
                    NotificationCard(
                        item = item,
                        myRole = myRole,
                        peerDevices = peerDevices,
                        onOpen = { onOpenDetail(item) },
                        onDismiss = { onDismiss(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun filterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Card,
    labelColor = TextMid,
    selectedContainerColor = PrimarySoft,
    selectedLabelColor = Primary
)

@Composable
private fun NotificationCard(
    item: SyncNotification,
    myRole: String,
    peerDevices: List<PeerDevice>,
    onOpen: () -> Unit,
    onDismiss: () -> Unit
) {
    val accent = accentFor(item.appName)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        color = Card,
        border = BorderStroke(1.dp, Stroke),
        shadowElevation = 1.dp
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.Top) {
            AppBadge(item.appName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.appName,
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "· ${if (item.originDevice == myRole) "this device" else peerName(item.originDevice, myRole, peerDevices)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLow,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        shortTime(item.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLow
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    item.title.ifBlank { item.appName },
                    style = MaterialTheme.typography.titleMedium,
                    color = TextHi,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.content.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        item.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMid,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(7.dp))
                if (!item.repliedText.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = Mint,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "Replied: ${item.repliedText}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Mint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else if (item.canReply) {
                    Text(
                        "Tap to reply",
                        style = MaterialTheme.typography.labelMedium,
                        color = Primary
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Dismiss on both devices",
                    tint = TextLow,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
private fun ActiveCallCard(
    call: CallSnapshot,
    myRole: String,
    peerDevices: List<PeerDevice>,
    onAnswer: () -> Unit,
    onReject: () -> Unit,
    onEnd: () -> Unit
) {
    SoftCard(padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Danger.copy(alpha = 0.11f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Call, contentDescription = null, tint = Danger)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        call.isRinging -> "INCOMING CALL"
                        call.status == "answer-requested" -> "ANSWERING"
                        call.isActive -> "CALL IN PROGRESS"
                        else -> "CALL"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (call.isRinging) Mint else Danger
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    call.callerName.ifBlank { "Unknown caller" },
                    style = MaterialTheme.typography.titleLarge,
                    color = TextHi
                )
                Text(
                    if (call.originDevice == myRole) {
                        "SIM on this device"
                    } else {
                        "Ringing on ${peerName(call.originDevice, myRole, peerDevices)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLow
                )
            }
            Pill(
                text = call.status.replace('-', ' '),
                color = if (call.isRinging) Mint else Danger
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (call.isRinging) {
                Button(
                    onClick = onAnswer,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Mint,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Icon(
                        Icons.Rounded.Call,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text("Answer")
                }
                OutlinedButton(
                    onClick = onReject,
                    border = BorderStroke(1.dp, Danger.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Icon(
                        Icons.Rounded.CallEnd,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text("Decline")
                }
            } else {
                OutlinedButton(
                    onClick = onEnd,
                    border = BorderStroke(1.dp, Danger.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Icon(
                        Icons.Rounded.CallEnd,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text("End call")
                }
            }
        }
    }
}

@Composable
private fun EmptyFeed(
    hasFilters: Boolean,
    connected: Boolean,
    onOpenDevices: () -> Unit
) {
    SoftCard {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(PrimarySoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (hasFilters) Icons.Rounded.Search else Icons.Rounded.Notifications,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                when {
                    hasFilters -> "Nothing matches those filters"
                    connected -> "You're all caught up"
                    else -> "Connect your other device"
                },
                style = MaterialTheme.typography.titleLarge,
                color = TextHi
            )
            Spacer(Modifier.height(5.dp))
            Text(
                when {
                    hasFilters -> "Try another app, device or search term."
                    connected -> "New alerts appear here instantly, in both directions."
                    else -> "Pair once with a QR code and both devices share one feed."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextLow,
                textAlign = TextAlign.Center
            )
            if (!connected && !hasFilters) {
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onOpenDevices,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Text("Open devices")
                }
            }
        }
    }
}
