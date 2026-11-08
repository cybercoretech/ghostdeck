package net.cybercoretech.ghostdeck.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import net.cybercoretech.ghostdeck.badges.BadgeCounts
import net.cybercoretech.ghostdeck.catalog.AppEntry
import net.cybercoretech.ghostdeck.catalog.ProfileKind
import net.cybercoretech.ghostdeck.home.edit.DropGeometry
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
private const val DRAGGING_ALPHA = 0.25f

/** What every tile needs from the home screen. */
data class TileEnv(
    val apps: Map<AppKey, AppEntry>,
    val unavailableUsers: Set<Long>,
    /** Profile kind per user, from catalog snapshots. A missing user has not been read yet. */
    val profiles: Map<Long, ProfileKind> = emptyMap(),
    val swipeMarks: Boolean,
    /** Labs: outline every grid cell. */
    val gridBounds: Boolean = false,
    /** Notification counts keyed "package/userSerial"; empty unless the user opted in. */
    val badges: Map<String, Int>,
    val launcher: AppLauncher,
    val icons: IconProvider,
    val openFolder: (FolderItem) -> Unit,
    val showShortcuts: (Item) -> Unit,
    /** Edit mode: where tiles are, for long-press hit tests and drops. Null for the drag ghost. */
    val geometry: DropGeometry? = null,
    /** Tiles of a folder sheet sit above the home screen. */
    val layer: Int = 0,
    /** The item being dragged is drawn faint where it came from. */
    val draggingId: String? = null,
)

/** How a layout item is drawn, given what the catalog knows about its profile. */
enum class TileState { Normal, Dimmed, Hidden }

/**
 * - A locked Private Space hides its items completely (they stay in the layout, their cells stay taken).
 * - A paused work (or other) profile dims its items and they don't launch.
 * - A user no snapshot has arrived for yet (cold start) is assumed available: drawn normally.
 * - A known, available profile without the app (uninstalled, storage unmounted) dims it.
 */
fun tileState(userSerial: Long, hasEntry: Boolean, unavailableUsers: Set<Long>, profiles: Map<Long, ProfileKind>): TileState = when {
    userSerial in unavailableUsers -> if (profiles[userSerial] == ProfileKind.PRIVATE) TileState.Hidden else TileState.Dimmed
    userSerial !in profiles || hasEntry -> TileState.Normal
    else -> TileState.Dimmed
}

fun TileEnv.stateOf(item: Item): TileState = when (item) {
    is AppItem -> tileState(item.app.userSerial, item.app in apps, unavailableUsers, profiles)
    is ShortcutItem -> tileState(item.userSerial, hasEntry = true, unavailableUsers, profiles)
    // A folder whose every child is hidden is hidden too, so its name gives nothing away.
    is FolderItem -> if (item.children.isNotEmpty() && item.children.all { stateOf(it) == TileState.Hidden }) TileState.Hidden else TileState.Normal
}

/** The children of [folder] that may be drawn right now. */
fun TileEnv.visibleChildren(folder: FolderItem): List<Item> = folder.children.filter { stateOf(it) != TileState.Hidden }

/** One home, dock or folder item: icon, label, tap, swipe-up action. [index] staggers the first appearance. */
@Composable
fun ItemTile(item: Item, env: TileEnv, iconSize: Dp, showLabel: Boolean, index: Int, modifier: Modifier = Modifier) {
    // Hidden items keep their cell (nothing is placed over them) but draw nothing and take no input or semantics.
    if (env.stateOf(item) == TileState.Hidden) {
        Box(modifier)
        return
    }
    val geometry = env.geometry
    if (geometry != null) DisposableEffect(item.id) { onDispose { geometry.items.remove(item.id) } }
    val tracked = modifier
        .then(if (env.draggingId == item.id) Modifier.alpha(DRAGGING_ALPHA) else Modifier)
        .then(
            if (geometry == null) Modifier
            else Modifier.onGloballyPositioned { geometry.items[item.id] = DropGeometry.Entry(it.boundsInRoot(), env.layer) },
        )
    ItemTileContent(item, env, iconSize, showLabel, index, tracked)
}

@Composable
private fun ItemTileContent(item: Item, env: TileEnv, iconSize: Dp, showLabel: Boolean, index: Int, modifier: Modifier) {
    when (item) {
        is AppItem -> {
            val entry = env.apps[item.app]
            val available = env.stateOf(item) == TileState.Normal
            val label = item.customLabel ?: entry?.label ?: item.app.packageName.substringAfterLast('.')
            AppIcon(
                id = item.id, label = label, available = available, showLabel = showLabel, index = index, modifier = modifier,
                swipeUp = item.swipeUp, showSwipeMark = env.swipeMarks,
                badge = env.badges.hasCount(item.app.packageName, item.app.userSerial),
                onTap = { env.launcher.launch(item.app, it) },
                onSwipeUp = { runAction(item.swipeUp, item, env, it) },
                icon = { AppImage(item.app, iconSize, env.icons) },
            )
        }
        is ShortcutItem -> {
            val owner = env.apps.keys.firstOrNull { it.packageName == item.packageName && it.userSerial == item.userSerial }
                ?: AppKey(item.packageName, "", item.userSerial)
            AppIcon(
                id = item.id, label = item.label, available = env.stateOf(item) == TileState.Normal,
                showLabel = showLabel, index = index, modifier = modifier,
                swipeUp = item.swipeUp, showSwipeMark = env.swipeMarks,
                badge = env.badges.hasCount(item.packageName, item.userSerial),
                onTap = { env.launcher.launchShortcut(item, it) },
                onSwipeUp = { runAction(item.swipeUp, item, env, it) },
                icon = { AppImage(owner, iconSize, env.icons) },
            )
        }
        is FolderItem -> AppIcon(
            id = item.id, label = item.name, available = true, showLabel = showLabel, index = index, modifier = modifier,
            swipeUp = null, showSwipeMark = false,
            badge = env.visibleChildren(item).any {
                when (it) {
                    is AppItem -> env.badges.hasCount(it.app.packageName, it.app.userSerial)
                    is ShortcutItem -> env.badges.hasCount(it.packageName, it.userSerial)
                    is FolderItem -> false
                }
            },
            onTap = { env.openFolder(item) }, onSwipeUp = {},
            icon = { FolderGlyph(item, iconSize, env) },
        )
    }
}

private fun Map<String, Int>.hasCount(packageName: String, userSerial: Long) =
    (this[BadgeCounts.key(packageName, userSerial)] ?: 0) > 0

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
    badge: Boolean,
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
            // Claims the gesture only once it has moved up by the touch slop; a downward or sideways
            // drag is left alone so the page (shade, pager) gets it.
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var total = 0f
                var claimed = false
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Main).changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed || (!claimed && change.isConsumed)) break
                    total += change.positionChange().y
                    if (!claimed && total >= viewConfiguration.touchSlop) break
                    if (!claimed && total <= -viewConfiguration.touchSlop) claimed = true
                    if (claimed) change.consume()
                }
                if (total <= -thresholdPx) onSwipeUp(bounds)
            }
        }
    } else Modifier
    Box(
        modifier
            .testTag("item-$id")
            .materialize(visible = shown, reduceMotion = reduceMotion)
            .alpha(if (available) 1f else UNAVAILABLE_ALPHA)
            .onGloballyPositioned { c -> c.boundsInWindow().let { bounds = android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) } }
            // TalkBack hears the label, plus "has notifications" when the dot is showing.
            .then(
                if (showLabel && !badge) Modifier
                else Modifier.semantics { contentDescription = if (badge) "$label, has notifications" else label },
            )
            .clickable(enabled = available) { onTap(bounds) }
            .then(swipeModifier),
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            icon()
            if (showLabel) {
                Text(
                    label,
                    // The tile's own description already carries the label when a badge is showing.
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp)
                        .then(if (badge) Modifier.clearAndSetSemantics {} else Modifier),
                    color = tokens.text,
                    fontFamily = GhostdeckFonts.ui,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
        // A dot, never a number: counts would be one more thing competing for attention.
        if (badge) {
            Box(Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 8.dp).testTag("badge-$id").size(8.dp).background(tokens.accent, CircleShape))
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
    // A new generation (package updated, profile unlocked) re-requests; the old bitmap stays until the new one lands.
    val generation by icons.generation.collectAsState()
    val bitmap by produceState<ImageBitmap?>(null, key, px, generation) {
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
            env.visibleChildren(folder).take(4).chunked(2).forEach { row ->
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
