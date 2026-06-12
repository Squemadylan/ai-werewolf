package com.squemadylan.wolfcha.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = WolfchaPrimary,
    onPrimary = TextPrimary,
    primaryContainer = WolfchaPrimaryDark,
    onPrimaryContainer = TextPrimary,
    secondary = WolfchaSecondary,
    onSecondary = TextPrimary,
    secondaryContainer = WolfchaSecondary.copy(alpha = 0.2f),
    onSecondaryContainer = TextPrimary,
    tertiary = WolfchaAccent,
    onTertiary = TextPrimary,
    tertiaryContainer = WolfchaAccent.copy(alpha = 0.2f),
    onTertiaryContainer = TextPrimary,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkCard,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = TextPrimary,
    errorContainer = ErrorRed.copy(alpha = 0.2f),
    onErrorContainer = TextPrimary,
    outline = TextMuted,
    outlineVariant = TextMuted.copy(alpha = 0.5f),
    scrim = Color.Black.copy(alpha = 0.5f),
    inverseSurface = TextPrimary,
    inverseOnSurface = DarkBackground,
    inversePrimary = WolfchaPrimary.copy(alpha = 0.8f),
    surfaceTint = WolfchaPrimary
)

@Composable
fun WolfchaTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> DarkColorScheme // Always use dark theme for wolfcha
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
