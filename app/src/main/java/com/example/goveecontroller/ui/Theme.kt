package com.example.goveecontroller.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Material 3 Expressive styling, built on the stable 1.4 components:
 * heavy display type, oversized corner radii, and softer tonal surfaces.
 */
private val BaseTypography = Typography()

private val GlowTypography = BaseTypography.copy(
    displayLarge = BaseTypography.displayLarge.copy(
        fontWeight = FontWeight.Black,
        letterSpacing = (-1.6).sp,
    ),
    displayMedium = BaseTypography.displayMedium.copy(
        fontWeight = FontWeight.Black,
        letterSpacing = (-1.2).sp,
    ),
    displaySmall = BaseTypography.displaySmall.copy(
        fontWeight = FontWeight.Black,
        letterSpacing = (-0.8).sp,
    ),
    headlineLarge = BaseTypography.headlineLarge.copy(
        fontWeight = FontWeight.Black,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = BaseTypography.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = BaseTypography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = BaseTypography.labelMedium.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp,
    ),
)

private val GlowShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val FallbackLight = lightColorScheme(
    primary = Color(0xFF00639B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE5FF),
    onPrimaryContainer = Color(0xFF001D33),
    secondary = Color(0xFF50606F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3E4F7),
    onSecondaryContainer = Color(0xFF0C1D2B),
    tertiary = Color(0xFF6A5778),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF2DAFF),
    onTertiaryContainer = Color(0xFF251431),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF181C20),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF181C20),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41474D),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F4F9),
    surfaceContainer = Color(0xFFEBEEF4),
    surfaceContainerHigh = Color(0xFFE5E8EE),
    surfaceContainerHighest = Color(0xFFDFE3E9),
    outline = Color(0xFF71787E),
    outlineVariant = Color(0xFFC1C7CE),
    inverseSurface = Color(0xFF2D3135),
    inverseOnSurface = Color(0xFFEFF1F5),
    inversePrimary = Color(0xFF96CCFF),
    surfaceTint = Color(0xFF00639B),
)

private val FallbackDark = darkColorScheme(
    primary = Color(0xFF96CCFF),
    onPrimary = Color(0xFF003354),
    primaryContainer = Color(0xFF004A77),
    onPrimaryContainer = Color(0xFFCDE5FF),
    secondary = Color(0xFFB9C8DA),
    onSecondary = Color(0xFF23323F),
    secondaryContainer = Color(0xFF3A4857),
    onSecondaryContainer = Color(0xFFD5E3F7),
    tertiary = Color(0xFFD7BDE4),
    onTertiary = Color(0xFF3B2949),
    tertiaryContainer = Color(0xFF523F60),
    onTertiaryContainer = Color(0xFFF2DAFF),
    background = Color(0xFF0F1418),
    onBackground = Color(0xFFDFE3E8),
    surface = Color(0xFF0F1418),
    onSurface = Color(0xFFDFE3E8),
    surfaceVariant = Color(0xFF41474D),
    onSurfaceVariant = Color(0xFFC1C7CE),
    surfaceContainerLowest = Color(0xFF0A0F13),
    surfaceContainerLow = Color(0xFF171C20),
    surfaceContainer = Color(0xFF1B2024),
    surfaceContainerHigh = Color(0xFF252B2F),
    surfaceContainerHighest = Color(0xFF30363A),
    outline = Color(0xFF8B9198),
    outlineVariant = Color(0xFF41474D),
    inverseSurface = Color(0xFFDFE3E8),
    inverseOnSurface = Color(0xFF2C3135),
    inversePrimary = Color(0xFF00639B),
    surfaceTint = Color(0xFF96CCFF),
)

@Composable
fun GoveeGlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> FallbackDark
        else -> FallbackLight
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = GlowTypography,
        shapes = GlowShapes,
        content = content,
    )
}

/** Black or white, whichever reads better on [background]. */
fun readableOn(background: Color): Color =
    if (background.luminance() > 0.55f) Color(0xFF101418) else Color.White

private fun Color.luminance(): Float {
    fun channel(c: Float) = if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).let {
        it * it * it
    }
    return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
}
