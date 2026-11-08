package net.cybercoretech.ghostdeck.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MotionTest {
    @get:Rule val rule = createComposeRule()

    @Test fun materializeInstantWhenReduceMotion() {
        rule.mainClock.autoAdvance = false
        var first: Float? = null
        rule.setContent {
            val p by materializeProgress(visible = true, reduceMotion = true)
            if (first == null) first = p
        }
        assertEquals("alpha is 1 on the first frame", 1f, first!!, 0f)
    }

    @Test fun materializeStartsHiddenAndFinishesWhenMotionOn() {
        rule.mainClock.autoAdvance = false
        val seen = mutableListOf<Float>()
        rule.setContent {
            val p by materializeProgress(visible = true, reduceMotion = false)
            seen += p
        }
        assertEquals(0f, seen.first(), 0f)
        rule.mainClock.advanceTimeBy(MATERIALIZE_MS + 100L)
        rule.waitForIdle()
        assertEquals(1f, seen.last(), 0.001f)
        assertTrue(MATERIALIZE_MS <= 600)
    }

    @Test fun orbsStaticWhenPowerSave() {
        assertTrue(orbsStatic(policy(reduce = false, save = true)))
        assertTrue(orbsStatic(policy(reduce = true, save = false)))
        assertFalse(orbsStatic(policy(reduce = false, save = false)))
    }

    private fun policy(reduce: Boolean, save: Boolean) = object : MotionPolicy {
        override val reduceMotion = reduce
        override val powerSave = save
    }
}
