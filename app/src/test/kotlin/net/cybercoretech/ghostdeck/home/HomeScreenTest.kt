package net.cybercoretech.ghostdeck.home

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import net.cybercoretech.ghostdeck.catalog.AppEntry
import net.cybercoretech.ghostdeck.catalog.ProfileKind
import net.cybercoretech.ghostdeck.model.AppItem
import net.cybercoretech.ghostdeck.model.AppKey
import net.cybercoretech.ghostdeck.model.Cell
import net.cybercoretech.ghostdeck.model.Dock
import net.cybercoretech.ghostdeck.model.FolderItem
import net.cybercoretech.ghostdeck.model.ItemAction
import net.cybercoretech.ghostdeck.model.LaunchApp
import net.cybercoretech.ghostdeck.model.Layout
import net.cybercoretech.ghostdeck.model.Page
import net.cybercoretech.ghostdeck.model.Placed
import net.cybercoretech.ghostdeck.model.Prefs
import net.cybercoretech.ghostdeck.model.cell
import net.cybercoretech.ghostdeck.testutil.RobolectricMotion
import net.cybercoretech.ghostdeck.ui.theme.ThemeRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeScreenTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val launcher = FakeLauncher()
    private var swipedUp = 0
    private var swipedDown = 0

    private fun key(n: String, user: Long = 0) = AppKey("com.$n", "com.$n.Main", user)
    private fun apps(vararg k: AppKey) = k.associateWith { AppEntry(it, it.packageName.removePrefix("com.").replaceFirstChar(Char::uppercase), ProfileKind.PERSONAL) }

    @Before fun animationsOff() = RobolectricMotion.disableAnimations(ctx)

    private fun show(
        layout: Layout,
        apps: Map<AppKey, AppEntry>,
        unavailable: Set<Long> = emptySet(),
        prefs: Prefs = Prefs(),
        badges: Map<String, Int> = emptyMap(),
        profiles: Map<Long, ProfileKind> = (apps.keys.map { it.userSerial } + unavailable).associateWith { ProfileKind.PERSONAL },
    ) {
        val pack = ThemeRepository(ctx).load(prefs.themePack)
        rule.setContent {
            HomeScreen(
                state = HomeUiState(layout, apps, unavailable, badges = badges, prefs = prefs, profiles = profiles),
                pack = pack,
                launcher = launcher,
                icons = FakeIcons(),
                homePresses = emptyFlow(),
                onPageChanged = {},
                onSwipeUp = { swipedUp++ },
                onSwipeDown = { swipedDown++ },
            )
        }
    }

    private fun page(vararg p: Placed) = Layout(pages = listOf(Page(p.toList())))

    @Test fun rendersAppAtCell() {
        val maps = key("maps")
        show(page(Placed(AppItem("maps", maps), cell(2, 3))), apps(maps))
        rule.onNodeWithText("Maps").assertIsDisplayed()
        val grid = rule.onNodeWithTag("page-grid").getBoundsInRoot()
        val item = rule.onNodeWithTag("item-maps").getBoundsInRoot()
        val cellW = grid.width / 5
        val cellH = grid.height / 6
        assertEquals((grid.left + cellW * 2).value, item.left.value, 0.6f)
        assertEquals((grid.top + cellH * 3).value, item.top.value, 0.6f)
    }

    @Test fun tapLaunches() {
        val maps = key("maps")
        show(page(Placed(AppItem("maps", maps), cell(0, 0))), apps(maps))
        rule.onNodeWithText("Maps").performClick()
        assertEquals(listOf(maps), launcher.launched)
    }

    @Test fun pausedWorkItemsRenderDimmed() {
        val vault = key("vault", user = 10)
        val layout = Layout(
            pages = listOf(Page(listOf(Placed(AppItem("home-v", vault), cell(0, 0))))),
            dock = Dock(listOf(AppItem("dock-v", vault), null, null)),
        )
        show(layout, apps(vault), unavailable = setOf(10L), profiles = mapOf(0L to ProfileKind.PERSONAL, 10L to ProfileKind.WORK))
        rule.onNodeWithText("Vault").assertIsDisplayed().assertIsNotEnabled()
        rule.onNodeWithTag("item-home-v").performClick()
        rule.onNodeWithTag("item-dock-v").assertIsNotEnabled().performClick()
        assertTrue(launcher.launched.isEmpty())
    }

    @Test fun lockedPrivateItemsAreNotRendered() {
        val vault = key("vault", user = 11)
        val bank = key("bank", user = 11)
        val maps = key("maps")
        val folder = FolderItem("f", "Mixed", listOf(AppItem("fm", maps), AppItem("fb", bank)))
        val layout = Layout(
            pages = listOf(Page(listOf(Placed(AppItem("home-v", vault), cell(0, 0)), Placed(folder, cell(1, 0))))),
            dock = Dock(listOf(AppItem("dock-v", vault), null, null)),
        )
        val entries = apps(maps) + listOf(vault, bank).associateWith {
            AppEntry(it, it.packageName.removePrefix("com.").replaceFirstChar(Char::uppercase), ProfileKind.PRIVATE)
        }
        show(layout, entries, unavailable = setOf(11L), profiles = mapOf(0L to ProfileKind.PERSONAL, 11L to ProfileKind.PRIVATE))
        rule.onAllNodesWithText("Vault").assertCountEquals(0)
        rule.onAllNodesWithTag("item-home-v").assertCountEquals(0)
        rule.onAllNodesWithTag("item-dock-v").assertCountEquals(0)
        // Folders keep their other children but never show the private one.
        rule.onNodeWithText("Mixed").performClick()
        rule.onNodeWithText("Maps").assertIsDisplayed()
        rule.onAllNodesWithText("Bank").assertCountEquals(0)
        rule.onAllNodesWithTag("item-fb").assertCountEquals(0)
    }

    @Test fun itemsRenderNormallyBeforeFirstSnapshot() {
        val maps = key("maps")
        show(page(Placed(AppItem("maps", maps), cell(0, 0))), apps = emptyMap(), profiles = emptyMap())
        rule.onNodeWithTag("item-maps").assertIsEnabled().performClick()
        assertEquals(listOf(maps), launcher.launched)
    }

    @Test fun folderTapOpensSheetWithChildren() {
        val a = key("alpha"); val b = key("beta")
        val folder = FolderItem("f", "Stuff", listOf(AppItem("a", a), AppItem("b", b)))
        show(page(Placed(folder, cell(0, 0))), apps(a, b))
        rule.onNodeWithText("Stuff").performClick()
        rule.onNodeWithTag("folder-sheet").assertIsDisplayed()
        rule.onNodeWithText("Alpha").assertIsDisplayed()
        rule.onNodeWithText("Beta").assertIsDisplayed()
        rule.onNodeWithText("Beta").performClick()
        assertEquals(listOf(b), launcher.launched)
    }

    @Test fun halfOffsetItemRendersBetweenCells() {
        val a = key("alpha"); val b = key("beta")
        show(page(Placed(AppItem("a", a), cell(0, 0)), Placed(AppItem("b", b), Cell(1, 0))), apps(a, b))
        val grid = rule.onNodeWithTag("page-grid").getBoundsInRoot()
        val ax = rule.onNodeWithTag("item-a").getBoundsInRoot().left
        val bx = rule.onNodeWithTag("item-b").getBoundsInRoot().left
        assertEquals((grid.width / 5 / 2).value, (bx - ax).value, 0.6f)
    }

    @Test fun iconSwipeUpRunsSwipeAction() {
        val a = key("alpha"); val b = key("beta"); val c = key("gamma")
        val withAction: ItemAction = LaunchApp(b)
        show(
            page(Placed(AppItem("a", a, swipeUp = withAction), cell(0, 0)), Placed(AppItem("c", c), cell(2, 0))),
            apps(a, b, c),
        )
        rule.onNodeWithTag("item-a").performTouchInput { swipeUp() }
        assertEquals(listOf(b), launcher.launched)
        assertTrue("the swipe did not also tap the icon", a !in launcher.launched)
        assertEquals("handled by the icon, not the page", 0, swipedUp)

        rule.onNodeWithTag("item-c").performTouchInput { swipeUp() }
        assertEquals("no swipe action: nothing launched", listOf(b), launcher.launched)
        assertEquals("the page gets the swipe", 1, swipedUp)
    }

    @Test fun swipeDownOnIconWithSwipeActionOpensShade() {
        val a = key("alpha"); val b = key("beta")
        show(page(Placed(AppItem("a", a, swipeUp = LaunchApp(b)), cell(0, 0))), apps(a, b))
        rule.onNodeWithTag("item-a").performTouchInput { swipeDown() }
        assertEquals("the page gets the downward swipe", 1, swipedDown)
        assertTrue("the icon's swipe-up action did not run", launcher.launched.isEmpty())
        assertEquals(0, swipedUp)
    }

    @Test fun loadIssueShowsBannerAndDismissClears() {
        var state by mutableStateOf(HomeUiState(Layout(), emptyMap(), emptySet(), loadIssue = "Your layout file couldn't be read. The old file was kept as config.corrupt-1.json."))
        val pack = ThemeRepository(ctx).load("ghostdeck")
        rule.setContent {
            HomeScreen(
                state = state, pack = pack, launcher = launcher, icons = FakeIcons(), homePresses = emptyFlow(),
                onPageChanged = {}, onSwipeUp = {}, onSwipeDown = {},
                onDismissIssue = { state = state.copy(loadIssue = null) },
            )
        }
        rule.onNodeWithTag("load-issue-banner").assertIsDisplayed()
        rule.onNodeWithText("config.corrupt-1.json", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("load-issue-dismiss").performClick()
        rule.onAllNodesWithTag("load-issue-banner").assertCountEquals(0)
    }

    @Test fun swipeMarkShownOnlyWhenEnabled() {
        val a = key("alpha"); val b = key("beta")
        val layout = page(Placed(AppItem("a", a, swipeUp = LaunchApp(b)), cell(0, 0)))
        show(layout, apps(a, b), prefs = Prefs(swipeMarks = true))
        rule.onNodeWithTag("swipe-mark-a", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun swipeMarkHiddenWhenDisabled() {
        val a = key("alpha"); val b = key("beta")
        val layout = page(Placed(AppItem("a", a, swipeUp = LaunchApp(b)), cell(0, 0)))
        show(layout, apps(a, b), prefs = Prefs(swipeMarks = false))
        rule.onNodeWithText("Alpha").assertIsDisplayed()
        rule.onAllNodesWithTag("swipe-mark-a", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun dotShownWhenCountPositive() {
        val a = key("a")
        show(Layout(pages = listOf(Page(listOf(Placed(AppItem("ia", a), cell(2, 3)))))), apps(a), badges = mapOf("com.a/0" to 3))
        rule.onNodeWithTag("badge-ia", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun noDotWithoutCount() {
        val a = key("a")
        show(Layout(pages = listOf(Page(listOf(Placed(AppItem("ia", a), cell(2, 3)))))), apps(a), badges = mapOf("com.a/10" to 3))
        rule.onAllNodesWithTag("badge-ia", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun badgedIconAnnouncesNotifications() {
        val a = key("a"); val b = key("b")
        val layout = Layout(pages = listOf(Page(listOf(Placed(AppItem("ia", a), cell(0, 0)), Placed(AppItem("ib", b), cell(1, 0))))))
        show(layout, apps(a, b), badges = mapOf("com.a/0" to 3))
        rule.onNodeWithContentDescription("A, has notifications").assertIsDisplayed()
        // An icon without a badge keeps announcing just its label.
        rule.onAllNodesWithContentDescription("B, has notifications").assertCountEquals(0)
        rule.onNodeWithText("B").assertIsDisplayed()
    }

    private fun showWithHomePresses(initial: HomeUiState, presses: MutableSharedFlow<Unit>, pages: MutableList<Int>, onDone: () -> Unit): (HomeUiState) -> Unit {
        var state by mutableStateOf(initial)
        val pack = ThemeRepository(ctx).load("ghostdeck")
        rule.setContent {
            HomeScreen(
                state = state, pack = pack, launcher = launcher, icons = FakeIcons(), homePresses = presses,
                onPageChanged = { pages += it }, onSwipeUp = {}, onSwipeDown = {},
                onEmptyLongPress = { state = state.copy(editing = true) },
                onDoneEditing = { onDone(); state = state.copy(editing = false) },
            )
        }
        return { state = it }
    }

    @Test fun homePressExitsEditMode() {
        val a = key("alpha")
        val presses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        var done = 0
        val initial = HomeUiState(page(Placed(AppItem("a", a), cell(0, 0))), apps(a), emptySet())
        val set = showWithHomePresses(initial, presses, mutableListOf()) { done++ }
        // Edit mode starts after HomeScreen entered composition, as it does on a real long-press.
        set(initial.copy(editing = true))
        rule.waitForIdle()
        presses.tryEmit(Unit)
        rule.waitForIdle()
        assertEquals(1, done)
    }

    @Test fun homePressScrollsToFirstPage() {
        val a = key("alpha"); val b = key("beta")
        val layout = Layout(pages = listOf(Page(listOf(Placed(AppItem("a", a), cell(0, 0)))), Page(listOf(Placed(AppItem("b", b), cell(0, 0))))))
        val presses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val pages = mutableListOf<Int>()
        showWithHomePresses(HomeUiState(layout, apps(a, b), emptySet(), currentPage = 1), presses, pages) {}
        rule.onNodeWithText("Beta").assertIsDisplayed()
        presses.tryEmit(Unit)
        rule.waitForIdle()
        rule.onNodeWithText("Alpha").assertIsDisplayed()
        assertEquals(0, pages.last())
    }

    // ---- Lane B minors ----

    private fun setContentWith(
        state: () -> HomeUiState,
        pack: () -> net.cybercoretech.ghostdeck.theme.ThemePack = { ThemeRepository(ctx).load("ghostdeck") },
        icons: FakeIcons = FakeIcons(),
        wallpaperOffsets: WallpaperOffsets? = null,
        onTileComposed: ((String) -> Unit)? = null,
    ) {
        rule.setContent {
            HomeScreen(
                state = state(), pack = pack(), launcher = launcher, icons = icons, homePresses = emptyFlow(),
                onPageChanged = {}, onSwipeUp = { swipedUp++ }, onSwipeDown = { swipedDown++ },
                wallpaperOffsets = wallpaperOffsets, onTileComposed = onTileComposed,
            )
        }
    }

    private fun twoPageLayout(a: AppKey, b: AppKey, dock: Dock = Dock(listOf(null, null, null))) = Layout(
        pages = listOf(Page(listOf(Placed(AppItem("a", a), cell(0, 0)))), Page(listOf(Placed(AppItem("b", b), cell(0, 0))))),
        dock = dock,
    )

    private fun materialized(tag: String) =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config[net.cybercoretech.ghostdeck.ui.theme.MaterializedProgressKey]

    @Test fun itemDoesNotReMaterializeWhenPageIsRevisited() {
        // Motion on (the class default is off) and a paused clock, so a re-materializing tile would be caught mid-fade.
        android.provider.Settings.Global.putFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val a = key("alpha"); val b = key("beta")
        rule.mainClock.autoAdvance = false
        val layout = twoPageLayout(a, b)
        setContentWith({ HomeUiState(layout, apps(a, b), emptySet()) })
        rule.mainClock.advanceTimeBy(2_000)
        assertEquals(1f, materialized("item-a"), 0f)

        rule.onNodeWithTag("page-grid").performTouchInput { swipeLeft() }
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithTag("item-b").assertIsDisplayed()
        rule.onNodeWithTag("page-grid").performTouchInput { swipeRight() }
        // The page-0 tile is composed again at the first frame of the swipe back: it must already be settled.
        var frames = 0
        while (rule.onAllNodesWithTag("item-a").fetchSemanticsNodes().isEmpty() && frames++ < 60) rule.mainClock.advanceTimeByFrame()
        assertEquals(1f, materialized("item-a"), 0f)
    }

    @Test fun reorderKeepsTileStateWithItem() {
        val a = key("alpha"); val b = key("beta")
        val icons = FakeIcons()
        var layout by mutableStateOf(page(Placed(AppItem("a", a), cell(0, 0)), Placed(AppItem("b", b), cell(1, 0))))
        setContentWith({ HomeUiState(layout, apps(a, b), emptySet()) }, icons = icons)
        rule.waitUntil(5_000) { icons.requests.map { it.first }.toSet() == setOf(a, b) }
        rule.waitForIdle()
        val before = icons.requests.size
        // Same items, swapped order: with item keys each tile keeps its icon; by slot, both would ask again.
        layout = page(Placed(AppItem("b", b), cell(1, 0)), Placed(AppItem("a", a), cell(0, 0)))
        rule.waitForIdle()
        Thread.sleep(200)
        rule.waitForIdle()
        assertEquals("no icon was requested again", before, icons.requests.size)
        rule.onNodeWithTag("item-a").assertIsDisplayed()
        rule.onNodeWithTag("item-b").assertIsDisplayed()
    }

    @Test fun pageSwipeDoesNotRecomposeTiles() {
        val a = key("alpha"); val b = key("beta"); val d = key("dock")
        val composed = java.util.Collections.synchronizedMap(HashMap<String, Int>())
        val layout = twoPageLayout(a, b, Dock(listOf(AppItem("d", d), null, null)))
        setContentWith({ HomeUiState(layout, apps(a, b, d), emptySet()) }, onTileComposed = { composed.merge(it, 1, Int::plus) })
        rule.waitForIdle()
        val before = composed.getValue("d")
        rule.onNodeWithTag("page-grid").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithTag("item-b").assertIsDisplayed()
        assertEquals("a tile that stays visible is not recomposed by a page change", before, composed.getValue("d"))
    }

    @Test fun tapPassesCurrentBoundsToLauncher() {
        val maps = key("maps")
        show(page(Placed(AppItem("maps", maps), cell(2, 3))), apps(maps))
        rule.onNodeWithTag("item-maps").performClick()
        val bounds = launcher.launchBounds.single()!!
        val node = rule.onNodeWithTag("item-maps").fetchSemanticsNode().boundsInWindow
        assertTrue("bounds are not empty: $bounds", bounds.width() > 0 && bounds.height() > 0)
        assertEquals(node.left.toInt(), bounds.left)
        assertEquals(node.top.toInt(), bounds.top)
        assertEquals(node.right.toInt(), bounds.right)
        assertEquals(node.bottom.toInt(), bounds.bottom)
    }

    @Test fun wallpaperOffsetIsSentAfterWindowAttach() {
        val a = key("alpha")
        val sent = java.util.Collections.synchronizedList(mutableListOf<Pair<Float, Int>>())
        setContentWith(
            { HomeUiState(page(Placed(AppItem("a", a), cell(0, 0))), apps(a), emptySet()) },
            wallpaperOffsets = WallpaperOffsets { _, position, count -> sent += position to count },
        )
        rule.waitUntil(5_000) { sent.isNotEmpty() }
        assertEquals(0f to 1, sent.first())
    }

    @Test fun swipeUpUsesLatestActionAfterRecomposition() {
        val a = key("alpha"); val b = key("beta"); val c = key("gamma")
        var action by mutableStateOf<ItemAction>(LaunchApp(b))
        setContentWith({ HomeUiState(page(Placed(AppItem("a", a, swipeUp = action), cell(0, 0))), apps(a, b, c), emptySet()) })
        action = LaunchApp(c)
        rule.waitForIdle()
        rule.onNodeWithTag("item-a").performTouchInput { swipeUp() }
        assertEquals(listOf(c), launcher.launched)
    }

    @Test fun pageSwipeDownCallsOnSwipeDown() {
        val a = key("alpha")
        show(page(Placed(AppItem("a", a), cell(0, 0))), apps(a))
        rule.onNodeWithTag("page-grid").performTouchInput { swipeDown(startY = height * 0.6f, endY = height * 0.95f) }
        assertEquals(1, swipedDown)
        assertEquals(0, swipedUp)
    }

    @Test fun profileUnlockRestoresEnabledItems() {
        val vault = key("vault", user = 10)
        val profiles = mapOf(0L to ProfileKind.PERSONAL, 10L to ProfileKind.WORK)
        var unavailable by mutableStateOf(emptySet<Long>())
        setContentWith({
            HomeUiState(page(Placed(AppItem("v", vault), cell(0, 0))), apps(vault), unavailable, profiles = profiles)
        })
        unavailable = setOf(10L) // ProfileAvailability(10, false)
        rule.waitForIdle()
        rule.onNodeWithTag("item-v").assertIsNotEnabled()
        unavailable = emptySet() // ProfileAvailability(10, true)
        rule.waitForIdle()
        rule.onNodeWithTag("item-v").assertIsEnabled().performClick()
        assertEquals(listOf(vault), launcher.launched)
    }

    @Test fun workProfileTileIsAnnouncedAsWork() {
        val slack = key("slack", user = 10)
        val e = mapOf(slack to AppEntry(slack, "Slack", ProfileKind.WORK))
        show(page(Placed(AppItem("w", slack), cell(0, 0))), e, profiles = mapOf(0L to ProfileKind.PERSONAL, 10L to ProfileKind.WORK))
        rule.onNodeWithTag("item-w").assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "work"))
        rule.onNodeWithText("Slack").assertIsDisplayed()
    }

    @Test fun folderTileIsAnnouncedAsFolder() {
        val a = key("alpha"); val b = key("beta")
        val folder = FolderItem("f", "Stuff", listOf(AppItem("fa", a), AppItem("fb", b)))
        show(page(Placed(folder, cell(0, 0))), apps(a, b))
        rule.onNodeWithTag("item-f").assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "folder, 2 items"))
    }

    @Test fun accentChangeReRequestsIcons() {
        val a = key("alpha")
        val icons = FakeIcons()
        val repo = ThemeRepository(ctx)
        var pack by mutableStateOf(repo.load("ghostdeck"))
        setContentWith({ HomeUiState(page(Placed(AppItem("a", a), cell(0, 0))), apps(a), emptySet()) }, pack = { pack }, icons = icons)
        rule.waitUntil(5_000) { icons.requests.isNotEmpty() }
        val before = icons.requests.size
        pack = repo.bundled().first { it.roles["accent_hot"] != repo.load("ghostdeck").roles["accent_hot"] }
        // The request runs on Dispatchers.Default: give it real time, then let the UI settle.
        rule.waitForIdle(); Thread.sleep(500); rule.waitForIdle()
        rule.waitUntil(5_000) { icons.requests.size > before }
    }

    @Test fun folderShowsDotWhenAnyVisibleChildHasNotifications() {
        val a = key("alpha"); val b = key("beta")
        val folder = FolderItem("f", "Stuff", listOf(AppItem("fa", a), AppItem("fb", b)))
        show(page(Placed(folder, cell(0, 0))), apps(a, b), badges = mapOf("com.beta/0" to 2))
        rule.onNodeWithTag("badge-f", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun folderShowsNoDotWhenOnlyAHiddenPrivateChildHasOne() {
        val a = key("alpha"); val v = key("vault", user = 11)
        val folder = FolderItem("f", "Stuff", listOf(AppItem("fa", a), AppItem("fv", v)))
        val entries = apps(a) + (v to AppEntry(v, "Vault", ProfileKind.PRIVATE))
        show(
            page(Placed(folder, cell(0, 0))), entries, unavailable = setOf(11L),
            badges = mapOf("com.vault/11" to 4), profiles = mapOf(0L to ProfileKind.PERSONAL, 11L to ProfileKind.PRIVATE),
        )
        rule.onNodeWithTag("item-f").assertIsDisplayed()
        rule.onAllNodesWithTag("badge-f", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun shortcutTileShowsDotForItsPackage() {
        val a = key("alpha")
        val shortcut = net.cybercoretech.ghostdeck.model.ShortcutItem("s", "com.alpha", "new", 0, "New thing")
        show(page(Placed(shortcut, cell(0, 0))), apps(a), badges = mapOf("com.alpha/0" to 1))
        rule.onNodeWithTag("badge-s", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun folderSheetClosesWhenAllChildrenBecomeHidden() {
        val v = key("vault", user = 11); val w = key("bank", user = 11)
        val folder = FolderItem("f", "Private", listOf(AppItem("fv", v), AppItem("fb", w)))
        val entries = listOf(v, w).associateWith { AppEntry(it, it.packageName.removePrefix("com.").replaceFirstChar(Char::uppercase), ProfileKind.PRIVATE) }
        val profiles = mapOf(0L to ProfileKind.PERSONAL, 11L to ProfileKind.PRIVATE)
        var unavailable by mutableStateOf(emptySet<Long>())
        setContentWith({ HomeUiState(page(Placed(folder, cell(0, 0))), entries, unavailable, profiles = profiles) })
        rule.onNodeWithText("Private").performClick()
        rule.onNodeWithTag("folder-sheet").assertIsDisplayed()
        unavailable = setOf(11L) // the Private Space locks
        rule.waitForIdle()
        rule.onAllNodesWithTag("folder-sheet").assertCountEquals(0)
    }
}
