package dev.continuumsync.bridge.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Bg = Color(0xFF060A12)
val BgSoft = Color(0xFF0A1220)
val Card = Color(0xFF101A2B)
val CardSoft = Color(0xFF0C1524)
val Stroke = Color(0xFF1E2C44)
val Primary = Color(0xFF5B8CFF)
val Mint = Color(0xFF2BD9A8)
val Amber = Color(0xFFFFB020)
val Danger = Color(0xFFFF5D6C)
val Violet = Color(0xFFB78CFF)
val Cyan = Color(0xFF4FD1FF)
val TextHi = Color(0xFFF2F6FF)
val TextMid = Color(0xFFA9B8D0)
val TextLow = Color(0xFF7B8CA8)

private val scheme = darkColorScheme(
    primary = Primary,
    onPrimary = Color(0xFF06122E),
    primaryContainer = Color(0xFF16274D),
    onPrimaryContainer = Color(0xFFD9E4FF),
    secondary = Mint,
    onSecondary = Bg,
    background = Bg,
    onBackground = TextHi,
    surface = Card,
    onSurface = TextHi,
    surfaceVariant = CardSoft,
    onSurfaceVariant = TextMid,
    outline = Stroke,
    error = Danger,
    onError = Color(0xFF2A0509)
)

private val appTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        letterSpacing = (-0.6).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        letterSpacing = 1.4.sp
    )
)

@Composable
fun ContinuumTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        typography = appTypography,
        content = content
    )
}
