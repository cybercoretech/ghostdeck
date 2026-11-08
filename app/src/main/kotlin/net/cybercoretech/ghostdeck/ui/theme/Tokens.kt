package net.cybercoretech.ghostdeck.ui.theme

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import net.cybercoretech.ghostdeck.theme.Contrast
import net.cybercoretech.ghostdeck.theme.ThemePack

data class GhostdeckTokens(
    val ground: Color,
    val glass: Color,
    val glassAlpha: Float,
    val border: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val accentAlt: Color,
    val ok: Color,
    val warn: Color,
    val error: Color,
)

private fun ThemePack.rgb(role: String): Int = roles.getValue(role).toInt(16)
private fun ThemePack.color(role: String): Color = Color(0xFF000000.toInt() or rgb(role))

/** Highest glass opacity ever used; also the fallback for a pack that fails the contrast check. */
private const val MAX_GLASS_ALPHA = 0.95f

fun tokensFor(pack: ThemePack, deckAccent: Color? = null): GhostdeckTokens = GhostdeckTokens(
    ground = pack.color("bg"),
    glass = pack.color("surface"),
    glassAlpha = Contrast.glassAlpha(pack.rgb("text_primary"), pack.rgb("surface")) ?: MAX_GLASS_ALPHA,
    border = pack.color("border"),
    text = pack.color("text_primary"),
    muted = pack.color("muted"),
    accent = deckAccent ?: pack.color("accent_hot"),
    accentAlt = pack.color("accent_alt"),
    ok = pack.color("success"),
    warn = pack.color("warning"),
    error = pack.color("error"),
)

val LocalTokens: ProvidableCompositionLocal<GhostdeckTokens> = staticCompositionLocalOf {
    error("GhostdeckTheme is not provided")
}
