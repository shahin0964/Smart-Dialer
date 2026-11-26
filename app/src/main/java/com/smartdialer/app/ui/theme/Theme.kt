package com.smartdialer.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

data class AppThemePalette(
    val id: String,
    val name: String,
    val primary: Color,
    val primaryLight: Color,
    val primaryUltraLight: Color,
    val background: Color,
    val surface: Color,
    val cardBackground: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val divider: Color,
    val isDark: Boolean,
    val dialpadButtonBg: Color,
    val dialpadText: Color,
    val dialpadSubText: Color,
    val callButtonColor: Color,
    val callerScreenGradient: List<Color>,
    val callerAccent: Color,
    val callerButtonBg: Color,
    val topBarBg: Color,
    val bottomBarBg: Color
)

val ClassicDarkPalette = AppThemePalette(
    id = "CLASSIC_DARK",
    name = "Classic Dark",
    primary = Color(0xFF6366F1),
    primaryLight = Color(0xFF818CF8),
    primaryUltraLight = Color(0xFF1E1B4B),
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    cardBackground = Color(0xFF1E293B),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFF94A3B8),
    textMuted = Color(0xFF64748B),
    divider = Color(0xFF334155),
    isDark = true,
    dialpadButtonBg = Color(0xFF1E293B),
    dialpadText = Color(0xFFF8FAFC),
    dialpadSubText = Color(0xFF94A3B8),
    callButtonColor = Color(0xFF22C55E),
    callerScreenGradient = listOf(Color(0xFF0B1021), Color(0xFF111827), Color(0xFF1E293B)),
    callerAccent = Color(0xFF6366F1),
    callerButtonBg = Color(0x33FFFFFF),
    topBarBg = Color(0xFF0F172A),
    bottomBarBg = Color(0xFF0F172A)
)

val IosGlassPalette = AppThemePalette(
    id = "IOS_GLASS",
    name = "iOS Glassmorphism",
    primary = Color(0xFF0A84FF),
    primaryLight = Color(0xFF5AC8FA),
    primaryUltraLight = Color(0xFF1C2C3E),
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    cardBackground = Color(0xFF2C2C2E),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF8E8E93),
    textMuted = Color(0xFF636366),
    divider = Color(0xFF38383A),
    isDark = true,
    dialpadButtonBg = Color(0xFF242426),
    dialpadText = Color(0xFFFFFFFF),
    dialpadSubText = Color(0xFF8E8E93),
    callButtonColor = Color(0xFF30D158),
    callerScreenGradient = listOf(Color(0xFF1C1C1E), Color(0xFF2C2C2E), Color(0xFF000000)),
    callerAccent = Color(0xFF0A84FF),
    callerButtonBg = Color(0x38FFFFFF),
    topBarBg = Color(0xFF1C1C1E),
    bottomBarBg = Color(0xFF1C1C1E)
)

val SamsungOneUiPalette = AppThemePalette(
    id = "SAMSUNG_ONE_UI",
    name = "Samsung One UI",
    primary = Color(0xFF0284C7),
    primaryLight = Color(0xFF38BDF8),
    primaryUltraLight = Color(0xFF0C243B),
    background = Color(0xFF0B1120),
    surface = Color(0xFF0F172A),
    cardBackground = Color(0xFF1E293B),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFF94A3B8),
    textMuted = Color(0xFF64748B),
    divider = Color(0xFF1E293B),
    isDark = true,
    dialpadButtonBg = Color(0xFF1E293B),
    dialpadText = Color(0xFFF8FAFC),
    dialpadSubText = Color(0xFF94A3B8),
    callButtonColor = Color(0xFF10B981),
    callerScreenGradient = listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF0B1120)),
    callerAccent = Color(0xFF38BDF8),
    callerButtonBg = Color(0x3338BDF8),
    topBarBg = Color(0xFF0B1120),
    bottomBarBg = Color(0xFF0F172A)
)

val CyberpunkAmoledPalette = AppThemePalette(
    id = "CYBERPUNK_AMOLED",
    name = "Cyberpunk AMOLED",
    primary = Color(0xFF00F0FF),
    primaryLight = Color(0xFFFF007F),
    primaryUltraLight = Color(0xFF002B30),
    background = Color(0xFF000000),
    surface = Color(0xFF080814),
    cardBackground = Color(0xFF0E0E22),
    textPrimary = Color(0xFF00F0FF),
    textSecondary = Color(0xFF9D9DB8),
    textMuted = Color(0xFF6B6B8A),
    divider = Color(0xFF1F1B3B),
    isDark = true,
    dialpadButtonBg = Color(0xFF0D0D1F),
    dialpadText = Color(0xFF00F0FF),
    dialpadSubText = Color(0xFFFF007F),
    callButtonColor = Color(0xFF00F0FF),
    callerScreenGradient = listOf(Color(0xFF000000), Color(0xFF070714), Color(0xFF120324)),
    callerAccent = Color(0xFF00F0FF),
    callerButtonBg = Color(0xFF141428),
    topBarBg = Color(0xFF000000),
    bottomBarBg = Color(0xFF080814)
)

val EmeraldAuroraPalette = AppThemePalette(
    id = "EMERALD_AURORA",
    name = "Emerald Aurora",
    primary = Color(0xFF10B981),
    primaryLight = Color(0xFF34D399),
    primaryUltraLight = Color(0xFF063326),
    background = Color(0xFF021B1A),
    surface = Color(0xFF042F2E),
    cardBackground = Color(0xFF064E3B),
    textPrimary = Color(0xFFECFDF5),
    textSecondary = Color(0xFF6EE7B7),
    textMuted = Color(0xFF34D399).copy(alpha = 0.7f),
    divider = Color(0xFF064E3B),
    isDark = true,
    dialpadButtonBg = Color(0xFF064E3B),
    dialpadText = Color(0xFFECFDF5),
    dialpadSubText = Color(0xFF6EE7B7),
    callButtonColor = Color(0xFF10B981),
    callerScreenGradient = listOf(Color(0xFF042F2E), Color(0xFF064E3B), Color(0xFF021B1A)),
    callerAccent = Color(0xFF10B981),
    callerButtonBg = Color(0x3310B981),
    topBarBg = Color(0xFF021B1A),
    bottomBarBg = Color(0xFF042F2E)
)

val SunsetVelvetPalette = AppThemePalette(
    id = "SUNSET_VELVET",
    name = "Sunset Velvet",
    primary = Color(0xFFE11D48),
    primaryLight = Color(0xFFFB7185),
    primaryUltraLight = Color(0xFF3B0718),
    background = Color(0xFF180A28),
    surface = Color(0xFF3B0764),
    cardBackground = Color(0xFF4C0519),
    textPrimary = Color(0xFFFFF1F2),
    textSecondary = Color(0xFFFDA4AF),
    textMuted = Color(0xFFF43F5E).copy(alpha = 0.7f),
    divider = Color(0xFF4C0519),
    isDark = true,
    dialpadButtonBg = Color(0xFF3B0764),
    dialpadText = Color(0xFFFFF1F2),
    dialpadSubText = Color(0xFFFDA4AF),
    callButtonColor = Color(0xFFE11D48),
    callerScreenGradient = listOf(Color(0xFF4C0519), Color(0xFF3B0764), Color(0xFF180A28)),
    callerAccent = Color(0xFFE11D48),
    callerButtonBg = Color(0x33E11D48),
    topBarBg = Color(0xFF180A28),
    bottomBarBg = Color(0xFF3B0764)
)

val LightPurplePalette = AppThemePalette(
    id = "LIGHT_PURPLE",
    name = "Classic Purple (Light)",
    primary = Color(0xFF5B42F3),
    primaryLight = Color(0xFF8B5CF6),
    primaryUltraLight = Color(0xFFF5F3FF),
    background = Color(0xFFF6F8FC),
    surface = Color(0xFFFFFFFF),
    cardBackground = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF1E293B),
    textSecondary = Color(0xFF64748B),
    textMuted = Color(0xFF94A3B8),
    divider = Color(0xFFE2E8F0),
    isDark = false,
    dialpadButtonBg = Color(0xFFFFFFFF),
    dialpadText = Color(0xFF1E293B),
    dialpadSubText = Color(0xFF64748B),
    callButtonColor = Color(0xFF16A34A),
    callerScreenGradient = listOf(Color(0xFF0F0C29), Color(0xFF1B1B3A), Color(0xFF100E2E)),
    callerAccent = Color(0xFF5B42F3),
    callerButtonBg = Color(0x33FFFFFF),
    topBarBg = Color(0xFFFFFFFF),
    bottomBarBg = Color(0xFFFFFFFF)
)

val PureAmoledPalette = AppThemePalette(
    id = "AMOLED_BLACK",
    name = "Pure AMOLED Black",
    primary = Color(0xFF3B82F6),
    primaryLight = Color(0xFF60A5FA),
    primaryUltraLight = Color(0xFF0B192E),
    background = Color(0xFF000000),
    surface = Color(0xFF0A0A0A),
    cardBackground = Color(0xFF121212),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFA1A1AA),
    textMuted = Color(0xFF71717A),
    divider = Color(0xFF27272A),
    isDark = true,
    dialpadButtonBg = Color(0xFF141414),
    dialpadText = Color(0xFFFFFFFF),
    dialpadSubText = Color(0xFF71717A),
    callButtonColor = Color(0xFF22C55E),
    callerScreenGradient = listOf(Color(0xFF000000), Color(0xFF050505), Color(0xFF0A0A0A)),
    callerAccent = Color(0xFF3B82F6),
    callerButtonBg = Color(0x28FFFFFF),
    topBarBg = Color(0xFF000000),
    bottomBarBg = Color(0xFF000000)
)

val RoyalGoldPalette = AppThemePalette(
    id = "ROYAL_GOLD",
    name = "Royal Obsidian Gold",
    primary = Color(0xFFF59E0B),
    primaryLight = Color(0xFFFBBF24),
    primaryUltraLight = Color(0xFF2B1D05),
    background = Color(0xFF0F0D0A),
    surface = Color(0xFF1C1914),
    cardBackground = Color(0xFF262119),
    textPrimary = Color(0xFFFEF3C7),
    textSecondary = Color(0xFFFDE68A),
    textMuted = Color(0xFFD97706).copy(alpha = 0.8f),
    divider = Color(0xFF45361E),
    isDark = true,
    dialpadButtonBg = Color(0xFF1C1914),
    dialpadText = Color(0xFFFEF3C7),
    dialpadSubText = Color(0xFFD97706),
    callButtonColor = Color(0xFF10B981),
    callerScreenGradient = listOf(Color(0xFF0F0D0A), Color(0xFF1F1608), Color(0xFF2B1D05)),
    callerAccent = Color(0xFFF59E0B),
    callerButtonBg = Color(0x33F59E0B),
    topBarBg = Color(0xFF0F0D0A),
    bottomBarBg = Color(0xFF1C1914)
)

object AppThemeRegistry {
    fun getPalette(id: String): AppThemePalette {
        return when (id) {
            "IOS_GLASS" -> IosGlassPalette
            "SAMSUNG_ONE_UI" -> SamsungOneUiPalette
            "CYBERPUNK_AMOLED" -> CyberpunkAmoledPalette
            "EMERALD_AURORA" -> EmeraldAuroraPalette
            "SUNSET_VELVET" -> SunsetVelvetPalette
            "LIGHT_PURPLE" -> LightPurplePalette
            "AMOLED_BLACK" -> PureAmoledPalette
            "ROYAL_GOLD" -> RoyalGoldPalette
            else -> ClassicDarkPalette
        }
    }
}

val LocalAppTheme = staticCompositionLocalOf { ClassicDarkPalette }

@Composable
fun SmartDialerTheme(
    themeId: String = "CLASSIC_DARK",
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    val palette = AppThemeRegistry.getPalette(themeId)
    val effectiveIsDark = darkTheme ?: palette.isDark

    val colorScheme = if (effectiveIsDark) {
        darkColorScheme(
            primary = palette.primary,
            onPrimary = Color.White,
            primaryContainer = palette.primaryUltraLight,
            onPrimaryContainer = Color.White,
            secondary = Sim1Blue,
            onSecondary = Color.White,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.cardBackground,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.divider
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = Color.White,
            primaryContainer = palette.primaryUltraLight,
            onPrimaryContainer = palette.primary,
            secondary = Sim1Blue,
            onSecondary = Color.White,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.cardBackground,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.divider
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = palette.topBarBg.toArgb()
                window.navigationBarColor = palette.bottomBarBg.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !effectiveIsDark
                controller.isAppearanceLightNavigationBars = !effectiveIsDark
            }
        }
    }

    CompositionLocalProvider(LocalAppTheme provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
