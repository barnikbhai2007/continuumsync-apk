package dev.continuumsync.bridge.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Light, airy palette inspired by the Notification Sync & Mirror reference app.
val Bg = Color(0xFFF4F7FB)
val BgSoft = Color(0xFFEDF2FA)
val Card = Color(0xFFFFFFFF)
val CardSoft = Color(0xFFF1F5FB)
val Stroke = Color(0xFFE3EAF4)
val Primary = Color(0xFF4F46E5)
val PrimarySoft = Color(0xFFEEF0FF)
val Mint = Color(0xFF10B981)
val MintSoft = Color(0xFFE7F8F1)
val Amber = Color(0xFFF59E0B)
val AmberSoft = Color(0xFFFEF3E2)
val Danger = Color(0xFFEF4444)
val DangerSoft = Color(0xFFFDECEC)
val Violet = Color(0xFF8B5CF6)
val Cyan = Color(0xFF06B6D4)
val TextHi = Color(0xFF0F172A)
val TextMid = Color(0xFF475569)
val TextLow = Color(0xFF7C8BA1)
val TextFaint = Color(0xFFA3B0C2)

// The call experience stays dark for contrast and legibility in low light.
val CallBg = Color(0xFF060A12)
val CallTextHi = Color(0xFFF2F6FF)
val CallTextMid = Color(0xFFA9B8D0)
val CallTextLow = Color(0xFF7B8CA8)

private val scheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = PrimarySoft,
    onPrimaryContainer = Primary,
    secondary = Mint,
    onSecondary = Color.White,
    secondaryContainer = MintSoft,
    onSecondaryContainer = Color(0xFF047857),
    background = Bg,
    onBackground = TextHi,
    surface = Card,
    onSurface = TextHi,
    surfaceVariant = CardSoft,
    onSurfaceVariant = TextMid,
    outline = Stroke,
    outlineVariant = Stroke,
    error = Danger,
    onError = Color.White,
    errorContainer = DangerSoft,
    onErrorContainer = Color(0xFF991B1B)
)

private val appTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 31.sp,
        lineHeight = 37.sp,
        letterSpacing = (-0.7).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
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
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        letterSpacing = 1.2.sp
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
