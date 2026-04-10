package com.hexadecinull.vineos.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ─── Static fallback color schemes (Android < 12) ────────────────────────────
// These are used when dynamic color (Material You wallpaper extraction) is not available.

private val VineLightColorScheme = lightColorScheme(
    primary = VineGreen40,
    onPrimary = VineNeutral99,
    primaryContainer = VineGreen90,
    onPrimaryContainer = VineGreen10,
    secondary = VineBrown40,
    onSecondary = VineNeutral99,
    secondaryContainer = VineBrown90,
    onSecondaryContainer = VineBrown10,
    tertiary = VineTeal40,
    onTertiary = VineNeutral99,
    tertiaryContainer = VineTeal90,
    onTertiaryContainer = VineTeal10,
    error = VineError40,
    onError = VineNeutral99,
    errorContainer = VineError90,
    onErrorContainer = VineError10,
    background = VineNeutral99,
    onBackground = VineNeutral10,
    surface = VineNeutral99,
    onSurface = VineNeutral10,
    surfaceVariant = VineNeutralVariant90,
    onSurfaceVariant = VineNeutralVariant30,
    outline = VineNeutralVariant50,
    outlineVariant = VineNeutralVariant80,
)

private val VineDarkColorScheme = darkColorScheme(
    primary = VineGreen80,
    onPrimary = VineGreen20,
    primaryContainer = VineGreen30,
    onPrimaryContainer = VineGreen90,
    secondary = VineBrown80,
    onSecondary = VineBrown20,
    secondaryContainer = VineBrown30,
    onSecondaryContainer = VineBrown90,
    tertiary = VineTeal80,
    onTertiary = VineTeal20,
    tertiaryContainer = VineTeal30,
    onTertiaryContainer = VineTeal90,
    error = VineError80,
    onError = VineError20,
    errorContainer = VineError30,
    onErrorContainer = VineError90,
    background = VineNeutral10,
    onBackground = VineNeutral90,
    surface = VineNeutral10,
    onSurface = VineNeutral90,
    surfaceVariant = VineNeutralVariant30,
    onSurfaceVariant = VineNeutralVariant80,
    outline = VineNeutralVariant60,
    outlineVariant = VineNeutralVariant30,
)

// ─── VineOSTheme ─────────────────────────────────────────────────────────────

/**
 * VineOS application theme.
 *
 * On Android 12+ (API 31+): uses dynamic color extracted from the user's wallpaper
 * (Material You). This respects the system's color scheme automatically.
 *
 * On Android 8–11 (API 26–30): falls back to VineOS's custom static color scheme
 * based on vine green + earthy tones.
 *
 * Dark/light mode follows the system setting in both cases.
 */
@Composable
fun VineOSTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // dynamicColor can be disabled explicitly (e.g. in screenshots, dev settings toggle)
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Material You: dynamic color available on Android 12+
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        // Static fallback for Android 8–11
        darkTheme -> VineDarkColorScheme
        else -> VineLightColorScheme
    }

    // Apply status bar / navigation bar color to match the theme surface color.
    // This gives an edge-to-edge feel consistent with modern Android design.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VineTypography,
        content = content
    )
}
