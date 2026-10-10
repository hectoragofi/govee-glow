package com.example.goveecontroller.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3 Expressive theme.
 * - Dynamic color (wallpaper HCT) on Android 12+
 * - Expressive = bigger corner radii, bolder tonal contrast, emphasis shapes.
 * Component-level expressive widgets (SplitButton, LoadingIndicator…) come
 * from material3 1.3.1 once you opt in; this theme is forward-compatible.
 */
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
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    // Slightly more expressive shapes than defaults can be added here via
    // shapes = Shapes(extraLarge = RoundedCornerShape(28.dp), …)
    MaterialTheme(colorScheme = scheme, content = content)
}
