package net.cybercoretech.ghostdeck.home.edit

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.center
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import net.cybercoretech.ghostdeck.model.FolderItem
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.emptyFlow
import net.cybercoretech.ghostdeck.catalog.AppEntry
import net.cybercoretech.ghostdeck.catalog.ProfileKind
import net.cybercoretech.ghostdeck.home.FakeIcons
import net.cybercoretech.ghostdeck.home.FakeLauncher
import net.cybercoretech.ghostdeck.home.HomeScreen
import net.cybercoretech.ghostdeck.home.HomeUiState
import net.cybercoretech.ghostdeck.model.AppItem
import net.cybercoretech.ghostdeck.model.AppKey
import net.cybercoretech.ghostdeck.model.Layout
import net.cybercoretech.ghostdeck.model.Page
import net.cybercoretech.ghostdeck.model.Placed
import net.cybercoretech.ghostdeck.model.cell
import net.cybercoretech.ghostdeck.model.findItem
import net.cybercoretech.ghostdeck.model.removeApp
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.unit.dp
import net.cybercoretech.ghostdeck.testutil.RobolectricMotion
import net.cybercoretech.ghostdeck.ui.theme.ThemeRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditModeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val launcher = FakeLauncher()
    private var editTriggered = 0
    private var done = 0
    private val changes = mutableListOf<(Layout) -> Layout>()

    @Before fun animationsOff() = RobolectricMotion.disableAnimations(ctx)

    private fun key(n: String) = AppKey("com.$n", "com.$n.Main", 0)
    private val layout = Layout(pages = listOf(Page(listOf(Placed(AppItem("a", key("alpha")), cell(0, 0)), Placed(AppItem("b", key("beta")), cell(1, 0))))))
    private val apps = listOf("alpha", "beta").associate { key(it) to AppEntry(key(it), it.replaceFirstChar(Char::uppercase), ProfileKind.PERSONAL) }

    private fun show(editing: Boolean = false, layout: Layout = this.layout, apps: Map<AppKey, AppEntry> = this.apps) {
        val pack = ThemeRepository(ctx).load("ghostdeck")
        rule.setContent {
            HomeScreen(
                state = HomeUiState(layout, apps, emptySet(), editing = editing),
                pack = pack, launcher = launcher, icons = FakeIcons(), homePresses = emptyFlow(),
                onPageChanged = {}, onSwipeUp = {}, onSwipeDown = {},
                onEmptyLongPress = { editTriggered++ }, onDoneEditing = { done++ }, onLayoutChange = { changes += it },
            )
        }
    }

    /** Holds a finger down on [tag] past the long-press timeout, then moves it by [by] and lifts it. */
    private fun longPressAndDrag(tag: String, by: Offset?) {
        // Each performTouchInput flushes its events, so the clock moves between the calls.
        rule.onNodeWithTag(tag).performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(800)
        if (by != null) {
            rule.onNodeWithTag(tag).performTouchInput { moveBy(by / 2f) }
            rule.onNodeWithTag(tag).performTouchInput { moveBy(by / 2f) }
        }
        rule.onNodeWithTag(tag).performTouchInput { up() }
    }

    @Test fun longPressOnEmptySpaceTriggersEditMode() {
        show()
        rule.onNodeWithTag("page-grid").performTouchInput { down(Offset(width * 0.5f, height * 0.7f)) }
        rule.mainClock.advanceTimeBy(800)
        rule.onNodeWithTag("page-grid").performTouchInput { up() }
        assertEquals(1, editTriggered)
        assertTrue(launcher.launched.isEmpty())
    }

    @Test fun editModeShowsTargetsAndDoneButton() {
        show(editing = true)
        rule.onNodeWithTag("drop-remove").assertIsDisplayed()
        rule.onNodeWithTag("drop-uninstall").assertIsDisplayed()
        rule.onNodeWithTag("edit-add-apps").assertIsDisplayed()
        rule.onNodeWithTag("edit-done").performClick()
        assertEquals(1, done)
    }

    @Test fun addAppsOpensThePicker() {
        show(editing = true)
        rule.onNodeWithTag("edit-add-apps").performClick()
        rule.onNodeWithTag("app-picker").assertIsDisplayed()
        rule.onNodeWithTag("picker-row-com.alpha-0").performClick()
        val placed = changes.single()(layout).pages[0].placed
        assertEquals("a picked app lands in the first free whole cell", 3, placed.size)
        assertEquals(cell(2, 0), placed.last().cell)
        rule.onAllNodesWithTag("app-picker").assertCountEquals(0)
    }

    @Test fun longPressOnItemOpensMenuAndLaunchesNothing() {
        show()
        longPressAndDrag("item-a", null)
        rule.onNodeWithTag("item-menu").assertIsDisplayed()
        rule.onNodeWithTag("menu-app-info").assertIsDisplayed()
        assertTrue("the long press did not also tap", launcher.launched.isEmpty())
        rule.onNodeWithTag("menu-remove").performClick()
        assertNull(changes.single()(layout).findItem("a"))
        rule.onAllNodesWithTag("item-menu").assertCountEquals(0)
    }

    @Test fun menuHideTakesAppOffHomeAndHidesIt() {
        show()
        longPressAndDrag("item-a", null)
        rule.onNodeWithTag("menu-hide").performClick()
        val next = changes.single()(layout)
        assertNull(next.findItem("a"))
        assertEquals(setOf(key("alpha")), next.hidden)
    }

    @Test fun dragIconToEmptyCellMovesIt() {
        show(editing = true)
        longPressAndDrag("item-a", Offset(0f, 200f))
        val moved = changes.single()(layout).pages[0].placed.single { it.item.id == "a" }.cell
        assertEquals("same column, further down", 0, moved.x)
        assertTrue("moved down from row 0 (was ${moved.y})", moved.y > 0)
        assertNotNull(changes.single()(layout).findItem("b"))
    }

    @Test fun dragToRemoveTargetRemovesItem() {
        show(editing = true)
        val target = rule.onNodeWithTag("drop-remove").fetchSemanticsNode().boundsInRoot
        val item = rule.onNodeWithTag("item-a").fetchSemanticsNode().boundsInRoot
        longPressAndDrag("item-a", target.center - item.center)
        assertNull(changes.single()(layout).findItem("a"))
    }

    @Test fun hideUsesCurrentLayout() {
        show()
        longPressAndDrag("item-a", null)
        rule.onNodeWithTag("menu-hide").performClick()
        val withoutBeta = layout.removeApp(key("beta"))
        val out = changes.single()(withoutBeta)
        assertNull("beta stays removed", out.findItem("b"))
        assertNull(out.findItem("a"))
        assertEquals(setOf(key("alpha")), out.hidden)
    }

    @Test fun touchTargetsAreAtLeast48dp() {
        show(editing = true)
        rule.onNodeWithTag("edit-done").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("edit-add-apps").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("edit-add-apps").performClick()
        rule.onNodeWithTag("picker-row-com.alpha-0").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("picker-hide-com.alpha-0").assertHeightIsAtLeast(48.dp)
    }

    @Test fun menuRowsAreAtLeast48dp() {
        show()
        longPressAndDrag("item-a", null)
        rule.onNodeWithTag("menu-app-info").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("menu-remove").assertHeightIsAtLeast(48.dp)
    }

    private fun pressBack() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    @Test fun backLeavesEditMode() {
        show(editing = true)
        pressBack()
        assertEquals(1, done)
    }

    @Test fun backClosesItemMenu() {
        show()
        longPressAndDrag("item-a", null)
        rule.onNodeWithTag("item-menu").assertIsDisplayed()
        pressBack()
        rule.onAllNodesWithTag("item-menu").assertCountEquals(0)
    }

    @Test fun backClosesAppPickerBeforeLeavingEditMode() {
        show(editing = true)
        rule.onNodeWithTag("edit-add-apps").performClick()
        rule.onNodeWithTag("app-picker").assertIsDisplayed()
        pressBack()
        rule.onAllNodesWithTag("app-picker").assertCountEquals(0)
        assertEquals("the first Back only closed the picker", 0, done)
    }

    @Test fun backClosesFolderSheet() {
        val folder = FolderItem("f", "Stuff", listOf(AppItem("fa", key("alpha")), AppItem("fb", key("beta"))))
        show(layout = Layout(pages = listOf(Page(listOf(Placed(folder, cell(0, 0)))))))
        rule.onNodeWithText("Stuff").performClick()
        rule.onNodeWithTag("folder-sheet").assertIsDisplayed()
        pressBack()
        rule.onAllNodesWithTag("folder-sheet").assertCountEquals(0)
    }

    @Test fun menuTitleUsesCatalogLabel() {
        val gmail = AppKey("com.google.android.gm", "com.google.android.gm.ConversationListActivityGmail", 0)
        show(
            layout = Layout(pages = listOf(Page(listOf(Placed(AppItem("g", gmail), cell(0, 0)))))),
            apps = mapOf(gmail to AppEntry(gmail, "Gmail", ProfileKind.PERSONAL)),
        )
        longPressAndDrag("item-g", null)
        rule.onNodeWithTag("item-menu").assertIsDisplayed()
        rule.onNodeWithTag("item-menu-title", useUnmergedTree = true).assertTextEquals("Gmail")
    }

    // ---- Lane B minors ----

    private fun themed(content: @androidx.compose.runtime.Composable () -> Unit) =
        rule.setContent { net.cybercoretech.ghostdeck.ui.theme.GhostdeckTheme(ThemeRepository(ctx).load("ghostdeck")) { content() } }

    @Test fun swipePickerShowsProfileBadge() {
        val owner = AppItem("o", key("alpha"))
        val slack = AppKey("com.slack", "com.slack.Main", 10)
        val personal = AppKey("com.slack", "com.slack.Main", 0)
        themed {
            SwipeActionPicker(
                owner = owner,
                apps = listOf(AppEntry(personal, "Slack", ProfileKind.PERSONAL), AppEntry(slack, "Slack", ProfileKind.WORK)),
                shortcuts = emptyList(), onChoose = {}, onBack = {},
            )
        }
        rule.onNodeWithTag("swipe-app-com.slack-10").assertIsDisplayed()
        rule.onNodeWithTag("swipe-app-com.slack-0").assertIsDisplayed()
        rule.onNodeWithText("Slack (Work)").assertIsDisplayed()
        rule.onNodeWithText("Slack").assertIsDisplayed()
    }

    @Test fun swipePickerBackControlIsAtLeast48dp() {
        themed { SwipeActionPicker(AppItem("o", key("alpha")), emptyList(), emptyList(), onChoose = {}, onBack = {}) }
        rule.onNodeWithTag("swipe-back").assertHeightIsAtLeast(48.dp)
    }

    @Test fun canUninstallDoesNotBlockMainThread() {
        show(editing = true)
        rule.waitUntil(5_000) { launcher.systemAppThreads.size >= 2 }
        val main = android.os.Looper.getMainLooper().thread
        assertTrue("isSystemApp ran on ${launcher.systemAppThreads}", launcher.systemAppThreads.none { it === main })
    }

    @Test fun dropCommitsTheTransformOfItsOwnOutcome() {
        val session = EditSession(layout, canUninstall = { true })
        session.geometry.remove = androidx.compose.ui.geometry.Rect(0f, 0f, 100f, 100f)
        session.start(net.cybercoretech.ghostdeck.edit.DragSource.Existing("a"), layout.findItem("a")!!, Offset(50f, 50f))
        session.move(Offset(50f, 50f), fine = false, grid = layout.grid)
        val commit = session.finish()
        assertTrue(commit.outcome is net.cybercoretech.ghostdeck.edit.DropOutcome.Updated)
        assertNull(commit.transform(layout).findItem("a"))
        assertNotNull(commit.transform(layout).findItem("b"))
    }

    @Test fun dragGhostSnapsLikeTheController() {
        for (fine in listOf(false, true)) {
            val session = EditSession(layout, canUninstall = { true })
            val controller = net.cybercoretech.ghostdeck.edit.EditController(layout) { true }.also { it.fine = fine }
            session.start(net.cybercoretech.ghostdeck.edit.DragSource.Existing("a"), layout.findItem("a")!!, Offset.Zero)
            session.move(Offset.Zero, fine, layout.grid)
            val raw = net.cybercoretech.ghostdeck.model.Cell(3, 5)
            val (x, y) = controller.snap(raw.x, raw.y)
            assertEquals(net.cybercoretech.ghostdeck.model.Cell(x, y), session.snapped(raw))
        }
    }

    @Test fun swipePickerChoiceIsPersistedByHomeScreen() {
        show()
        longPressAndDrag("item-a", null)
        rule.onNodeWithTag("menu-swipe").performClick()
        rule.onNodeWithTag("swipe-app-com.beta-0").performClick()
        val item = changes.single()(layout).findItem("a") as AppItem
        assertEquals(net.cybercoretech.ghostdeck.model.LaunchApp(key("beta")), item.swipeUp)
    }

    @Test fun showShortcutsSwipeOpensMenu() {
        val withSwipe = Layout(pages = listOf(Page(listOf(Placed(AppItem("a", key("alpha"), swipeUp = net.cybercoretech.ghostdeck.model.ShowShortcuts), cell(0, 0))))))
        show(layout = withSwipe)
        rule.onNodeWithTag("item-a").performTouchInput { swipeUp() }
        rule.onNodeWithTag("item-menu").assertIsDisplayed()
    }

    @Test fun editBarFitsAtDoubleFontScale() {
        val pack = ThemeRepository(ctx).load("ghostdeck")
        rule.setContent {
            val d = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, fontScale = 2f),
            ) {
                HomeScreen(
                    state = HomeUiState(layout, apps, emptySet(), editing = true),
                    pack = pack, launcher = launcher, icons = FakeIcons(), homePresses = emptyFlow(),
                    onPageChanged = {}, onSwipeUp = {}, onSwipeDown = {},
                )
            }
        }
        for (tag in listOf("drop-remove", "drop-uninstall", "edit-settings", "edit-add-apps", "edit-done")) {
            rule.onNodeWithTag(tag).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        }
    }

    @Test fun doubleTapHideDoesNotReHide() {
        show(editing = true)
        rule.onNodeWithTag("edit-add-apps").performClick()
        rule.onNodeWithTag("picker-hide-com.alpha-0").performClick()
        rule.onNodeWithTag("picker-hide-com.alpha-0").performClick()
        // Both taps hit the same render snapshot; each transform must decide on the layout it is applied to.
        val net = changes.fold(layout) { l, f -> f(l) }
        assertEquals(2, changes.size)
        assertTrue("hidden then unhidden: net visible", key("alpha") !in net.hidden)
    }
}
