package net.cybercoretech.ghostdeck.ui.theme

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import net.cybercoretech.ghostdeck.testutil.RobolectricMotion
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf
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

    @Test fun orbsStopWhenPowerSaveTurnsOnWhileShown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val seen = mutableListOf<Boolean>()
        rule.setContent {
            val policy = rememberMotionPolicy()
            seen += orbsStatic(policy)
        }
        assertFalse(seen.last())
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsPowerSaveMode(true)
        context.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        rule.waitForIdle()
        assertTrue("orbs static after battery saver turns on", seen.last())
    }

    @Test fun orbsStopWhenRemoveAnimationsTurnsOnWhileShown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val seen = mutableListOf<Boolean>()
        rule.setContent {
            val policy = rememberMotionPolicy()
            seen += orbsStatic(policy)
        }
        assertFalse(seen.last())
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        context.contentResolver.notifyChange(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), null)
        rule.waitForIdle()
        assertTrue(seen.last())
    }

    private fun phaseAfter(ms: Long): Float {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pack = ThemeRepository(context).load(DEFAULT_PACK_ID)
        rule.mainClock.autoAdvance = false // the infinite drift would otherwise keep the test from idling
        rule.setContent { GhostdeckTheme(pack) { AmbientOrbs(Modifier.fillMaxSize().testTag("orbs")) } }
        rule.mainClock.advanceTimeBy(ms)
        return rule.onNodeWithTag("orbs").fetchSemanticsNode().config[OrbPhaseKey]
    }

    @Test fun orbPhaseAdvancesWhenAnimationsOn() {
        val early = phaseAfter(1_000)
        rule.mainClock.advanceTimeBy(ORB_DRIFT_MS / 2L)
        val later = rule.onNodeWithTag("orbs").fetchSemanticsNode().config[OrbPhaseKey]
        assertTrue("phase moved from $early to $later", later > early + 0.2f)
    }

    @Test fun orbPhaseStaysZeroWhenReduceMotion() {
        RobolectricMotion.disableAnimations(ApplicationProvider.getApplicationContext<Context>())
        assertEquals(0f, phaseAfter(ORB_DRIFT_MS / 2L), 0f)
    }

    @Test fun nestedThemesShareOneMotionReceiver() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val pack = ThemeRepository(app).load(DEFAULT_PACK_ID)
        rule.setContent {
            CompositionLocalProvider(LocalMotionPolicy provides rememberMotionPolicy()) {
                repeat(5) { GhostdeckTheme(pack) {} }
            }
        }
        rule.waitForIdle()
        val receivers = shadowOf(app).registeredReceivers.count { it.intentFilter.hasAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) }
        assertEquals(1, receivers)
    }
}
