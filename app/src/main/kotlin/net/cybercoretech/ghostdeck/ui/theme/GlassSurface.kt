package net.cybercoretech.ghostdeck.ui.theme

import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Phantom Glass: a translucent tinted surface with a 1 dp top highlight. It does not blur what is behind
 * it, because the wallpaper's pixels can't be read (spec 2b.4). [alpha] overrides the pack's glass opacity,
 * e.g. `Contrast.sheetAlpha(...)` for sheets.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    alpha: Float? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val t = LocalTokens.current
    val highlight = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.03f)))
    Box(
        modifier
            .clip(shape)
            .background(t.glass.copy(alpha = alpha ?: t.glassAlpha), shape)
            .border(1.dp, highlight, shape),
        content = content,
    )
}

/** True on API 31+ when the system has cross-window blur enabled. */
fun blurAvailable(window: Window): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val wm = window.context.getSystemService(WindowManager::class.java) ?: return false
    return wm.isCrossWindowBlurEnabled
}

/** Cross-window blur for full-screen sheets when [blurAvailable]; otherwise a no-op (use `Contrast.sheetAlpha`). */
fun applySheetBlur(window: Window, radiusPx: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurAvailable(window)) window.setBackgroundBlurRadius(radiusPx)
}
