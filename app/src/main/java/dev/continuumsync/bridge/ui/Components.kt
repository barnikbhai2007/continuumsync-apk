package dev.continuumsync.bridge.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.continuumsync.bridge.data.ConnectionState
import dev.continuumsync.bridge.data.DeviceIdentity
import dev.continuumsync.bridge.data.PeerDevice
import dev.continuumsync.bridge.data.ROLE_A
import dev.continuumsync.bridge.ui.theme.Amber
import dev.continuumsync.bridge.ui.theme.Card
import dev.continuumsync.bridge.ui.theme.CardSoft
import dev.continuumsync.bridge.ui.theme.Cyan
import dev.continuumsync.bridge.ui.theme.Danger
import dev.continuumsync.bridge.ui.theme.Mint
import dev.continuumsync.bridge.ui.theme.Primary
import dev.continuumsync.bridge.ui.theme.PrimarySoft
import dev.continuumsync.bridge.ui.theme.Stroke
import dev.continuumsync.bridge.ui.theme.TextHi
import dev.continuumsync.bridge.ui.theme.TextLow
import dev.continuumsync.bridge.ui.theme.TextMid
import dev.continuumsync.bridge.ui.theme.Violet
import kotlin.math.absoluteValue

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, color = TextHi)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextLow)
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        }
    }
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Card,
        border = BorderStroke(1.dp, Stroke),
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun Pill(
    text: String,
    color: Color,
    background: Color = color.copy(alpha = 0.11f)
) {
    Text(
        text,
        Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1
    )
}

@Composable
fun StatusPill(connection: ConnectionState, peers: Int) {
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
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.22f), RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun AppBadge(name: String, size: Dp = 46.dp) {
    val accent = accentFor(name)
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(15.dp))
            .background(accent.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = accent
        )
    }
}

@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CardSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = TextMid, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextHi)
            Spacer(Modifier.height(1.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextLow,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        } else if (onClick != null) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = TextLow,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
fun PermissionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (granted) Mint.copy(alpha = 0.12f) else CardSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (granted) Mint else TextMid,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextHi)
            Spacer(Modifier.height(1.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextLow)
        }
        Icon(
            if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = if (granted) Mint else Amber,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(5.dp))
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = TextLow,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun ActionBanner(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Card)
            .border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(20.dp))
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(color.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextHi)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = TextLow)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            actionLabel,
            Modifier
                .clip(RoundedCornerShape(11.dp))
                .background(color.copy(alpha = 0.12f))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = color
        )
    }
}

@Composable
fun ChoiceCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) PrimarySoft else Card)
            .border(
                1.dp,
                if (selected) Primary.copy(alpha = 0.55f) else Stroke,
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (selected) Primary else TextLow.copy(alpha = 0.5f))
            )
            Spacer(Modifier.width(7.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) Primary else TextHi,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextLow)
    }
}

@Composable
fun ContinuumBottomBar(selected: Int, onSelect: (Int) -> Unit) {
    Column {
        HorizontalDivider(color = Stroke)
        NavigationBar(containerColor = Card, tonalElevation = 0.dp) {
            val items = listOf(
                Triple("Alerts", Icons.Rounded.Notifications, Icons.Outlined.Notifications),
                Triple("Devices", Icons.Rounded.Devices, Icons.Outlined.Devices),
                Triple("Settings", Icons.Rounded.Settings, Icons.Outlined.Settings)
            )
            items.forEachIndexed { index, item ->
                NavigationBarItem(
                    selected = selected == index,
                    onClick = { onSelect(index) },
                    icon = {
                        Icon(
                            if (selected == index) item.second else item.third,
                            contentDescription = item.first,
                            modifier = Modifier.size(23.dp)
                        )
                    },
                    label = { Text(item.first, style = MaterialTheme.typography.labelMedium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Primary,
                        selectedTextColor = Primary,
                        indicatorColor = PrimarySoft,
                        unselectedIconColor = TextLow,
                        unselectedTextColor = TextLow
                    )
                )
            }
        }
    }
}

fun peerName(role: String, myRole: String, peers: List<PeerDevice>): String {
    if (role == myRole) return DeviceIdentity.displayName
    val peer = peers.firstOrNull { it.role == role }
    return peer?.label?.takeIf { it.isNotBlank() }
        ?: if (role == ROLE_A) "Phone A" else "Phone B"
}

fun accentFor(name: String): Color {
    val palette = listOf(Primary, Mint, Amber, Violet, Cyan, Danger)
    val index = (name.hashCode().absoluteValue) % palette.size
    return palette[index]
}

fun shortTime(raw: String): String = when {
    raw.length >= 5 && raw[2] == ':' -> raw.take(5)
    else -> raw
}
