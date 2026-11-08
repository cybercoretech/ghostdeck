package net.cybercoretech.ghostdeck.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.cybercoretech.ghostdeck.catalog.AppEntry
import net.cybercoretech.ghostdeck.icons.IconProvider
import net.cybercoretech.ghostdeck.model.AppItem
import net.cybercoretech.ghostdeck.model.AppKey
import net.cybercoretech.ghostdeck.model.FolderItem
import net.cybercoretech.ghostdeck.model.Item
import net.cybercoretech.ghostdeck.model.ItemAction
import net.cybercoretech.ghostdeck.model.LaunchApp
import net.cybercoretech.ghostdeck.model.LaunchShortcut
import net.cybercoretech.ghostdeck.model.ShortcutItem
import net.cybercoretech.ghostdeck.model.ShowShortcuts
import net.cybercoretech.ghostdeck.platform.AppLauncher
import net.cybercoretech.ghostdeck.ui.theme.GhostdeckFonts
import net.cybercoretech.ghostdeck.ui.theme.LocalMotionPolicy
import net.cybercoretech.ghostdeck.ui.theme.LocalTokens
import net.cybercoretech.ghostdeck.ui.theme.materialize

private const val SWIPE_UP_DP = 24
private const val STAGGER_MS = 20L
private const val STAGGER_CAP_MS = 200L
private const val UNAVAILABLE_ALPHA = 0.4f

/** What every tile needs from the home screen. */
class TileEnv(
    val apps: Map<AppKey, AppEntry>,
    val unavailableUsers: Set<Long>,
    val swipeMarks: Boolean,
    val launcher: AppLauncher,
    val icons: IconProvider,
    val openFolder: (FolderItem) -> Unit,
    val showShortcuts: (Item) -> Unit,
)

/** One home, dock or folder item: icon, label, tap, swipe-up action. [index] staggers the first appearance. */
@Composable
fun ItemTile(item: Item, env: TileEnv, iconSize: Dp, showLabel: Boolean, index: Int, modifier: Modifier = Modifier) {
    when (item) {
        is AppItem -> {
            val entry = env.apps[item.app]
            val available = item.app.userSerial !in env.unavailableUsers && entry != null
            val label = item.customLabel ?: entry?.label ?: item.app.packageName.substringAfterLast('.')
            AppIcon(
                id = item.id, label = label, available = available, showLabel = showLabel, index = index, modifier = modifier,
                swipeUp = item.swipeUp, showSwipeMark = env.swipeMarks,
                onTap = { env.launcher.launch(item.app, it) },
                onSwipeUp = { runAction(item.swipeUp, item, env, it) },
                icon = { AppImage(item.app, iconSize, env.icons) },
            )
        }
        is ShortcutItem -> {
            val owner = env.apps.keys.firstOrNull { it.packageName == item.packageName && it.userSerial == item.userSerial }
                ?: AppKey(item.packageName, "", item.userSerial)
            AppIcon(
                id = item.id, label = item.label, available = item.userSerial !in env.unavailableUsers,
                showLabel = showLabel, index = index, modifier = modifier,
                swipeUp = item.swipeUp, showSwipeMark = env.swipeMarks,
                onTap = { env.launcher.launchShortcut(item, it) },
                onSwipeUp = { runAction(item.swipeUp, item, env, it) },
                icon = { AppImage(owner, iconSize, env.icons) },
            )
        }
        is FolderItem -> AppIcon(
            id = item.id, label = item.name, available = true, showLabel = showLabel, index = index, modifier = modifier,
            swipeUp = null, showSwipeMark = false,
            onTap = { env.openFolder(item) }, onSwipeUp = {},
            icon = { FolderGlyph(item, iconSize, env) },
        )
    }
}

private fun runAction(action: ItemAction?, item: Item, env: TileEnv, bounds: android.graphics.Rect?) {
    when (action) {
        is LaunchApp -> env.launcher.launch(action.app, bounds)
        is LaunchShortcut -> env.launcher.launchShortcut(
            ShortcutItem(action.shortcutId, action.packageName, action.shortcutId, action.userSerial, label = ""), bounds,
        )
        ShowShortcuts -> env.showShortcuts(item)
        null -> Unit
    }
}

@Composable
fun AppIcon(
    id: String,
    label: String,
    available: Boolean,
    showLabel: Boolean,
    index: Int,
    swipeUp: ItemAction?,
    showSwipeMark: Boolean,
    onTap: (android.graphics.Rect?) -> Unit,
    onSwipeUp: (android.graphics.Rect?) -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    val tokens = LocalTokens.current
    val reduceMotion = LocalMotionPolicy.current.reduceMotion
    var shown by remember { mutableStateOf(reduceMotion) }
    LaunchedEffect(Unit) {
        if (!shown) delay((index * STAGGER_MS).coerceAtMost(STAGGER_CAP_MS))
        shown = true
    }
    var bounds by remember { mutableStateOf<android.graphics.Rect?>(null) }
    val thresholdPx = with(LocalDensity.current) { SWIPE_UP_DP.dp.toPx() }
    val swipeModifier = if (swipeUp != null && available) {
        Modifier.pointerInput(swipeUp) {
            var total = 0f
            detectVerticalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = { if (total <= -thresholdPx) onSwipeUp(bounds) },
            ) { change, dy ->
                total += dy
                change.consume()
            }
        }
    } else Modifier
    Box(
        modifier
            .testTag("item-$id")
            .materialize(visible = shown, reduceMotion = reduceMotion)
            .alpha(if (available) 1f else UNAVAILABLE_ALPHA)
            .onGloballyPositioned { c -> c.boundsInWindow().let { bounds = android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) } }
            .then(if (showLabel) Modifier else Modifier.semantics { contentDescription = label })
            .clickable(enabled = available) { onTap(bounds) }
            .then(swipeModifier),
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            icon()
            if (showLabel) {
                Text(
                    label,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
                    color = tokens.text,
                    fontFamily = GhostdeckFonts.ui,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
        // Overlaid at the bottom edge so it never competes with the label for height.
        if (swipeUp != null && showSwipeMark) {
            Box(Modifier.align(Alignment.BottomCenter).testTag("swipe-mark-$id").size(4.dp).background(tokens.accent, CircleShape))
        }
    }
}

@Composable
fun AppImage(key: AppKey, size: Dp, icons: IconProvider) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, key, px) {
        value = withContext(Dispatchers.Default) { icons.icon(key, px) }
    }
    Box(Modifier.size(size)) {
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.size(size)) }
    }
}

@Composable
private fun FolderGlyph(folder: FolderItem, size: Dp, env: TileEnv) {
    val tokens = LocalTokens.current
    val mini = size * 0.36f
    Box(
        Modifier.size(size).background(tokens.glass.copy(alpha = tokens.glassAlpha), RoundedCornerShape(size * 0.28f)).padding(size * 0.1f),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(size * 0.04f)) {
            folder.children.take(4).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(size * 0.04f)) {
                    row.forEach { child ->
                        val key = when (child) {
                            is AppItem -> child.app
                            is ShortcutItem -> AppKey(child.packageName, "", child.userSerial)
                            is FolderItem -> null
                        }
                        if (key != null) AppImage(key, mini, env.icons) else Spacer(Modifier.size(mini))
                    }
                }
            }
        }
    }
}
