package net.cybercoretech.ghostdeck.home

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import android.os.Build
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.cybercoretech.ghostdeck.model.AppKey
import net.cybercoretech.ghostdeck.model.FolderItem
import net.cybercoretech.ghostdeck.model.ShortcutItem
import net.cybercoretech.ghostdeck.platform.AppLauncher
import net.cybercoretech.ghostdeck.theme.Contrast
import net.cybercoretech.ghostdeck.ui.theme.GhostdeckFonts
import net.cybercoretech.ghostdeck.ui.theme.GlassSurface
import net.cybercoretech.ghostdeck.ui.theme.LocalTokens
import net.cybercoretech.ghostdeck.ui.theme.applySheetBlur
import net.cybercoretech.ghostdeck.ui.theme.blurAvailable

private const val COLUMNS = 4
private const val SHEET_BLUR_PX = 48
private val CHILD_ICON = 48.dp

private fun Context.findWindow(): Window? = when (this) {
    is ComponentActivity -> window
    is ContextWrapper -> baseContext.findWindow()
    else -> null
}

/** Launching a child closes the sheet. */
private class DismissingLauncher(private val inner: AppLauncher, private val dismiss: () -> Unit) : AppLauncher by inner {
    override fun launch(key: AppKey, bounds: Rect?) { inner.launch(key, bounds); dismiss() }
    override fun launchShortcut(item: ShortcutItem, bounds: Rect?) { inner.launchShortcut(item, bounds); dismiss() }
}

/** Full-screen scrim with a glass panel of the folder's children. Blurs the wallpaper where the system allows it. */
@Composable
fun FolderSheet(folder: FolderItem, env: TileEnv, onDismiss: () -> Unit) {
    val tokens = LocalTokens.current
    val window = LocalContext.current.findWindow()
    DisposableEffect(window) {
        if (window != null) applySheetBlur(window, SHEET_BLUR_PX)
        onDispose { if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) window.setBackgroundBlurRadius(0) }
    }
    val alpha = Contrast.sheetAlpha(tokens.glassAlpha, window != null && blurAvailable(window))
    val sheetEnv = remember(env, onDismiss) {
        TileEnv(env.apps, env.unavailableUsers, env.swipeMarks, DismissingLauncher(env.launcher, onDismiss), env.icons, env.openFolder, env.showShortcuts)
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            Modifier.padding(24.dp).fillMaxWidth().testTag("folder-sheet")
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            alpha = alpha,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    folder.name, color = tokens.text, fontFamily = GhostdeckFonts.display,
                    fontWeight = FontWeight.SemiBold, fontSize = 20.sp,
                )
                folder.children.chunked(COLUMNS).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEachIndexed { i, child ->
                            ItemTile(child, sheetEnv, CHILD_ICON, showLabel = true, index = i, modifier = Modifier.weight(1f).height(84.dp))
                        }
                        repeat(COLUMNS - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
