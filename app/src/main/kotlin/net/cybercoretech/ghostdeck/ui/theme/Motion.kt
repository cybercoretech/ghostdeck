package net.cybercoretech.ghostdeck.ui.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
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

/** What [LocalMotionPolicy] holds until something provides a live policy; [GhostdeckTheme] then creates one itself. */
internal object UnprovidedMotionPolicy : MotionPolicy {
    override val reduceMotion = false
    override val powerSave = false
}

/**
 * Tests set this to true to read animation progress through semantics. In production it stays false, so the
 * animated values are never read there: reading them would invalidate the semantics every frame (spec 13).
 */
internal val LocalMotionProbe = staticCompositionLocalOf { false }

val LocalMotionPolicy = compositionLocalOf<MotionPolicy> { UnprovidedMotionPolicy }

/** A [MotionPolicy] backed by snapshot state, so composables that read it recompose when the system setting changes. */
class LiveMotionPolicy(private val source: MotionPolicy) : MotionPolicy {
    override var reduceMotion by mutableStateOf(source.reduceMotion)
        private set
    override var powerSave by mutableStateOf(source.powerSave)
        private set

    fun refresh() {
        reduceMotion = source.reduceMotion
        powerSave = source.powerSave
    }
}

/**
 * Live policy: refreshes on battery-saver broadcasts and on changes to the animator duration scale,
 * and unregisters both when it leaves the composition.
 */
@Composable
fun rememberMotionPolicy(): MotionPolicy {
    val context = LocalContext.current
    val policy = remember(context) { LiveMotionPolicy(AndroidMotionPolicy(context)) }
    DisposableEffect(policy) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) = policy.refresh()
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = policy.refresh()
        }
        context.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer,
        )
        policy.refresh()
        onDispose {
            context.unregisterReceiver(receiver)
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
    return policy
}

/** The ambient orbs hold still when animations are off or battery saver is on. */
fun orbsStatic(policy: MotionPolicy): Boolean = policy.reduceMotion || policy.powerSave

/** No motion in Ghostdeck is longer than this. */
const val MAX_MOTION_MS = 600
