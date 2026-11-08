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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
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
private val BADGE_DOT_SIZE = 8.dp
private val BADGE_DOT_TOP = 4.dp
private val BADGE_DOT_END = 8.dp

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
    /** Ids of tiles that have already materialized once; a revisited page shows them at once. */
    val shownTiles: MutableSet<String> = HashSet(),
    /** Test hook: told each time a tile composes. */
    val onTileComposed: ((String) -> Unit)? = null,
)

/** Colour of the soft dark halo behind light home text and dots, so they read on any wallpaper (as Launcher3 does). */
val HomeShadowColor = Color.Black.copy(alpha = 0.6f)

/**
 * Home label text. The shadow (1 dp down, 4 dp blur; [density] converts to the px Shadow wants) keeps the
 * theme's light label legible on light wallpapers too.
 */
fun homeLabelStyle(color: Color, density: Float = 1f): TextStyle = TextStyle(
    color = color, fontFamily = GhostdeckFonts.ui, fontSize = 11.sp,
    shadow = Shadow(HomeShadowColor, offset = Offset(0f, 1f * density), blurRadius = 4f * density),
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

/** The launcher activity a shortcut belongs to (for its icon and shortcut list), or a class-less key if unknown. */
fun ShortcutItem.ownerKey(apps: Map<AppKey, AppEntry>): AppKey =
    apps.keys.firstOrNull { it.packageName == packageName && it.userSerial == userSerial } ?: AppKey(packageName, "", userSerial)

/** The children of [folder] that may be drawn right now. */
fun TileEnv.visibleChildren(folder: FolderItem): List<Item> = folder.children.filter { stateOf(it) != TileState.Hidden }

/** One home, dock or folder item: icon, label, tap, swipe-up action. [index] staggers the first appearance. */
@Composable
fun ItemTile(item: Item, env: TileEnv, iconSize: Dp, showLabel: Boolean, index: Int, modifier: Modifier = Modifier) {
    env.onTileComposed?.let { hook -> SideEffect { hook(item.id) } }
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
                announce = env.profiles[item.app.userSerial]?.spoken(), alreadyShown = item.id in env.shownTiles, onShown = { env.shownTiles += item.id },
                swipeUp = item.swipeUp, showSwipeMark = env.swipeMarks,
                badge = env.badges.hasCount(item.app.packageName, item.app.userSerial),
                onTap = { env.launcher.launch(item.app, it) },
                onSwipeUp = { runAction(item.swipeUp, item, env, it) },
                icon = { AppImage(item.app, iconSize, env.icons) },
            )
        }
        is ShortcutItem -> {
            val owner = item.ownerKey(env.apps)
            AppIcon(
                id = item.id, label = item.label, available = env.stateOf(item) == TileState.Normal,
                showLabel = showLabel, index = index, modifier = modifier,
                announce = env.profiles[item.userSerial]?.spoken(), alreadyShown = item.id in env.shownTiles, onShown = { env.shownTiles += item.id },
                swipeUp = item.swipeUp, showSwipeMark = env.swipeMarks,
                badge = env.badges.hasCount(item.packageName, item.userSerial),
                onTap = { env.launcher.launchShortcut(item, it) },
                onSwipeUp = { runAction(item.swipeUp, item, env, it) },
                icon = { AppImage(owner, iconSize, env.icons) },
            )
        }
        is FolderItem -> AppIcon(
            id = item.id, label = item.name, available = true, showLabel = showLabel, index = index, modifier = modifier,
            announce = env.visibleChildren(item).size.let { n -> "folder, $n ${if (n == 1) "item" else "items"}" },
            alreadyShown = item.id in env.shownTiles, onShown = { env.shownTiles += item.id },
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

/** What a screen reader adds after the label for an app of this profile. Personal apps add nothing. */
private fun ProfileKind.spoken(): String? = when (this) {
    ProfileKind.WORK -> "work"
    ProfileKind.PRIVATE -> "private"
    else -> null
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
    /** Spoken after the label, e.g. "work" or "folder, 3 items". */
    announce: String? = null,
    /** True when this tile has already materialized once (its page is being revisited). */
    alreadyShown: Boolean = false,
    onShown: () -> Unit = {},
    icon: @Composable () -> Unit,
) {
    val tokens = LocalTokens.current
    val reduceMotion = LocalMotionPolicy.current.reduceMotion
    var shown by remember { mutableStateOf(reduceMotion || alreadyShown) }
    LaunchedEffect(Unit) {
        if (!shown) delay((index * STAGGER_MS).coerceAtMost(STAGGER_CAP_MS))
        shown = true
        onShown()
    }
    // The coordinates are kept as a plain reference and turned into a Rect only when a tap or swipe needs it:
    // no allocation and no state write per tile per scroll frame.
    val coords = remember { CoordsHolder() }
    val thresholdPx = with(LocalDensity.current) { SWIPE_UP_DP.dp.toPx() }
    val latestSwipeUp by rememberUpdatedState(onSwipeUp)
    val swipeModifier = if (swipeUp != null && available) {
        Modifier.pointerInput(thresholdPx) {
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
                if (total <= -thresholdPx) latestSwipeUp(coords.windowRect())
            }
        }
    } else Modifier
    Box(
        modifier
            .testTag("item-$id")
            .materialize(visible = shown, reduceMotion = reduceMotion || alreadyShown)
            .alpha(if (available) 1f else UNAVAILABLE_ALPHA)
            .onGloballyPositioned { coords.layout = it }
            // TalkBack hears the label, then the profile or folder note (as the node's state), and
            // "has notifications" when the dot is showing.
            .then(
                if (showLabel && !badge && announce == null) Modifier
                else Modifier.semantics {
                    if (badge || !showLabel) contentDescription = if (badge) "$label, has notifications" else label
                    if (announce != null) stateDescription = announce
                },
            )
            .clickable(enabled = available) { onTap(coords.windowRect()) }
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
                    style = homeLabelStyle(tokens.text, LocalDensity.current.density),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
        // A dot, never a number: counts would be one more thing competing for attention.
        if (badge) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = BADGE_DOT_TOP, end = BADGE_DOT_END)
                    .testTag("badge-$id").size(BADGE_DOT_SIZE).background(tokens.accent, CircleShape),
            )
        }
        // Overlaid at the bottom edge so it never competes with the label for height.
        if (swipeUp != null && showSwipeMark) {
            Box(Modifier.align(Alignment.BottomCenter).testTag("swipe-mark-$id").size(4.dp).background(tokens.accent, CircleShape))
        }
    }
}

private class CoordsHolder {
    var layout: LayoutCoordinates? = null

    fun windowRect(): android.graphics.Rect? =
        layout?.takeIf { it.isAttached }?.boundsInWindow()?.let {
            android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt())
        }
}

@Composable
fun AppImage(key: AppKey, size: Dp, icons: IconProvider) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    // A new generation (package updated, profile unlocked) re-requests; the old bitmap stays until the new one lands.
    val generation by icons.generation.collectAsState()
    // Monochrome fallback icons are tinted with the accent, so an accent change must request them again.
    val accent = LocalTokens.current.accent
    val bitmap by produceState<ImageBitmap?>(null, key, px, generation, accent) {
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
                            is ShortcutItem -> child.ownerKey(env.apps)
                            is FolderItem -> null
                        }
                        if (key != null) AppImage(key, mini, env.icons) else Spacer(Modifier.size(mini))
                    }
                }
            }
        }
    }
}
