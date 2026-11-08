package net.cybercoretech.ghostdeck.ui.theme

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

const val MATERIALIZE_MS = 260
private const val START_SCALE = 0.86f
private const val START_BLUR_DP = 10f

/** 0 (hidden) to 1 (settled). With [reduceMotion] it is at its target on the very first frame. */
@Composable
fun materializeProgress(visible: Boolean, reduceMotion: Boolean): State<Float> {
    val target = if (visible) 1f else 0f
    val anim = remember { Animatable(if (reduceMotion) target else 0f) }
    LaunchedEffect(visible, reduceMotion) {
        if (reduceMotion) anim.snapTo(target)
        else anim.animateTo(target, tween(MATERIALIZE_MS, easing = FastOutSlowInEasing))
    }
    return anim.asState()
}

/** Fade in from blur and 0.86 scale over 260 ms (blur on API 31+). Instant when [reduceMotion]. */
fun Modifier.materialize(visible: Boolean, reduceMotion: Boolean): Modifier = composed {
    val progress = materializeProgress(visible, reduceMotion)
    val blurPx = with(LocalDensity.current) { START_BLUR_DP.dp.toPx() }
    graphicsLayer {
        val p = progress.value
        val s = START_SCALE + (1f - START_SCALE) * p
        scaleX = s
        scaleY = s
        alpha = p
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && p < 1f) {
            val r = blurPx * (1f - p)
            renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Decal) else null
        } else {
            renderEffect = null
        }
    }
}
