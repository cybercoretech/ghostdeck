package net.cybercoretech.ghostdeck.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import net.cybercoretech.ghostdeck.R

private val weights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

/** Variable-font family: one TTF, one named instance per weight (API 26+ variation settings). */
@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int): FontFamily = FontFamily(
    weights.map { w -> Font(res, w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight))) },
)

object GhostdeckFonts {
    val display: FontFamily = variable(R.font.space_grotesk)
    val ui: FontFamily = variable(R.font.inter)
    val mono: FontFamily = variable(R.font.jetbrains_mono)
}
