package net.cybercoretech.ghostdeck.testutil

import android.content.Context
import android.provider.Settings

/**
 * Turns "Remove animations" on. Tests that launch MainActivity must call this first: the ambient orbs
 * loop forever otherwise, and Robolectric then spends minutes idling the paused main looper on frame callbacks.
 */
object RobolectricMotion {
    fun disableAnimations(context: Context) {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }
}
