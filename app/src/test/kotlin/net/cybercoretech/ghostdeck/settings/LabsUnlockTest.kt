package net.cybercoretech.ghostdeck.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabsUnlockTest {
    private var now = 0L
    private val unlock = LabsUnlock { now }

    private fun tapSeven(gapMs: Long): Boolean {
        var result = false
        repeat(7) { result = unlock.onVersionTap(); if (it < 6) now += gapMs }
        return result
    }

    @Test fun sevenTapsWithin3sUnlocks() {
        repeat(6) { assertFalse(unlock.onVersionTap()); now += 400 }
        assertTrue(unlock.onVersionTap())
    }

    @Test fun slowTapsReset() {
        assertFalse(tapSeven(gapMs = 1_000))
        // A slow run leaves nothing behind: a fresh fast run of seven still needs all seven.
        now += 10_000
        repeat(6) { assertFalse(unlock.onVersionTap()); now += 100 }
        assertTrue(unlock.onVersionTap())
    }

    @Test fun volumeDownHeld2sUnlocks() {
        assertFalse(unlock.onVolumeDown(true))
        now += 1_000
        assertFalse(unlock.onVolumeDown(true)) // key repeat
        now += 1_000
        assertTrue(unlock.onVolumeDown(true))
    }

    @Test fun volumeDownReleasedEarlyDoesNot() {
        assertFalse(unlock.onVolumeDown(true))
        now += 1_900
        assertFalse(unlock.onVolumeDown(false))
        now += 500
        // A new press starts the clock again.
        assertFalse(unlock.onVolumeDown(true))
        now += 1_900
        assertFalse(unlock.onVolumeDown(true))
    }

    @Test fun lockAgainClears() {
        assertTrue(tapSeven(gapMs = 100))
        // Once unlocked, further taps do not report another unlock.
        assertFalse(unlock.onVersionTap())
        unlock.lock()
        assertTrue(tapSeven(gapMs = 100))
    }
}
