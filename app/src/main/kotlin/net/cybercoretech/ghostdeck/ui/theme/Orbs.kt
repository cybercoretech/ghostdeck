package net.cybercoretech.ghostdeck.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** One full drift cycle. The brief asks for 12 s or more. */
const val ORB_DRIFT_MS = 16_000

/** Two soft radial colour orbs (accent, accentAlt) behind the home content. Still when motion is off. */
@Composable
fun AmbientOrbs(modifier: Modifier = Modifier) {
    val tokens = LocalTokens.current
    val static = orbsStatic(LocalMotionPolicy.current)
    // The State is read only inside the draw lambda, so each frame redraws the canvas without recomposing.
    val phase: State<Float>? = if (static) null else rememberInfiniteTransition(label = "orbs").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(ORB_DRIFT_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "orbPhase",
    )
    Canvas(modifier) {
        val angle = ((phase?.value ?: 0f) * 2 * PI).toFloat()
        val radius = size.maxDimension * 0.6f
        fun orb(color: Color, center: Offset) = drawCircle(
            brush = Brush.radialGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent), center = center, radius = radius),
            radius = radius,
            center = center,
        )
        orb(tokens.accent, Offset(size.width * (0.2f + 0.08f * cos(angle)), size.height * (0.15f + 0.05f * sin(angle))))
        orb(tokens.accentAlt, Offset(size.width * (0.85f - 0.08f * sin(angle)), size.height * (0.8f - 0.05f * cos(angle))))
    }
}
