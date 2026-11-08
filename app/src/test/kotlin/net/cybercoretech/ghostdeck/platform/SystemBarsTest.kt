package net.cybercoretech.ghostdeck.platform

import android.app.WallpaperColors
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemBarsTest {
    @Test fun lightWallpaperOnHomeGetsDarkIcons() {
        assertTrue(barIconsDark(WallpaperColors.HINT_SUPPORTS_DARK_TEXT, homeShowing = true))
    }

    @Test fun darkWallpaperKeepsLightIcons() {
        assertFalse(barIconsDark(0, homeShowing = true))
        assertFalse("no colours known yet", barIconsDark(null, homeShowing = true))
    }

    @Test fun ownConstantMatchesPlatform() {
        assertTrue(HINT_SUPPORTS_DARK_TEXT == WallpaperColors.HINT_SUPPORTS_DARK_TEXT)
    }

    @Test fun opaqueScreensKeepLightIcons() {
        // Settings and onboarding paint the dark theme ground over the wallpaper.
        assertFalse(barIconsDark(WallpaperColors.HINT_SUPPORTS_DARK_TEXT, homeShowing = false))
    }
}
