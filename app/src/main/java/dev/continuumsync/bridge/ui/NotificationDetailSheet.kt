package dev.continuumsync.bridge.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.automirrored.rounded.Send
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
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.continuumsync.bridge.data.PeerDevice
import dev.continuumsync.bridge.data.SyncNotification
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationDetailSheet(
    item: SyncNotification,
    myRole: String,
    peerDevices: List<PeerDevice>,
    onDismissRequest: () -> Unit,
    onReply: (String) -> Unit,
    onDismissBoth: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var replyText by remember(item.id) { mutableStateOf("") }
    val quickReplies = listOf("On my way!", "Sure 👍", "Call you soon")

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Card,
        contentColor = TextHi,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Stroke) }
    ) {
        androidx.compose.foundation.layout.Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppBadge(item.appName, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    Text(
                        item.appName,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextHi
                    )
                    Text(
                        "From ${if (item.originDevice == myRole) "this device" else peerName(item.originDevice, myRole, peerDevices)} · ${shortTime(item.timestamp)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLow,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = TextLow
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = Stroke)
            Spacer(Modifier.height(16.dp))

            Text("TITLE", style = MaterialTheme.typography.labelSmall, color = TextLow)
            Spacer(Modifier.height(4.dp))
            Text(
                item.title.ifBlank { item.appName },
                style = MaterialTheme.typography.titleLarge,
                color = TextHi
            )
            if (item.content.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text("MESSAGE", style = MaterialTheme.typography.labelSmall, color = TextLow)
                Spacer(Modifier.height(4.dp))
                Text(
                    item.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextMid
                )
            }

            if (!item.repliedText.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Mint.copy(alpha = 0.10f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = Mint,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Reply sent: ${item.repliedText}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF047857)
                    )
                }
            }

            if (item.canReply) {
                Spacer(Modifier.height(18.dp))
                Text("QUICK REPLY", style = MaterialTheme.typography.labelSmall, color = TextLow)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickReplies.forEach { quick ->
                        Text(
                            quick,
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(PrimarySoft)
                                .clickable { onReply(quick) }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = Primary
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp),
                    placeholder = {
                        Text(
                            "Write a reply…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextLow
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (replyText.isNotBlank()) {
                                    onReply(replyText)
                                    replyText = ""
                                }
                            },
                            enabled = replyText.isNotBlank()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Send reply",
                                tint = if (replyText.isNotBlank()) Primary else TextLow,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextHi,
                        unfocusedTextColor = TextHi,
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = Stroke,
                        cursorColor = Primary,
                        focusedContainerColor = CardSoft,
                        unfocusedContainerColor = CardSoft
                    )
                )
            } else {
                Spacer(Modifier.height(16.dp))
                Text(
                    "This app does not offer inline replies. You can still dismiss the alert on both devices.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLow
                )
            }

            Spacer(Modifier.height(18.dp))
            MetaRow(
                icon = Icons.Rounded.Apps,
                label = "App ID",
                value = item.appPackage.ifBlank { "Unknown" }
            )
            HorizontalDivider(color = Stroke, modifier = Modifier.padding(vertical = 9.dp))
            MetaRow(
                icon = Icons.Rounded.AccessTime,
                label = "Time",
                value = item.timestamp.ifBlank { "Just now" }
            )
            HorizontalDivider(color = Stroke, modifier = Modifier.padding(vertical = 9.dp))
            MetaRow(
                icon = Icons.Rounded.Notifications,
                label = "Status",
                value = if (item.repliedText.isNullOrBlank()) "Unread" else "Replied"
            )

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onDismissBoth,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, Danger.copy(alpha = 0.35f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text("Dismiss both")
                }
                Button(
                    onClick = {
                        val text = listOf(item.title, item.content)
                            .filter { it.isNotBlank() }
                            .joinToString("\n")
                        copyToClipboard(context, "ContinuumSync alert", text)
                        showToast(context, "Copied notification text")
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimarySoft,
                        contentColor = Primary
                    ),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text("Copy")
                }
            }
        }
    }
}

@Composable
private fun MetaRow(icon: ImageVector, label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = TextLow, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextLow,
            modifier = Modifier.width(72.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextHi,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
