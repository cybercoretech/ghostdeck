package net.cybercoretech.ghostdeck.ui.theme

import android.util.Log
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
    /** True when the pack failed the text-on-glass contrast check and [glassAlpha] fell back to the maximum. */
    val contrastFallback: Boolean = false,
)

private fun ThemePack.rgb(role: String): Int = roles.getValue(role).toInt(16)
private fun ThemePack.color(role: String): Color = Color(0xFF000000.toInt() or rgb(role))

/** Highest glass opacity ever used; also the fallback for a pack that fails the contrast check. */
private const val MAX_GLASS_ALPHA = 0.95f

fun tokensFor(pack: ThemePack, deckAccent: Color? = null): GhostdeckTokens {
    val measured = Contrast.glassAlpha(pack.rgb("text_primary"), pack.rgb("surface"))
    // Only the dynamic System pack can fail this at run time; bundled packs are checked by ThemeAssetsTest.
    if (measured == null) Log.w("Ghostdeck", "Theme pack ${pack.id} fails the contrast check; using the maximum glass opacity")
    return tokens(pack, deckAccent, measured)
}

private fun tokens(pack: ThemePack, deckAccent: Color?, measured: Float?): GhostdeckTokens = GhostdeckTokens(
    ground = pack.color("bg"),
    glass = pack.color("surface"),
    glassAlpha = measured ?: MAX_GLASS_ALPHA,
    border = pack.color("border"),
    text = pack.color("text_primary"),
    muted = pack.color("muted"),
    accent = deckAccent ?: pack.color("accent_hot"),
    accentAlt = pack.color("accent_alt"),
    ok = pack.color("success"),
    warn = pack.color("warning"),
    error = pack.color("error"),
    contrastFallback = measured == null,
)

val LocalTokens: ProvidableCompositionLocal<GhostdeckTokens> = staticCompositionLocalOf {
    error("GhostdeckTheme is not provided")
}
