package net.cybercoretech.ghostdeck.home

import android.app.WallpaperManager
import android.content.Context
import android.os.IBinder
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.cybercoretech.ghostdeck.catalog.CatalogState
import net.cybercoretech.ghostdeck.edit.DragSource
import net.cybercoretech.ghostdeck.edit.DropOutcome
import net.cybercoretech.ghostdeck.edit.placeFirstFree
import net.cybercoretech.ghostdeck.edit.setHidden
import net.cybercoretech.ghostdeck.home.edit.AppPickerSheet
import net.cybercoretech.ghostdeck.home.edit.DragLayer
import net.cybercoretech.ghostdeck.home.edit.EditBar
import net.cybercoretech.ghostdeck.home.edit.EditCallbacks
import net.cybercoretech.ghostdeck.home.edit.EditSession
import net.cybercoretech.ghostdeck.home.edit.ItemMenu
import net.cybercoretech.ghostdeck.home.edit.editGestures
import net.cybercoretech.ghostdeck.model.AppItem
import net.cybercoretech.ghostdeck.model.AppKey
import net.cybercoretech.ghostdeck.model.ItemIds
import net.cybercoretech.ghostdeck.model.allAppKeys
import net.cybercoretech.ghostdeck.model.Layout
import net.cybercoretech.ghostdeck.model.LayoutResult
import net.cybercoretech.ghostdeck.model.Page
import net.cybercoretech.ghostdeck.model.ShortcutItem
import net.cybercoretech.ghostdeck.model.remove
import net.cybercoretech.ghostdeck.model.setSwipeUp
import net.cybercoretech.ghostdeck.platform.AppShortcut
import net.cybercoretech.ghostdeck.model.FolderItem
import net.cybercoretech.ghostdeck.model.Item
import net.cybercoretech.ghostdeck.model.findItem
import net.cybercoretech.ghostdeck.platform.AppLauncher
import net.cybercoretech.ghostdeck.icons.IconProvider
import net.cybercoretech.ghostdeck.theme.Contrast
import net.cybercoretech.ghostdeck.theme.ThemePack
import net.cybercoretech.ghostdeck.ui.theme.AmbientOrbs
import net.cybercoretech.ghostdeck.ui.theme.GhostdeckFonts
import net.cybercoretech.ghostdeck.ui.theme.GhostdeckTokens
import net.cybercoretech.ghostdeck.ui.theme.GhostdeckTheme
import net.cybercoretech.ghostdeck.ui.theme.GlassSurface
import net.cybercoretech.ghostdeck.ui.theme.LocalMotionPolicy
import net.cybercoretech.ghostdeck.ui.theme.LocalTokens

private const val PAGE_SWIPE_DP = 32
private const val EDGE_DP = 40
private const val EDGE_TICK_MS = 100L
private const val EDGE_HOLD_MS = 500L

private data class MenuTarget(val itemId: String)

/**
 * The home screen. It paints no opaque ground: the window shows the wallpaper and the orbs sit translucently over it.
 * Page swipes that no icon consumed go to [onSwipeUp] / [onSwipeDown].
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    pack: ThemePack,
    launcher: AppLauncher,
    icons: IconProvider,
    homePresses: Flow<Unit>,
    onPageChanged: (Int) -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onDismissIssue: () -> Unit = {},
    /** The one trigger for edit mode (M5 replaces the long-press with the radial menu's "Edit home"). */
    onEmptyLongPress: () -> Unit = {},
    onDoneEditing: () -> Unit = {},
    /** Edits are transforms applied to the current layout, never snapshots, so concurrent catalog changes survive. */
    onLayoutChange: ((Layout) -> Layout) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    /** Where page positions are sent for the wallpaper parallax; defaults to the system WallpaperManager. */
    wallpaperOffsets: WallpaperOffsets? = null,
    /** Test hook: called each time a tile composes (recomposition counting). */
    onTileComposed: ((String) -> Unit)? = null,
) {
    GhostdeckTheme(pack) {
        val tokens = LocalTokens.current
        val reduceMotion = LocalMotionPolicy.current.reduceMotion
        val layoutPageCount = state.layout.pages.size.coerceAtLeast(1)
        var openFolderId by remember { mutableStateOf<String?>(null) }
        var menu by remember { mutableStateOf<MenuTarget?>(null) }
        var showPicker by remember { mutableStateOf(false) }
        var rootWidth by remember { mutableIntStateOf(0) }

        // isSystemApp is a binder call: it is answered from a cache that fills off the main thread when editing starts.
        // Until an app is known it counts as not uninstallable (the safe answer).
        val systemApps = remember(launcher) { ConcurrentHashMap<AppKey, Boolean>() }
        var systemAppsVersion by remember(launcher) { mutableIntStateOf(0) }
        val canUninstall = remember(launcher) { { key: AppKey -> systemAppsVersion >= 0 && systemApps[key] == false } }
        val session = remember(canUninstall) { EditSession(state.layout, canUninstall) }
        val layoutAppKeys = remember(state.layout) { state.layout.allAppKeys() }
        LaunchedEffect(launcher, layoutAppKeys, state.editing, session.isDragging) {
            if (!state.editing && !session.isDragging) return@LaunchedEffect
            val missing = layoutAppKeys.filter { !systemApps.containsKey(it) }
            if (missing.isEmpty()) return@LaunchedEffect
            withContext(Dispatchers.Default) { missing.forEach { systemApps[it] = launcher.isSystemApp(it) } }
            systemAppsVersion++
        }
        LaunchedEffect(state.layout, session.isDragging) { session.sync(state.layout) }
        // While dragging, one extra empty page at the end is a drop target for a new page.
        val pageCount = layoutPageCount + if (session.isDragging) 1 else 0
        val pagerState = rememberPagerState(initialPage = state.currentPage.coerceIn(0, pageCount - 1)) { pageCount }

        fun handle(commit: EditSession.Commit) {
            when (val outcome = commit.outcome) {
                is DropOutcome.Updated -> onLayoutChange(commit.transform)
                is DropOutcome.UninstallRequested -> launcher.uninstall(outcome.key)
                is DropOutcome.Rejected, DropOutcome.Cancelled -> Unit
            }
        }
        val callbacks = rememberUpdatedState(
            EditCallbacks(
                enabled = menu == null && !showPicker,
                allowEmptyLongPress = menu == null && !showPicker && openFolderId == null,
                grid = state.layout.grid,
                fineByDefault = state.prefs.fineGrid,
                resolve = { state.layout.findItem(it) },
                onItemLongPress = { menu = MenuTarget(it.id) },
                onDragStart = {
                    menu = null
                    openFolderId = null
                    if (!state.editing) onEmptyLongPress()
                },
                onEmptyLongPress = onEmptyLongPress,
                onDragFinished = ::handle,
            ),
        )

        val edgePx = with(LocalDensity.current) { EDGE_DP.dp.toPx() }
        // Long-lived effects read "Remove animations" live, not as it was when they started.
        val latestReduceMotion by rememberUpdatedState(reduceMotion)
        LaunchedEffect(session.isDragging) {
            if (!session.isDragging) return@LaunchedEffect
            var held = 0L
            var lastSide = 0
            while (true) {
                delay(EDGE_TICK_MS)
                val x = session.pointer.x
                val side = if (x < edgePx) -1 else if (x > rootWidth - edgePx) 1 else 0
                held = if (side != 0 && side == lastSide) held + EDGE_TICK_MS else 0L
                lastSide = side
                if (side != 0 && held >= EDGE_HOLD_MS) {
                    held = 0L
                    val target = (pagerState.currentPage + side).coerceIn(0, pagerState.pageCount - 1)
                    if (target != pagerState.currentPage) {
                        if (latestReduceMotion) pagerState.scrollToPage(target) else pagerState.animateScrollToPage(target)
                    }
                }
            }
        }

        // The effect outlives recompositions, so it reads the latest editing flag, callback and motion policy.
        val latestEditing by rememberUpdatedState(state.editing)
        val latestDoneEditing by rememberUpdatedState(onDoneEditing)
        LaunchedEffect(homePresses) {
            homePresses.collect {
                openFolderId = null
                menu = null
                showPicker = false
                if (latestEditing) latestDoneEditing()
                if (latestReduceMotion) pagerState.scrollToPage(0) else pagerState.animateScrollToPage(0)
            }
        }
        LaunchedEffect(pagerState) { snapshotFlow { pagerState.settledPage }.collect(onPageChanged) }
        // Back closes the topmost layer: item menu, picker, folder sheet, then edit mode.
        BackHandler(enabled = menu != null || showPicker || openFolderId != null || state.editing) {
            when {
                menu != null -> menu = null
                showPicker -> showPicker = false
                openFolderId != null -> openFolderId = null
                else -> onDoneEditing()
            }
        }

        val context = LocalContext.current
        val view = LocalView.current
        val sender = remember(wallpaperOffsets, context) { wallpaperOffsets ?: SystemWallpaperOffsets(context) }
        // The window token only exists once the view is attached: re-send the offset on attach.
        val attached = rememberAttached(view)
        LaunchedEffect(layoutPageCount, attached, sender) {
            if (!attached) return@LaunchedEffect
            snapshotFlow { pagerState.currentPage + pagerState.currentPageOffsetFraction }.collect { position ->
                val token = view.windowToken ?: return@collect
                sender.send(token, position, layoutPageCount)
            }
        }

        // Tiles that have appeared once do not materialize again when their page is revisited.
        val shownTiles = remember { HashSet<String>() }
        // Remembered on its inputs: an unrelated recomposition (page change, edit chrome) must not recompose every tile.
        val draggingId = session.dragItem?.id
        val env = remember(
            state.apps, state.unavailableUsers, state.profiles, state.prefs.swipeMarks, state.prefs.debugGridBounds, state.badges,
            launcher, icons, session, draggingId, shownTiles, onTileComposed,
        ) {
            TileEnv(
                apps = state.apps, unavailableUsers = state.unavailableUsers, profiles = state.profiles, swipeMarks = state.prefs.swipeMarks, gridBounds = state.prefs.debugGridBounds, badges = state.badges,
                launcher = launcher, icons = icons,
                openFolder = { openFolderId = it.id }, showShortcuts = { menu = MenuTarget(it.id) },
                geometry = session.geometry, draggingId = draggingId,
                shownTiles = shownTiles, onTileComposed = onTileComposed,
            )
        }
        val thresholdPx = with(LocalDensity.current) { PAGE_SWIPE_DP.dp.toPx() }
        Box(
            Modifier.fillMaxSize().onSizeChanged { rootWidth = it.width }.editGestures(session, callbacks).pointerInput(onSwipeUp, onSwipeDown) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total <= -thresholdPx) onSwipeUp() else if (total >= thresholdPx) onSwipeDown()
                    },
                ) { change, dy ->
                    total += dy
                    change.consume()
                }
            },
        ) {
            AmbientOrbs(Modifier.fillMaxSize())
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                if (state.editing) EditBar(session, onAddApps = { showPicker = true }, onDone = onDoneEditing, onSettings = onOpenSettings)
                HorizontalPager(pagerState, Modifier.weight(1f).fillMaxWidth()) { page ->
                    val shown = state.layout.pages.getOrNull(page) ?: Page().takeIf { page == layoutPageCount }
                    shown?.let { PageGrid(it, state.layout.grid, env, pageIndex = page) }
                }
                if (pageCount > 1) PageDots(pagerState, pageCount, tokens)
                Dock(state.layout.dock, env)
            }
            state.loadIssue?.let { LoadIssueBanner(it, onDismissIssue, Modifier.align(Alignment.TopCenter)) }
            (openFolderId?.let { state.layout.findItem(it) } as? FolderItem)?.let { folder ->
                // When its profile locks and every child goes hidden, the sheet must not stay up empty.
                if (env.visibleChildren(folder).isEmpty()) LaunchedEffect(folder.id) { openFolderId = null }
                else FolderSheet(folder, env) { openFolderId = null }
            }
            menu?.let { target ->
                val item = state.layout.findItem(target.itemId)
                if (item == null) {
                    LaunchedEffect(target) { menu = null }
                } else {
                    ItemMenuHost(
                        item = item, state = state, launcher = launcher, session = session,
                        dragging = session.isDragging, fineByDefault = state.prefs.fineGrid,
                        onLayoutChange = onLayoutChange, onOutcome = ::handle, onClose = { menu = null },
                        onStartEditing = { if (!state.editing) onEmptyLongPress() },
                    )
                }
            }
            if (showPicker) {
                AppPickerSheet(
                    apps = remember(state.apps, state.unavailableUsers) { CatalogState(state.apps, state.unavailableUsers).visibleApps() },
                    hidden = state.layout.hidden,
                    icons = icons,
                    onPick = { entry ->
                        val item = AppItem(ItemIds.new(), entry.key)
                        onLayoutChange { it.placeFirstFree(item) }
                        showPicker = false
                    },
                    // Decided inside the transform, on the layout that is current when it runs.
                    onToggleHidden = { key -> onLayoutChange { l -> l.setHidden(key, key !in l.hidden) } },
                    onDismiss = { showPicker = false },
                )
            }
            DragLayer(session, env, state.layout.grid)
        }
    }
}

/** Shown when the layout file was unreadable (or could not be saved). Stays until dismissed. */
@Composable
private fun LoadIssueBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalTokens.current
    GlassSurface(
        modifier.windowInsetsPadding(WindowInsets.statusBars).padding(12.dp).fillMaxWidth().testTag("load-issue-banner"),
        shape = RoundedCornerShape(16.dp),
        alpha = Contrast.sheetAlpha(tokens.glassAlpha, blurAvailable = false),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, Modifier.weight(1f), color = tokens.text, fontFamily = GhostdeckFonts.ui, fontSize = 13.sp)
            TextButton(onDismiss, Modifier.testTag("load-issue-dismiss")) {
                Text("Dismiss", color = tokens.accent, fontFamily = GhostdeckFonts.ui)
            }
        }
    }
}

/** Looks up the item's shortcuts off the main thread and wires the menu's actions to the layout and launcher. */
@Composable
private fun ItemMenuHost(
    item: Item,
    state: HomeUiState,
    launcher: AppLauncher,
    session: EditSession,
    dragging: Boolean,
    fineByDefault: Boolean,
    onLayoutChange: ((Layout) -> Layout) -> Unit,
    onOutcome: (EditSession.Commit) -> Unit,
    onClose: () -> Unit,
    onStartEditing: () -> Unit,
) {
    val layout = state.layout
    val ownerKey = when (item) {
        is AppItem -> item.app
        is ShortcutItem -> item.ownerKey(state.apps)
        is FolderItem -> null
    }
    val title = when (item) {
        is AppItem -> item.customLabel ?: state.apps[item.app]?.label ?: item.app.packageName.substringAfterLast('.')
        is ShortcutItem -> item.label
        is FolderItem -> item.name
    }
    val shortcuts by produceState(emptyList<AppShortcut>(), item.id) {
        value = ownerKey?.let { withContext(Dispatchers.Default) { launcher.shortcuts(it) } }.orEmpty()
    }
    val apps = remember(state.apps, state.unavailableUsers, layout.hidden) {
        CatalogState(state.apps, state.unavailableUsers).visibleApps().filter { it.key !in layout.hidden }
    }
    ItemMenu(
        item = item, title = title, shortcuts = shortcuts, apps = apps, dragging = dragging, startInSwipePicker = false,
        onLaunchShortcut = { s ->
            launcher.launchShortcut(ShortcutItem(ItemIds.new(), s.packageName, s.id, s.userSerial, s.label), null)
            onClose()
        },
        onShortcutDragStart = { s, at ->
            val dragged = ShortcutItem(ItemIds.new(), s.packageName, s.id, s.userSerial, s.label)
            onStartEditing()
            session.start(DragSource.New(dragged), dragged, at)
            session.move(at, fineByDefault, layout.grid)
        },
        onShortcutDrag = { session.move(it, fineByDefault, layout.grid) },
        onShortcutDragEnd = {
            if (session.isDragging) onOutcome(session.finish())
            onClose()
        },
        onAppInfo = { if (item is AppItem) launcher.appInfo(item.app); onClose() },
        onRemove = { onLayoutChange { it.remove(item.id) }; onClose() },
        onHide = { if (item is AppItem) onLayoutChange { it.setHidden(item.app, true) }; onClose() },
        onSwipeAction = { action -> onLayoutChange { (it.setSwipeUp(item.id, action) as? LayoutResult.Ok)?.layout ?: it } },
        onDismiss = onClose,
    )
}

/** The page indicator. Reads the pager position here, so a page change recomposes only the dots. */
@Composable
private fun PageDots(pagerState: PagerState, count: Int, tokens: GhostdeckTokens) {
    val current = pagerState.currentPage
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
        repeat(count) { i ->
            val color = if (i == current) tokens.text else tokens.muted.copy(alpha = 0.5f)
            // A dark ring keeps the dots visible on light wallpapers.
            Box(
                Modifier.padding(2.dp).size(8.dp).background(HomeShadowColor.copy(alpha = 0.35f), CircleShape)
                    .padding(1.dp).background(color, CircleShape),
            )
        }
    }
}

/** Wallpaper offset (0..1) for a pager [position] over [pageCount] pages. */
internal fun wallpaperOffset(position: Float, pageCount: Int): Float =
    if (pageCount <= 1) 0f else (position / (pageCount - 1)).coerceIn(0f, 1f)

/** Receives the wallpaper parallax position; tests substitute a fake. */
fun interface WallpaperOffsets {
    fun send(token: IBinder, position: Float, pageCount: Int)
}

private class SystemWallpaperOffsets(private val context: Context) : WallpaperOffsets {
    override fun send(token: IBinder, position: Float, pageCount: Int) {
        runCatching {
            val wallpaper = WallpaperManager.getInstance(context)
            if (pageCount > 1) wallpaper.setWallpaperOffsetSteps(1f / (pageCount - 1), 0f)
            wallpaper.setWallpaperOffsets(token, wallpaperOffset(position, pageCount), 0f)
        }
    }
}

/** Whether [view] is attached to a window (and so has a window token). */
@Composable
private fun rememberAttached(view: View): Boolean {
    var attached by remember(view) { mutableStateOf(view.isAttachedToWindow) }
    DisposableEffect(view) {
        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { attached = true }
            override fun onViewDetachedFromWindow(v: View) { attached = false }
        }
        view.addOnAttachStateChangeListener(listener)
        attached = view.isAttachedToWindow
        onDispose { view.removeOnAttachStateChangeListener(listener) }
    }
    return attached
}
