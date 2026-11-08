package net.cybercoretech.ghostdeck.settings

import net.cybercoretech.ghostdeck.model.GridSize

/**
 * The diagnostics export (spec 10a): app version, Android version, grid size and pack ids, nothing else.
 * No app names, package ids or layout contents ever go in here.
 */
fun diagnosticsText(
    version: String?,
    release: String,
    sdk: Int,
    grid: GridSize,
    themePackId: String,
    iconPackId: String?,
): String = buildString {
    appendLine("Ghostdeck $version")
    appendLine("Android $release (API $sdk)")
    appendLine("Grid ${grid.cols}x${grid.rows}")
    appendLine("Theme pack $themePackId")
    appendLine("Icon pack ${iconPackId ?: "system"}")
}
