package net.cybercoretech.ghostdeck.ui.theme

import androidx.activity.ComponentActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class GlassSurfaceTest {
    /** Robolectric has no shadow for it: flip the framework's own flag. */
    private fun setCrossWindowBlur(on: Boolean) {
        val cls = Class.forName("android.view.CrossWindowBlurListeners")
        val holder = cls.getMethod("getInstance").invoke(null)
        cls.getDeclaredField("mCrossWindowBlurEnabled").apply { isAccessible = true }.setBoolean(holder, on)
    }

    private fun window() = Robolectric.buildActivity(ComponentActivity::class.java).setup().get().window

    @Test @Config(sdk = [30])
    fun applySheetBlurReturnsFalseBelowApi31() {
        assertFalse(applySheetBlur(window(), 48))
    }

    @Test @Config(sdk = [34])
    fun applySheetBlurReturnsTrueWhenBlurEnabled() {
        setCrossWindowBlur(true)
        assertTrue(applySheetBlur(window(), 48))
    }

    @Test @Config(sdk = [34])
    fun applySheetBlurReturnsFalseWhenBlurDisabled() {
        setCrossWindowBlur(false)
        assertFalse(applySheetBlur(window(), 48))
    }
}
