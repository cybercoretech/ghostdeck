package net.cybercoretech.ghostdeck.home

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
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
    ) {
        val pack = ThemeRepository(ctx).load(prefs.themePack)
        rule.setContent {
            HomeScreen(
                state = HomeUiState(layout, apps, unavailable, prefs = prefs),
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

    @Test fun unavailableProfileItemsRenderDimmedAndDontLaunch() {
        val vault = key("vault", user = 10)
        val layout = Layout(
            pages = listOf(Page(listOf(Placed(AppItem("home-v", vault), cell(0, 0))))),
            dock = Dock(listOf(AppItem("dock-v", vault), null, null)),
        )
        show(layout, apps(vault), unavailable = setOf(10L))
        rule.onNodeWithText("Vault").assertIsDisplayed().assertIsNotEnabled()
        rule.onNodeWithTag("item-home-v").performClick()
        rule.onNodeWithTag("item-dock-v").assertIsNotEnabled().performClick()
        assertTrue(launcher.launched.isEmpty())
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
}
