package net.cybercoretech.ghostdeck.home

import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperOffsetTest {
    @Test fun onePageIsZero() = assertEquals(0f, wallpaperOffset(0f, 1), 0f)

    @Test fun middleOfThreePages() = assertEquals(0.75f, wallpaperOffset(1.5f, 3), 0f)

    @Test fun clampedToTheRange() {
        assertEquals(1f, wallpaperOffset(5f, 3), 0f)
        assertEquals(0f, wallpaperOffset(-1f, 3), 0f)
    }

    @Test fun noPagesIsZero() = assertEquals(0f, wallpaperOffset(2f, 0), 0f)
}
