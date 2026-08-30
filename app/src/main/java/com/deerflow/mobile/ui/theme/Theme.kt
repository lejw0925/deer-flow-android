@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.deerflow.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.deerflow.mobile.data.ThemePreference

/**
 * Gemini-inspired palette: a cool blue/violet/pink identity on near-neutral
 * surfaces, so the aurora glow behind the glass layers carries the brand color.
 * Dynamic color is intentionally not used; only light/dark variants exist.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF1B6EF3),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),
    secondary = Color(0xFF7B5EA7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEADDFF),
    onSecondaryContainer = Color(0xFF24104A),
    tertiary = Color(0xFFC2497C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFBD0E1),
    onTertiaryContainer = Color(0xFF3E0024),
    background = Color(0xFFFBFCFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFBFCFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE1E4EC),
    onSurfaceVariant = Color(0xFF44474E),
    surfaceDim = Color(0xFFDBDfE7),
    surfaceBright = Color(0xFFFBFCFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F7FC),
    surfaceContainer = Color(0xFFEFF2F9),
    surfaceContainerHigh = Color(0xFFE9EDF5),
    surfaceContainerHighest = Color(0xFFE1E4EC),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF1B4D94),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFCBB8F5),
    onSecondary = Color(0xFF37265D),
    secondaryContainer = Color(0xFF4A3A75),
    onSecondaryContainer = Color(0xFFEADDFF),
    tertiary = Color(0xFFF0A5C2),
    onTertiary = Color(0xFF4A102E),
    tertiaryContainer = Color(0xFF652944),
    onTertiaryContainer = Color(0xFFFBD0E1),
    background = Color(0xFF0E0F13),
    onBackground = Color(0xFFE3E3E3),
    surface = Color(0xFF0E0F13),
    onSurface = Color(0xFFE3E3E3),
    surfaceVariant = Color(0xFF1E1F24),
    onSurfaceVariant = Color(0xFFC4C6D0),
    surfaceDim = Color(0xFF0A0B0E),
    surfaceBright = Color(0xFF3A3E45),
    surfaceContainerLowest = Color(0xFF090A0C),
    surfaceContainerLow = Color(0xFF16181D),
    surfaceContainer = Color(0xFF1A1C21),
    surfaceContainerHigh = Color(0xFF22242B),
    surfaceContainerHighest = Color(0xFF2B2E36),
    outline = Color(0xFF8E9199),
    outlineVariant = Color(0xFF44474E),
)

/** Gemini gradient stops (sparkle gradient): blue -> violet -> pink. */
object GeminiColors {
    val Blue = Color(0xFF4796E3)
    val Violet = Color(0xFF9177C7)
    val Pink = Color(0xFFD96570)
}

private val DeerFlowTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
)

private val DeerFlowShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun DeerFlowTheme(
    preference: ThemePreference = ThemePreference.System,
    content: @Composable () -> Unit,
) {
    val dark = when (preference) {
        ThemePreference.System -> isSystemInDarkTheme()
        ThemePreference.Light -> false
        ThemePreference.Dark -> true
    }
    MaterialExpressiveTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        motionScheme = MotionScheme.expressive(),
        typography = DeerFlowTypography,
        shapes = DeerFlowShapes,
        content = content,
    )
}
