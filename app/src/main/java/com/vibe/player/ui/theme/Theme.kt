package com.vibe.player.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlin.math.max
import kotlin.math.min

enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** Default accent: the blue from the app icon. */
const val DEFAULT_ACCENT_ARGB = 0xFF4F8CFF.toInt()

val AccentPresets: List<Pair<String, Int>> = listOf(
    "Blue" to 0xFF4F8CFF.toInt(),
    "Cyan" to 0xFF22D3EE.toInt(),
    "Teal" to 0xFF2DD4BF.toInt(),
    "Green" to 0xFF4ADE80.toInt(),
    "Amber" to 0xFFFBBF24.toInt(),
    "Orange" to 0xFFFB923C.toInt(),
    "Red" to 0xFFF87171.toInt(),
    "Pink" to 0xFFF472B6.toInt(),
    "Purple" to 0xFF9D8CFF.toInt(),
)

private val Rose = Color(0xFFFF7AA2)

private val DarkBase = darkColorScheme(
    tertiary = Rose,
    onTertiary = Color(0xFF3C0A1E),
    tertiaryContainer = Color(0xFF4A1B30),
    onTertiaryContainer = Color(0xFFFFD9E3),
    background = Color(0xFF0B0B10),
    onBackground = Color(0xFFECE9F6),
    surface = Color(0xFF0B0B10),
    onSurface = Color(0xFFECE9F6),
    surfaceVariant = Color(0xFF1A1A24),
    onSurfaceVariant = Color(0xFFB4B1C4),
    surfaceContainerLowest = Color(0xFF07070B),
    surfaceContainerLow = Color(0xFF111118),
    surfaceContainer = Color(0xFF15151E),
    surfaceContainerHigh = Color(0xFF1C1C27),
    surfaceContainerHighest = Color(0xFF242431),
    outline = Color(0xFF4A4858),
    outlineVariant = Color(0xFF2A2936),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0000),
    errorContainer = Color(0xFF5A1A17),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightBase = lightColorScheme(
    tertiary = Color(0xFFB4336A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9E3),
    onTertiaryContainer = Color(0xFF3C0A1E),
    background = Color(0xFFF8F7FC),
    onBackground = Color(0xFF1A1A22),
    surface = Color(0xFFF8F7FC),
    onSurface = Color(0xFF1A1A22),
    surfaceVariant = Color(0xFFE8E6F0),
    onSurfaceVariant = Color(0xFF4A4858),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F1F8),
    surfaceContainer = Color(0xFFECEBF3),
    surfaceContainerHigh = Color(0xFFE6E5EE),
    surfaceContainerHighest = Color(0xFFE0DFE8),
    outline = Color(0xFF7A788A),
    outlineVariant = Color(0xFFCAC8D6),
)

/** Builds the primary/secondary roles from one accent colour, keeping the neutral surfaces fixed. */
fun accentScheme(accentArgb: Int, dark: Boolean): ColorScheme {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(accentArgb, hsv)
    val h = hsv[0]
    val s = hsv[1].coerceIn(0.25f, 1f) // very grey accents still need some colour to read as "accent"
    fun c(sat: Float, value: Float, hue: Float = h) =
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat.coerceIn(0f, 1f), value.coerceIn(0f, 1f))))

    return if (dark) DarkBase.copy(
        primary = c(s * 0.80f, max(hsv[2], 0.92f)),
        onPrimary = c(min(s, 0.7f), 0.14f),
        primaryContainer = c(s * 0.95f, 0.36f),
        onPrimaryContainer = c(s * 0.25f, 0.97f),
        inversePrimary = c(s, 0.55f),
        secondary = c(s * 0.45f, 0.93f),
        onSecondary = c(s * 0.7f, 0.16f),
        secondaryContainer = c(s * 0.75f, 0.26f),
        onSecondaryContainer = c(s * 0.2f, 0.96f),
        surfaceTint = c(s * 0.8f, 0.92f),
    ) else LightBase.copy(
        primary = c(s, min(hsv[2], 0.78f)),
        onPrimary = Color.White,
        primaryContainer = c(s * 0.22f, 1f),
        onPrimaryContainer = c(s, 0.28f),
        inversePrimary = c(s * 0.7f, 0.95f),
        secondary = c(s * 0.85f, 0.55f),
        onSecondary = Color.White,
        secondaryContainer = c(s * 0.18f, 0.98f),
        onSecondaryContainer = c(s, 0.24f),
        surfaceTint = c(s, 0.78f),
    )
}

val VibeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

val VibeTypography = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.Medium),
    )
}

@Composable
fun VibeTheme(mode: ThemeMode, accentArgb: Int = DEFAULT_ACCENT_ARGB, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val scheme = remember(accentArgb, dark) { accentScheme(accentArgb, dark) }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = scheme, typography = VibeTypography, shapes = VibeShapes, content = content)
}

fun Int.toHex(): String = "#%06X".format(this and 0xFFFFFF)
fun parseHex(text: String): Int? {
    val t = text.trim().removePrefix("#")
    if (t.length != 6 || !t.all { it.isLetterOrDigit() }) return null
    return runCatching { (0xFF000000 or t.toLong(16)).toInt() }.getOrNull()
}
fun Color.argb(): Int = toArgb()
