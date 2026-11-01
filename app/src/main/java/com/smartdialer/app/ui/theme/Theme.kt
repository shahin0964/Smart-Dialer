package com.smartdialer.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = PurplePrimary,
    onPrimary = Color.White,
    primaryContainer = PurpleUltraLight,
    onPrimaryContainer = PurpleDark,
    secondary = Sim1Blue,
    onSecondary = Color.White,
    background = BgLight,
    onBackground = TextDark,
    surface = BgCardWhite,
    onSurface = TextDark,
    surfaceVariant = BgLight,
    onSurfaceVariant = TextMuted,
    outline = DividerColor
)

private val DarkColorScheme = darkColorScheme(
    primary = PurpleLight,
    onPrimary = Color.White,
    primaryContainer = PurpleDark,
    onPrimaryContainer = Color.White,
    secondary = Sim1Blue,
    onSecondary = Color.White,
    background = CallBgDark1,
    onBackground = Color.White,
    surface = CallBgDark2,
    onSurface = Color.White,
    surfaceVariant = CallBgDark3,
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0x33FFFFFF)
)

@Composable
fun SmartDialerTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = (if (darkTheme) CallBgDark1 else Color.White).toArgb()
                window.navigationBarColor = (if (darkTheme) CallBgDark3 else Color.White).toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
