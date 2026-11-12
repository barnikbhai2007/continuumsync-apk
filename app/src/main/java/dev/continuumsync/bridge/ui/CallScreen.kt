package dev.continuumsync.bridge.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.continuumsync.bridge.R
import dev.continuumsync.bridge.data.CallSnapshot
import dev.continuumsync.bridge.data.roleLabel
import dev.continuumsync.bridge.ui.theme.CallTextHi
import dev.continuumsync.bridge.ui.theme.CallTextLow
import dev.continuumsync.bridge.ui.theme.CallTextMid
import dev.continuumsync.bridge.ui.theme.Danger
import dev.continuumsync.bridge.ui.theme.Mint

@Composable
fun CallScreen(
    call: CallSnapshot,
    myRole: String,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onEnd: () -> Unit,
    onClose: () -> Unit
) {
    val isRinging = call.isRinging
    val isActive = call.isActive
    val isAnswering = call.status == "answer-requested"
    val isRemote = call.originDevice != myRole
    val hasNumber = call.callerNumber.isNotBlank()
    val knownName = call.callerName.isNotBlank() && call.callerName != "Unknown caller"
    val displayName = when {
        knownName -> call.callerName
        hasNumber -> call.callerNumber
        else -> "Unknown caller"
    }
    val statusLabel = when {
        isRinging -> "INCOMING CALL"
        isAnswering -> "ANSWERING"
        isActive -> "CALL IN PROGRESS"
        call.status == "missed" -> "MISSED CALL"
        else -> "CALL ENDED"
    }
    val accent = when {
        isRinging || isAnswering || isActive -> Mint
        call.status == "missed" -> Mint
        else -> CallTextLow
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF071624),
                        Color(0xFF0A1C30),
                        Color(0xFF04080F)
                    )
                )
            )
    ) {
        // Ambient glow behind the avatar.
        Box(
            Modifier
                .fillMaxWidth()
                .height(460.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x4D5B8CFF),
                            Color(0x1A2BD9A8),
                            Color.Transparent
                        ),
                        radius = 520f
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 26.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_logo_badge),
                    contentDescription = null,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    "ContinuumSync",
                    style = MaterialTheme.typography.titleMedium,
                    color = CallTextHi
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (isRemote) "from ${roleLabel(call.originDevice)}" else "on this phone",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CallTextLow
                )
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Minimize call screen",
                        tint = CallTextMid
                    )
                }
            }

            Spacer(Modifier.weight(0.75f))

            PulsingAvatar(
                name = displayName,
                ringing = isRinging,
                active = isActive
            )

            Spacer(Modifier.height(30.dp))

            Text(
                statusLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    letterSpacing = 2.4.sp
                ),
                color = accent
            )
            Spacer(Modifier.height(12.dp))
            Text(
                displayName,
                fontSize = 36.sp,
                lineHeight = 41.sp,
                fontWeight = FontWeight.Bold,
                color = CallTextHi,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (knownName && hasNumber) {
                Spacer(Modifier.height(8.dp))
                Text(
                    call.callerNumber,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    color = CallTextMid,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    isRinging && isRemote ->
                        "Ringing on ${roleLabel(call.originDevice)} · live over Wi-Fi"
                    isRinging -> "Ringing on this phone"
                    isAnswering && isRemote ->
                        "Picking up the real call on ${roleLabel(call.originDevice)}…"
                    isAnswering -> "Picking up…"
                    isActive && isRemote ->
                        "Connected on ${roleLabel(call.originDevice)} · speaker on"
                    isActive -> "Connected · speaker on"
                    call.status == "missed" -> "Nobody answered on either phone"
                    call.status == "rejected" -> "Call declined from the other phone"
                    else -> "The call has ended"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = CallTextLow,
                textAlign = TextAlign.Center
            )

            if (isRinging && isRemote) {
                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x1A2BD9A8))
                        .padding(horizontal = 16.dp, vertical = 11.dp)
                ) {
                    Text(
                        "Answering picks up the call on ${roleLabel(call.originDevice)} and turns on its speaker.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CallTextMid,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            when {
                isRinging -> Row(
                    horizontalArrangement = Arrangement.spacedBy(54.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CallCircleButton(
                        color = Danger,
                        icon = Icons.Rounded.CallEnd,
                        label = "Decline",
                        onClick = onDecline
                    )
                    CallCircleButton(
                        color = Mint,
                        icon = Icons.Rounded.Call,
                        label = "Answer",
                        onClick = onAnswer
                    )
                }
                isAnswering -> CallCircleButton(
                    color = Danger,
                    icon = Icons.Rounded.CallEnd,
                    label = "Cancel",
                    onClick = onDecline
                )
                isActive -> CallCircleButton(
                    color = Danger,
                    icon = Icons.Rounded.CallEnd,
                    label = "End call",
                    onClick = onEnd
                )
                else -> CallCircleButton(
                    color = Color(0xFF22314A),
                    icon = Icons.Rounded.Close,
                    label = "Close",
                    onClick = onClose
                )
            }

            Spacer(Modifier.height(26.dp))
            Text(
                "ContinuumSync · peer-to-peer over your Wi-Fi",
                style = MaterialTheme.typography.bodyMedium,
                color = CallTextLow.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
private fun PulsingAvatar(name: String, ringing: Boolean, active: Boolean) {
    val transition = rememberInfiniteTransition(label = "callRings")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (ringing) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600),
            repeatMode = RepeatMode.Restart
        ),
        label = "callPulse"
    )

    val initials = name
        .split(' ', '.', '-')
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.take(1).uppercase() }
        .ifBlank { "?" }

    Box(
        Modifier.size(240.dp),
        contentAlignment = Alignment.Center
    ) {
        if (ringing) {
            Box(
                Modifier
                    .size(190.dp)
                    .graphicsLayer {
                        scaleX = 1f + pulse * 0.55f
                        scaleY = 1f + pulse * 0.55f
                        alpha = (1f - pulse) * 0.35f
                    }
                    .clip(CircleShape)
                    .background(Mint)
            )
            Box(
                Modifier
                    .size(160.dp)
                    .graphicsLayer {
                        scaleX = 1f + pulse * 0.3f
                        scaleY = 1f + pulse * 0.3f
                        alpha = (1f - pulse) * 0.45f
                    }
                    .clip(CircleShape)
                    .background(Color(0x335B8CFF))
            )
        }

        Box(
            Modifier
                .size(142.dp)
                .clip(CircleShape)
                .background(
                    when {
                        ringing -> Brush.linearGradient(
                            listOf(Color(0xFF5B8CFF), Color(0xFF2BD9A8))
                        )
                        active -> Brush.linearGradient(
                            listOf(Color(0xFF1E6B5C), Color(0xFF16243D))
                        )
                        else -> Brush.linearGradient(
                            listOf(Color(0xFF25375A), Color(0xFF16243D))
                        )
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(130.dp)
                    .clip(CircleShape)
                    .background(Color(0x22040A14)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    initials,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (ringing) Color(0xFF07132C) else CallTextHi
                )
            }
        }
    }
}

@Composable
private fun CallCircleButton(
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(78.dp)
                .clip(CircleShape)
                .background(color)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (color == Color(0xFF22314A)) CallTextHi else Color(0xFF07132C),
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = CallTextMid)
    }
}
