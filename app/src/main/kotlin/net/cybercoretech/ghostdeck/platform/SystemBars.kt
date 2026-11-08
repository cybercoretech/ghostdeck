package net.cybercoretech.ghostdeck.platform

import android.app.WallpaperColors
import android.os.Build

/** `WallpaperColors.HINT_SUPPORTS_DARK_TEXT`, public from API 31; spelled out so minSdk 30 lints cleanly. */
const val HINT_SUPPORTS_DARK_TEXT = 1

private const val LIGHT_LUMINANCE = 0.5f

/**
 * Whether the status and navigation bar icons should be dark. Only over the bare wallpaper (home) and only when
 * the wallpaper suits dark text. Settings and onboarding paint the theme's dark ground, so they keep light icons.
 */
fun barIconsDark(colorHints: Int?, homeShowing: Boolean): Boolean =
    homeShowing && colorHints != null && colorHints and HINT_SUPPORTS_DARK_TEXT != 0

/**
 * The wallpaper's colour hints (no permission needed, unlike reading its pixels). API 30 has no public hints, so
 * there a light primary colour stands in for [HINT_SUPPORTS_DARK_TEXT].
 */
fun colorHintsOf(colors: WallpaperColors?): Int? = when {
    colors == null -> null
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> colors.colorHints
    colors.primaryColor.luminance() > LIGHT_LUMINANCE -> HINT_SUPPORTS_DARK_TEXT
    else -> 0
}
