package net.cybercoretech.ghostdeck.home

import android.app.WallpaperManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import net.cybercoretech.ghostdeck.model.FolderItem
import net.cybercoretech.ghostdeck.model.Item
import net.cybercoretech.ghostdeck.model.findItem
import net.cybercoretech.ghostdeck.platform.AppLauncher
import net.cybercoretech.ghostdeck.icons.IconProvider
import net.cybercoretech.ghostdeck.theme.ThemePack
import net.cybercoretech.ghostdeck.ui.theme.AmbientOrbs
import net.cybercoretech.ghostdeck.ui.theme.GhostdeckTheme
import net.cybercoretech.ghostdeck.ui.theme.LocalMotionPolicy
import net.cybercoretech.ghostdeck.ui.theme.LocalTokens

private const val PAGE_SWIPE_DP = 32

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
    onShowShortcuts: (Item) -> Unit = {},
) {
    GhostdeckTheme(pack) {
        val tokens = LocalTokens.current
        val reduceMotion = LocalMotionPolicy.current.reduceMotion
        val pageCount = state.layout.pages.size.coerceAtLeast(1)
        val pagerState = rememberPagerState(initialPage = state.currentPage.coerceIn(0, pageCount - 1)) { pageCount }
        var openFolderId by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(homePresses) {
            homePresses.collect {
                openFolderId = null
                if (reduceMotion) pagerState.scrollToPage(0) else pagerState.animateScrollToPage(0)
            }
        }
        LaunchedEffect(pagerState) { snapshotFlow { pagerState.settledPage }.collect(onPageChanged) }

        val context = LocalContext.current
        val view = LocalView.current
        LaunchedEffect(pageCount) {
            snapshotFlow { pagerState.currentPage + pagerState.currentPageOffsetFraction }.collect { position ->
                runCatching {
                    val token = view.windowToken ?: return@runCatching
                    val wallpaper = WallpaperManager.getInstance(context)
                    if (pageCount > 1) {
                        wallpaper.setWallpaperOffsetSteps(1f / (pageCount - 1), 0f)
                        wallpaper.setWallpaperOffsets(token, (position / (pageCount - 1)).coerceIn(0f, 1f), 0f)
                    } else wallpaper.setWallpaperOffsets(token, 0f, 0f)
                }
            }
        }

        val env = TileEnv(
            apps = state.apps, unavailableUsers = state.unavailableUsers, swipeMarks = state.prefs.swipeMarks,
            launcher = launcher, icons = icons,
            openFolder = { openFolderId = it.id }, showShortcuts = onShowShortcuts,
        )
        val thresholdPx = with(LocalDensity.current) { PAGE_SWIPE_DP.dp.toPx() }
        Box(
            Modifier.fillMaxSize().pointerInput(onSwipeUp, onSwipeDown) {
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
                HorizontalPager(pagerState, Modifier.weight(1f).fillMaxWidth()) { page ->
                    state.layout.pages.getOrNull(page)?.let { PageGrid(it, state.layout.grid, env) }
                }
                if (pageCount > 1) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                        repeat(pageCount) { i ->
                            val color = if (i == pagerState.currentPage) tokens.text else tokens.muted.copy(alpha = 0.5f)
                            Box(Modifier.padding(3.dp).size(6.dp).background(color, CircleShape))
                        }
                    }
                }
                Dock(state.layout.dock, env)
            }
            (openFolderId?.let { state.layout.findItem(it) } as? FolderItem)?.let { folder ->
                FolderSheet(folder, env) { openFolderId = null }
            }
        }
    }
}
