package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.model.AppFontSize
import com.example.model.ThemeDesign
import com.example.model.ThemeMode

@Immutable
data class AppPalette(
    val isDark: Boolean,
    val design: ThemeDesign,
    val screenBg: Color,
    val headerBg: Color,
    val drawerBg: Color,
    val cardBg: Color,
    val cardAltBg: Color,
    val statusStripBg: Color,
    val consoleBg: Color,
    val terminalBg: Color,
    val cardBorder: Color,
    val glowBorder: Color,
    val divider: Color,
    val accentPrimary: Color,
    val accentSecondary: Color,
    val actionHighlight: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val startButtonBg: Color,
    val usbBadgeBg: Color,
    val usbBadgeBorder: Color,
    val usbBadgeText: Color,
    val badgeAdb: Color,
    val badgeFastboot: Color,
    val badgeFlash: Color,
    val badgeTerminal: Color,
    val badgeSetting: Color,
    val badgeAbout: Color
) {
    val cardAccentBrush: Brush
        get() = Brush.horizontalGradient(
            colors = listOf(accentPrimary, accentSecondary)
        )

    val panelBorderBrush: Brush
        get() = Brush.linearGradient(
            colors = listOf(
                glowBorder.copy(alpha = if (isDark) 0.75f else 0.55f),
                cardBorder,
                accentSecondary.copy(alpha = if (isDark) 0.55f else 0.35f)
            )
        )

    val subtleSurfaceBrush: Brush
        get() = Brush.verticalGradient(
            colors = listOf(
                cardAltBg.copy(alpha = if (isDark) 0.92f else 0.65f),
                cardBg
            )
        )
}

@Immutable
data class FontConfig(
    val fontSize: AppFontSize = AppFontSize.MEDIUM
) {
    val scale: Float get() = fontSize.scaleFactor
    val consoleSp: TextUnit get() = fontSize.consoleSp.sp
    val terminalCmdSp: TextUnit get() = (fontSize.consoleSp + 1.0f).sp
    val terminalOutSp: TextUnit get() = (fontSize.consoleSp + 0.5f).sp
}

val LocalAppPalette = staticCompositionLocalOf {
    resolvePalette(ThemeMode.DARK, ThemeDesign.CYBER_NEON)
}

val LocalFontConfig = staticCompositionLocalOf {
    FontConfig(AppFontSize.MEDIUM)
}

fun resolvePalette(mode: ThemeMode, design: ThemeDesign): AppPalette {
    val isDark = mode == ThemeMode.DARK
    return when (design) {
        ThemeDesign.CYBER_NEON -> if (isDark) {
            AppPalette(
                isDark = true,
                design = design,
                screenBg = CyberDarkBg,
                headerBg = CyberDarkHeader,
                drawerBg = CyberDarkDrawer,
                cardBg = CyberDarkCard,
                cardAltBg = CyberDarkCardAlt,
                statusStripBg = Color(0xFF0B1220),
                consoleBg = CyberDarkConsole,
                terminalBg = CyberDarkTerminal,
                cardBorder = CyberDarkBorder,
                glowBorder = CyberDarkPrimary,
                divider = Color(0xFF161F2E),
                accentPrimary = CyberDarkPrimary,
                accentSecondary = CyberDarkSecondary,
                actionHighlight = CyberDarkHighlight,
                textPrimary = Color(0xFFF1F5F9),
                textSecondary = Color(0xFF94A3B8),
                textMuted = Color(0xFF475569),
                startButtonBg = CyberDarkPrimary,
                usbBadgeBg = Color(0xFF0F1A2E),
                usbBadgeBorder = CyberDarkPrimary.copy(alpha = 0.5f),
                usbBadgeText = CyberDarkPrimary,
                badgeAdb = Color(0xFF00E5FF),
                badgeFastboot = Color(0xFF00E676),
                badgeFlash = Color(0xFFFF1744),
                badgeTerminal = Color(0xFFFFB300),
                badgeSetting = Color(0xFFB388FF),
                badgeAbout = Color(0xFF00E5FF)
            )
        } else {
            AppPalette(
                isDark = false,
                design = design,
                screenBg = CyberLightBg,
                headerBg = CyberLightHeader,
                drawerBg = CyberLightDrawer,
                cardBg = CyberLightCard,
                cardAltBg = CyberLightCardAlt,
                statusStripBg = Color(0xFFE2ECF8),
                consoleBg = CyberLightConsole,
                terminalBg = CyberLightTerminal,
                cardBorder = CyberLightBorder,
                glowBorder = CyberLightPrimary,
                divider = Color(0xFFD4E1F0),
                accentPrimary = CyberLightPrimary,
                accentSecondary = CyberLightSecondary,
                actionHighlight = CyberLightHighlight,
                textPrimary = Color(0xFF0F172A),
                textSecondary = Color(0xFF334155),
                textMuted = Color(0xFF64748B),
                startButtonBg = Color(0xFF0284C7),
                usbBadgeBg = Color(0xFFE8EEFF),
                usbBadgeBorder = Color(0xFF6366F1),
                usbBadgeText = Color(0xFF312E81),
                badgeAdb = Color(0xFF0284C7),
                badgeFastboot = Color(0xFF059669),
                badgeFlash = Color(0xFFDC2626),
                badgeTerminal = Color(0xFFD97706),
                badgeSetting = Color(0xFF7C3AED),
                badgeAbout = Color(0xFF0891B2)
            )
        }

        ThemeDesign.EMERALD_MATRIX -> if (isDark) {
            AppPalette(
                isDark = true,
                design = design,
                screenBg = MatrixDarkBg,
                headerBg = MatrixDarkHeader,
                drawerBg = MatrixDarkDrawer,
                cardBg = MatrixDarkCard,
                cardAltBg = MatrixDarkCardAlt,
                statusStripBg = Color(0xFF071A12),
                consoleBg = MatrixDarkConsole,
                terminalBg = MatrixDarkTerminal,
                cardBorder = MatrixDarkBorder,
                glowBorder = MatrixDarkPrimary,
                divider = Color(0xFF143526),
                accentPrimary = MatrixDarkPrimary,
                accentSecondary = MatrixDarkSecondary,
                actionHighlight = MatrixDarkHighlight,
                textPrimary = Color(0xFFECFDF5),
                textSecondary = Color(0xFF86EFAC),
                textMuted = Color(0xFF4D8A6B),
                startButtonBg = Color(0xFF059669),
                usbBadgeBg = Color(0xFF062E22),
                usbBadgeBorder = Color(0xFF10B981),
                usbBadgeText = Color(0xFFA7F3D0),
                badgeAdb = Color(0xFF2DD4BF),
                badgeFastboot = Color(0xFF10B981),
                badgeFlash = Color(0xFFF97316),
                badgeTerminal = Color(0xFF4ADE80),
                badgeSetting = Color(0xFF34D399),
                badgeAbout = Color(0xFF22D3EE)
            )
        } else {
            AppPalette(
                isDark = false,
                design = design,
                screenBg = MatrixLightBg,
                headerBg = MatrixLightHeader,
                drawerBg = MatrixLightDrawer,
                cardBg = MatrixLightCard,
                cardAltBg = MatrixLightCardAlt,
                statusStripBg = Color(0xFFDCF2E6),
                consoleBg = MatrixLightConsole,
                terminalBg = MatrixLightTerminal,
                cardBorder = MatrixLightBorder,
                glowBorder = MatrixLightPrimary,
                divider = Color(0xFFC7E6D5),
                accentPrimary = MatrixLightPrimary,
                accentSecondary = MatrixLightSecondary,
                actionHighlight = MatrixLightHighlight,
                textPrimary = Color(0xFF06281A),
                textSecondary = Color(0xFF1E4E3A),
                textMuted = Color(0xFF4B7A65),
                startButtonBg = Color(0xFF059669),
                usbBadgeBg = Color(0xFFD1FAE5),
                usbBadgeBorder = Color(0xFF059669),
                usbBadgeText = Color(0xFF064E3B),
                badgeAdb = Color(0xFF0D9488),
                badgeFastboot = Color(0xFF059669),
                badgeFlash = Color(0xFFEA580C),
                badgeTerminal = Color(0xFF15803D),
                badgeSetting = Color(0xFF0F766E),
                badgeAbout = Color(0xFF047857)
            )
        }

        ThemeDesign.CRIMSON_FORGE -> if (isDark) {
            AppPalette(
                isDark = true,
                design = design,
                screenBg = CrimsonDarkBg,
                headerBg = CrimsonDarkHeader,
                drawerBg = CrimsonDarkDrawer,
                cardBg = CrimsonDarkCard,
                cardAltBg = CrimsonDarkCardAlt,
                statusStripBg = Color(0xFF1C0C12),
                consoleBg = CrimsonDarkConsole,
                terminalBg = CrimsonDarkTerminal,
                cardBorder = CrimsonDarkBorder,
                glowBorder = CrimsonDarkPrimary,
                divider = Color(0xFF3B1825),
                accentPrimary = CrimsonDarkPrimary,
                accentSecondary = CrimsonDarkSecondary,
                actionHighlight = CrimsonDarkHighlight,
                textPrimary = Color(0xFFFFF1F2),
                textSecondary = Color(0xFFFDA4AF),
                textMuted = Color(0xFF9F606D),
                startButtonBg = Color(0xFFE11D48),
                usbBadgeBg = Color(0xFF33111D),
                usbBadgeBorder = Color(0xFFF43F5E),
                usbBadgeText = Color(0xFFFFE4E6),
                badgeAdb = Color(0xFFF59E0B),
                badgeFastboot = Color(0xFFF43F5E),
                badgeFlash = Color(0xFFEF4444),
                badgeTerminal = Color(0xFFFBBF24),
                badgeSetting = Color(0xFFFB7185),
                badgeAbout = Color(0xFFF97316)
            )
        } else {
            AppPalette(
                isDark = false,
                design = design,
                screenBg = CrimsonLightBg,
                headerBg = CrimsonLightHeader,
                drawerBg = CrimsonLightDrawer,
                cardBg = CrimsonLightCard,
                cardAltBg = CrimsonLightCardAlt,
                statusStripBg = Color(0xFFF9E0E5),
                consoleBg = CrimsonLightConsole,
                terminalBg = CrimsonLightTerminal,
                cardBorder = CrimsonLightBorder,
                glowBorder = CrimsonLightPrimary,
                divider = Color(0xFFF1CFD6),
                accentPrimary = CrimsonLightPrimary,
                accentSecondary = CrimsonLightSecondary,
                actionHighlight = CrimsonLightHighlight,
                textPrimary = Color(0xFF2A0912),
                textSecondary = Color(0xFF5E2232),
                textMuted = Color(0xFF8C4E5E),
                startButtonBg = Color(0xFFE11D48),
                usbBadgeBg = Color(0xFFFFE4E6),
                usbBadgeBorder = Color(0xFFE11D48),
                usbBadgeText = Color(0xFF881337),
                badgeAdb = Color(0xFFD97706),
                badgeFastboot = Color(0xFFE11D48),
                badgeFlash = Color(0xFFDC2626),
                badgeTerminal = Color(0xFFB45309),
                badgeSetting = Color(0xFFBE123C),
                badgeAbout = Color(0xFFC2410C)
            )
        }
    }
}

private fun buildScaledTypography(scale: Float): Typography {
    return Typography(
        titleLarge = Typography.titleLarge.copy(fontSize = (22f * scale).sp),
        titleMedium = Typography.titleMedium.copy(fontSize = (16f * scale).sp),
        bodyLarge = Typography.bodyLarge.copy(fontSize = (16f * scale).sp),
        bodyMedium = Typography.bodyMedium.copy(fontSize = (14f * scale).sp),
        bodySmall = Typography.bodySmall.copy(fontSize = (12f * scale).sp),
        labelLarge = Typography.labelLarge.copy(fontSize = (14f * scale).sp),
        labelMedium = Typography.labelMedium.copy(fontSize = (12f * scale).sp),
        labelSmall = Typography.labelSmall.copy(fontSize = (11f * scale).sp)
    )
}

@Composable
fun MyApplicationTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    themeDesign: ThemeDesign = ThemeDesign.CYBER_NEON,
    fontSize: AppFontSize = AppFontSize.MEDIUM,
    content: @Composable () -> Unit
) {
    val palette = resolvePalette(themeMode, themeDesign)
    val fontConfig = FontConfig(fontSize)

    val colorScheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.accentPrimary,
            onPrimary = Color.Black,
            secondary = palette.accentSecondary,
            onSecondary = Color.Black,
            background = palette.screenBg,
            onBackground = palette.textPrimary,
            surface = palette.cardBg,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.cardAltBg,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.cardBorder
        )
    } else {
        lightColorScheme(
            primary = palette.accentPrimary,
            onPrimary = Color.White,
            secondary = palette.accentSecondary,
            onSecondary = Color.White,
            background = palette.screenBg,
            onBackground = palette.textPrimary,
            surface = palette.cardBg,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.cardAltBg,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.cardBorder
        )
    }

    CompositionLocalProvider(
        LocalAppPalette provides palette,
        LocalFontConfig provides fontConfig
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = buildScaledTypography(fontSize.scaleFactor),
            content = content
        )
    }
}
