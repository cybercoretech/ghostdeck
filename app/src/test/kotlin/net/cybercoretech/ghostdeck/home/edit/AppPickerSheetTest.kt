package net.cybercoretech.ghostdeck.home.edit

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import net.cybercoretech.ghostdeck.catalog.AppEntry
import net.cybercoretech.ghostdeck.catalog.ProfileKind
import net.cybercoretech.ghostdeck.home.FakeIcons
import net.cybercoretech.ghostdeck.model.AppItem
import net.cybercoretech.ghostdeck.model.AppKey
import net.cybercoretech.ghostdeck.model.Cell
import net.cybercoretech.ghostdeck.model.ItemAction
import net.cybercoretech.ghostdeck.model.LaunchApp
import net.cybercoretech.ghostdeck.model.LaunchShortcut
import net.cybercoretech.ghostdeck.model.Layout
import net.cybercoretech.ghostdeck.model.Page
import net.cybercoretech.ghostdeck.model.Placed
import net.cybercoretech.ghostdeck.model.ShowShortcuts
import net.cybercoretech.ghostdeck.model.LayoutResult
import net.cybercoretech.ghostdeck.model.findItem
import net.cybercoretech.ghostdeck.model.setSwipeUp
import net.cybercoretech.ghostdeck.platform.AppShortcut
import net.cybercoretech.ghostdeck.testutil.RobolectricMotion
import net.cybercoretech.ghostdeck.ui.theme.GhostdeckTheme
import net.cybercoretech.ghostdeck.ui.theme.ThemeRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPickerSheetTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before fun animationsOff() = RobolectricMotion.disableAnimations(ctx)

    private fun key(n: String, user: Long = 0) = AppKey("com.$n", "com.$n.Main", user)
    private fun entry(n: String, profile: ProfileKind = ProfileKind.PERSONAL, user: Long = 0) =
        AppEntry(key(n, user), n.replaceFirstChar(Char::uppercase), profile)

    private fun themed(content: @androidx.compose.runtime.Composable () -> Unit) =
        rule.setContent { GhostdeckTheme(ThemeRepository(ctx).load("ghostdeck")) { content() } }

    private fun picker(apps: List<AppEntry>, hidden: Set<AppKey> = emptySet(), picked: MutableList<AppKey> = mutableListOf(), toggled: MutableList<AppKey> = mutableListOf()) =
        themed {
            AppPickerSheet(apps, hidden, FakeIcons(), onPick = { picked += it.key }, onToggleHidden = { toggled += it }, onDismiss = {})
        }

    @Test fun filtersByQueryCaseInsensitive() {
        picker(listOf(entry("alpha"), entry("gamma"), entry("Photos")))
        rule.onNodeWithTag("picker-row-com.alpha-0").assertIsDisplayed()
        rule.onNodeWithTag("picker-search").performTextInput("GAM")
        rule.onAllNodesWithTag("picker-row-com.alpha-0").assertCountEquals(0)
        rule.onNodeWithTag("picker-row-com.gamma-0").assertIsDisplayed()
    }

    @Test fun hiddenAppsExcludedUnlessToggled() {
        picker(listOf(entry("alpha"), entry("beta")), hidden = setOf(key("beta")))
        rule.onNodeWithTag("picker-row-com.alpha-0").assertIsDisplayed()
        rule.onAllNodesWithTag("picker-row-com.beta-0").assertCountEquals(0)
        rule.onNodeWithTag("picker-show-hidden").performClick()
        rule.onNodeWithTag("picker-row-com.beta-0").assertIsDisplayed()
    }

    @Test fun showsProfileBadgeForWorkAndPrivate() {
        picker(listOf(entry("alpha"), entry("slack", ProfileKind.WORK, user = 10), entry("vault", ProfileKind.PRIVATE, user = 11), entry("other", ProfileKind.OTHER, user = 12)))
        rule.onNodeWithTag("picker-badge-com.slack-10", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Work").assertIsDisplayed()
        rule.onNodeWithText("Private").assertIsDisplayed()
        rule.onAllNodesWithTag("picker-badge-com.alpha-0", useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithTag("picker-badge-com.other-12", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun tapPicksAndHideButtonToggles() {
        val picked = mutableListOf<AppKey>()
        val toggled = mutableListOf<AppKey>()
        picker(listOf(entry("alpha")), picked = picked, toggled = toggled)
        rule.onNodeWithTag("picker-row-com.alpha-0").performClick()
        assertEquals(listOf(key("alpha")), picked)
        rule.onNodeWithTag("picker-hide-com.alpha-0").performClick()
        assertEquals(listOf(key("alpha")), toggled)
    }

    @Test fun swipeActionPickerSetsItemAction() {
        val owner = AppItem("chrome", key("chrome"))
        var layout = Layout(pages = listOf(Page(listOf(Placed(owner, Cell(0, 0))))))
        var chosen: ItemAction? = LaunchApp(key("zzz"))
        var applied by mutableStateOf(0)
        val shortcut = AppShortcut("com.chrome", "new-tab", "New tab", 0)
        themed {
            SwipeActionPicker(
                owner = owner,
                apps = listOf(entry("chrome"), entry("maps")),
                shortcuts = listOf(shortcut),
                onChoose = { action ->
                    chosen = action
                    layout = (layout.setSwipeUp(owner.id, action) as LayoutResult.Ok).layout
                    applied++
                },
                onBack = {},
            )
        }
        fun swipeOf() = (layout.findItem("chrome") as AppItem).swipeUp

        rule.onNodeWithTag("swipe-app-com.maps-0").performClick()
        assertEquals(LaunchApp(key("maps")), swipeOf())
        rule.onNodeWithTag("swipe-shortcut-new-tab").performClick()
        assertEquals(LaunchShortcut("com.chrome", "new-tab", 0), swipeOf())
        rule.onNodeWithTag("swipe-show-shortcuts").performClick()
        assertEquals(ShowShortcuts, swipeOf())
        rule.onNodeWithTag("swipe-none").performClick()
        assertNull(swipeOf())
        assertNull(chosen)
        assertEquals(4, applied)
    }

    @Test fun sameAppInTwoProfilesHasDistinctRows() {
        picker(listOf(entry("chrome"), entry("chrome", ProfileKind.WORK, user = 10)))
        rule.onNodeWithTag("picker-row-com.chrome-0").assertIsDisplayed()
        rule.onNodeWithTag("picker-row-com.chrome-10").assertIsDisplayed()
        rule.onNodeWithTag("picker-badge-com.chrome-10", useUnmergedTree = true).assertIsDisplayed()
        rule.onAllNodesWithTag("picker-badge-com.chrome-0", useUnmergedTree = true).assertCountEquals(0)
    }
}
