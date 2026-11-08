package net.cybercoretech.ghostdeck.ui.theme

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Reads the system's "Remove animations" and battery-saver state. Nothing in Ghostdeck animates against these. */
interface MotionPolicy {
    val reduceMotion: Boolean
    val powerSave: Boolean
}

class AndroidMotionPolicy(private val context: Context) : MotionPolicy {
    override val reduceMotion: Boolean
        get() = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    override val powerSave: Boolean
        get() = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
}

val LocalMotionPolicy = compositionLocalOf<MotionPolicy> {
    object : MotionPolicy {
        override val reduceMotion = false
        override val powerSave = false
    }
}

@Composable
fun rememberMotionPolicy(): MotionPolicy {
    val context = LocalContext.current
    return remember(context) { AndroidMotionPolicy(context) }
}

/** The ambient orbs hold still when animations are off or battery saver is on. */
fun orbsStatic(policy: MotionPolicy): Boolean = policy.reduceMotion || policy.powerSave

/** No motion in Ghostdeck is longer than this. */
const val MAX_MOTION_MS = 600
