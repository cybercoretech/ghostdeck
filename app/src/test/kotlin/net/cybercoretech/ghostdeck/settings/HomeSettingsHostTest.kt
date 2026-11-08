package net.cybercoretech.ghostdeck.settings

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
class HomeSettingsHostTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val launcher = FakeLauncher()

    @Before fun setUp() = RobolectricMotion.disableAnimations(ctx)

    @Test fun tapOnEmptySettingsAreaDoesNotLaunchApp() {
        val themes = ThemeRepository(ctx)
        val pack = themes.load("ghostdeck")
        // The app sits in the top-right cell, right under an empty part of the Settings header.
        val key = AppKey("com.maps", "com.maps.Main", 0)
        val layout = Layout(pages = listOf(Page(listOf(Placed(AppItem("m", key), cell(4, 0))))))
        val apps = mapOf(key to AppEntry(key, "Maps", ProfileKind.PERSONAL))
        rule.setContent {
            HomeSettingsHost(
                showSettings = true,
                home = {
                    HomeScreen(
                        state = HomeUiState(layout, apps, emptySet(), loaded = true),
                        pack = pack, launcher = launcher, icons = FakeIcons(), homePresses = emptyFlow(),
                        onPageChanged = {}, onSwipeUp = {}, onSwipeDown = {},
                    )
                },
                settings = {
                    SettingsScreen(
                        pack = pack, themes = themes.available(), iconPacks = emptyList(), prefs = net.cybercoretech.ghostdeck.model.Prefs(),
                        layout = layout, apps = apps, versionName = "1", notificationAccessGranted = false,
                        labsUnlock = LabsUnlock { 0L }, onPrefs = {}, onGrid = {}, onDockSlots = {}, onUnhide = {},
                        onOpenNotificationAccess = {}, onLabsUnlocked = {}, onExportDiagnostics = {}, onClose = {},
                    )
                },
            )
        }
        rule.onNodeWithTag("settings-screen").performTouchInput { click(Offset(width - 8f, 8f)) }
        rule.waitForIdle()
        assertEquals(emptyList<AppKey>(), launcher.launched)
        // And the home tree is not there for TalkBack to focus.
        assertTrue(rule.onAllNodesWithTagCount("item-m") == 0)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size
}
