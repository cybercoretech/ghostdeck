package net.cybercoretech.ghostdeck.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import net.cybercoretech.ghostdeck.theme.ThemePack

/** Provides the tokens for [pack], the live motion policy, and the bundled type (Inter for UI text). */
@Composable
fun GhostdeckTheme(pack: ThemePack, content: @Composable () -> Unit) {
    val tokens = remember(pack) { tokensFor(pack) }
    val policy = rememberMotionPolicy()
    val colors = darkColorScheme(
        background = tokens.ground, surface = tokens.glass, primary = tokens.accent, secondary = tokens.accentAlt,
        onBackground = tokens.text, onSurface = tokens.text, onSurfaceVariant = tokens.muted, error = tokens.error,
        outline = tokens.border,
    )
    val base = Typography()
    val typography = Typography(
        displayLarge = base.displayLarge.withFamily(GhostdeckFonts.display),
        displayMedium = base.displayMedium.withFamily(GhostdeckFonts.display),
        displaySmall = base.displaySmall.withFamily(GhostdeckFonts.display),
        headlineLarge = base.headlineLarge.withFamily(GhostdeckFonts.display),
        headlineMedium = base.headlineMedium.withFamily(GhostdeckFonts.display),
        headlineSmall = base.headlineSmall.withFamily(GhostdeckFonts.display),
        titleLarge = base.titleLarge.withFamily(GhostdeckFonts.ui),
        titleMedium = base.titleMedium.withFamily(GhostdeckFonts.ui),
        titleSmall = base.titleSmall.withFamily(GhostdeckFonts.ui),
        bodyLarge = base.bodyLarge.withFamily(GhostdeckFonts.ui),
        bodyMedium = base.bodyMedium.withFamily(GhostdeckFonts.ui),
        bodySmall = base.bodySmall.withFamily(GhostdeckFonts.ui),
        labelLarge = base.labelLarge.withFamily(GhostdeckFonts.ui),
        labelMedium = base.labelMedium.withFamily(GhostdeckFonts.ui),
        labelSmall = base.labelSmall.withFamily(GhostdeckFonts.ui),
    )
    CompositionLocalProvider(LocalTokens provides tokens, LocalMotionPolicy provides policy) {
        MaterialTheme(colorScheme = colors, typography = typography, content = content)
    }
}

private fun TextStyle.withFamily(f: androidx.compose.ui.text.font.FontFamily) = copy(fontFamily = f)
