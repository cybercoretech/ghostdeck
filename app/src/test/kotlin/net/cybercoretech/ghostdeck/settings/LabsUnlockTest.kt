package net.cybercoretech.ghostdeck.settings

import org.junit.Assert.assertEquals
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

    @Test fun singleDownHeldTwoSecondsUnlocksWithoutRepeats() {
        var scheduledDelay = -1L
        var scheduled: (() -> Unit)? = null
        var cancelled = 0
        var unlockedByTimer = 0
        unlock.scheduler = { delay, action -> scheduledDelay = delay; scheduled = action; { cancelled++ } }
        unlock.onHoldUnlocked = { unlockedByTimer++ }
        // One KEY_DOWN, no repeat events at all.
        assertFalse(unlock.onVolumeDown(true))
        assertEquals(2_000L, scheduledDelay)
        now += 2_000
        scheduled!!.invoke() // the timer fires while the key is still down
        assertEquals(1, unlockedByTimer)
        // Releasing afterwards cancels nothing harmful and does not unlock again.
        unlock.onVolumeDown(false)
        assertEquals(1, unlockedByTimer)
    }

    @Test fun releaseCancelsTheHoldTimer() {
        var cancelled = 0
        var fired = 0
        var scheduled: (() -> Unit)? = null
        unlock.scheduler = { _, action -> scheduled = action; { cancelled++ } }
        unlock.onHoldUnlocked = { fired++ }
        unlock.onVolumeDown(true)
        unlock.onVolumeDown(false)
        assertEquals(1, cancelled)
        // Even a timer that fires late does nothing once the key was released.
        scheduled!!.invoke()
        assertEquals(0, fired)
    }

    @Test fun lockAgainClears() {
        assertTrue(tapSeven(gapMs = 100))
        // Once unlocked, further taps do not report another unlock.
        assertFalse(unlock.onVersionTap())
        unlock.lock()
        assertTrue(tapSeven(gapMs = 100))
    }

    @Test fun cancelHoldDoesNotSpendTheUnlock() {
        var cancelled = 0
        var fired = 0
        var scheduled: (() -> Unit)? = null
        unlock.scheduler = { _, action -> scheduled = action; { cancelled++ } }
        unlock.onHoldUnlocked = { fired++ }
        unlock.onVolumeDown(true)
        unlock.cancelHold()
        assertEquals(1, cancelled)
        now += 2_000
        scheduled!!.invoke() // a timer that outlived Settings does nothing
        assertEquals(0, fired)
        // The unlock is still available for a later hold.
        unlock.onVolumeDown(true)
        now += 2_000
        scheduled!!.invoke()
        assertEquals(1, fired)
    }

    @Test fun alreadyUnlockedFromPrefsDoesNotUnlockAgain() {
        val persisted = LabsUnlock(initiallyUnlocked = true) { now }
        assertFalse(persisted.onVersionTap())
        repeat(7) { persisted.onVersionTap(); now += 100 }
        assertFalse(persisted.onVersionTap())
        val late = LabsUnlock { now }
        late.syncUnlocked(true)
        repeat(7) { assertFalse(late.onVersionTap()) }
    }
}
