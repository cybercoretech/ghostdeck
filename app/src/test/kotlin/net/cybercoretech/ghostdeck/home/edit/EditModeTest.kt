package net.cybercoretech.ghostdeck.home.edit

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.center
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
    @get:Rule val rule = createComposeRule()
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val launcher = FakeLauncher()
    private var editTriggered = 0
    private var done = 0
    private val changes = mutableListOf<(Layout) -> Layout>()

    @Before fun animationsOff() = RobolectricMotion.disableAnimations(ctx)

    private fun key(n: String) = AppKey("com.$n", "com.$n.Main", 0)
    private val layout = Layout(pages = listOf(Page(listOf(Placed(AppItem("a", key("alpha")), cell(0, 0)), Placed(AppItem("b", key("beta")), cell(1, 0))))))
    private val apps = listOf("alpha", "beta").associate { key(it) to AppEntry(key(it), it.replaceFirstChar(Char::uppercase), ProfileKind.PERSONAL) }

    private fun show(editing: Boolean = false) {
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
        rule.onNodeWithTag("picker-row-com.alpha").performClick()
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
        rule.onNodeWithTag("picker-row-com.alpha").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("picker-hide-com.alpha").assertHeightIsAtLeast(48.dp)
    }

    @Test fun menuRowsAreAtLeast48dp() {
        show()
        longPressAndDrag("item-a", null)
        rule.onNodeWithTag("menu-app-info").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("menu-remove").assertHeightIsAtLeast(48.dp)
    }
}
