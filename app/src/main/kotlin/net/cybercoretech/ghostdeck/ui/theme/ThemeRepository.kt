package net.cybercoretech.ghostdeck.ui.theme

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import net.cybercoretech.ghostdeck.theme.ThemePack
import net.cybercoretech.ghostdeck.theme.parseThemePack

const val DEFAULT_PACK_ID = "ghostdeck"
const val SYSTEM_PACK_ID = "system"

private val bundledAssets = listOf(
    "ghostdeck" to "Ghostdeck",
    "cybercore-vortex-synthetic" to "Cybercore Vortex",
    "terminal-ghost-alpha" to "Terminal Ghost",
    "cryo-terminal-blue-synthetic" to "Cryo Terminal Blue",
)

@Suppress("NewApi") // only called for the System pack, which is offered on API 31+
private val systemDynamicScheme: (Context) -> ColorScheme = { dynamicDarkColorScheme(it) }

/** [dynamicScheme] is where the System pack's colours come from; tests swap it to change them at run time. */
class ThemeRepository(
    private val context: Context,
    private val dynamicScheme: (Context) -> ColorScheme = systemDynamicScheme,
) {
    private val cache: List<ThemePack> by lazy {
        bundledAssets.map { (id, name) ->
            val json = context.assets.open("themes/$id.json").bufferedReader().use { it.readText() }
            parseThemePack(id, name, json).getOrThrow()
        }
    }

    /** The four packs vendored as assets. */
    fun bundled(): List<ThemePack> = cache

    /** [bundled] plus the dynamic-colour "System" pack on API 31+. */
    fun available(): List<ThemePack> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) cache + systemPack() else cache

    /** Unknown ids fall back to ghostdeck. */
    fun load(id: String): ThemePack =
        if (id == SYSTEM_PACK_ID && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) systemPack()
        else cache.firstOrNull { it.id == id } ?: cache.first { it.id == DEFAULT_PACK_ID }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun systemPack(): ThemePack {
        val s = dynamicScheme(context)
        fun hex(c: Color) = "%06x".format(c.toArgb() and 0xFFFFFF)
        val fixed = cache.first { it.id == DEFAULT_PACK_ID }.roles
        return ThemePack(
            SYSTEM_PACK_ID, "System",
            fixed + mapOf(
                "bg" to hex(s.background), "surface" to hex(s.surfaceContainer), "panel" to hex(s.surfaceContainerHigh),
                "border" to hex(s.outlineVariant), "line" to hex(s.outlineVariant), "selection" to hex(s.secondaryContainer),
                "hover" to hex(s.surfaceContainerHighest), "white" to hex(s.onSurface), "muted" to hex(s.onSurfaceVariant),
                "text_primary" to hex(s.onSurface), "accent_hot" to hex(s.primary), "accent_alt" to hex(s.tertiary),
                "purple" to hex(s.primary), "cyan" to hex(s.tertiary), "hot_pink" to hex(s.secondary),
                "error" to hex(s.error), "red" to hex(s.error),
            ),
        )
    }
}

/**
 * The pack for [id]. [systemColorsVersion] is bumped whenever the system colours change, so the dynamic
 * System pack is rebuilt from the new scheme instead of staying remembered by id.
 */
@Composable
fun rememberThemePack(themes: ThemeRepository, id: String, systemColorsVersion: Int): ThemePack =
    remember(themes, id, systemColorsVersion) { themes.load(id) }
