package net.cybercoretech.ghostdeck.settings

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import net.cybercoretech.ghostdeck.catalog.AppEntry
import net.cybercoretech.ghostdeck.catalog.ProfileKind
import net.cybercoretech.ghostdeck.icons.IconPack
import net.cybercoretech.ghostdeck.model.AppKey
import net.cybercoretech.ghostdeck.model.GridSize
import net.cybercoretech.ghostdeck.model.Layout
import net.cybercoretech.ghostdeck.model.Prefs
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
class SettingsScreenTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private var prefs by mutableStateOf(Prefs())
    private var layout by mutableStateOf(Layout())
    private var access = false
    private val gridRequests = mutableListOf<GridSize>()
    private val dockRequests = mutableListOf<Int>()
    private var accessOpened = 0
    private var iconPacks = listOf(IconPack("org.example.pack", "Example Pack"))

    @Before fun setUp() = RobolectricMotion.disableAnimations(ctx)

    private fun show(hiddenApps: Map<AppKey, AppEntry> = emptyMap()) {
        val themes = ThemeRepository(ctx)
        rule.setContent {
            SettingsScreen(
                pack = themes.load(prefs.themePack),
                themes = themes.available(),
                iconPacks = iconPacks,
                prefs = prefs,
                layout = layout,
                apps = hiddenApps,
                versionName = "1.2.3",
                notificationAccessGranted = access,
                labsUnlock = LabsUnlock { 0L },
                onPrefs = { f -> prefs = f(prefs) },
                onGrid = { gridRequests += it; layout = layout.copy(grid = it) },
                onDockSlots = { dockRequests += it; layout = layout.copy(dock = net.cybercoretech.ghostdeck.model.Dock(List(it) { null })) },
                onUnhide = { key -> layout = layout.copy(hidden = layout.hidden - key) },
                onOpenNotificationAccess = { accessOpened++ },
                onLabsUnlocked = { prefs = prefs.copy(labsUnlocked = true) },
                onExportDiagnostics = {},
                onClose = {},
            )
        }
    }

    private fun click(tag: String) = rule.onNodeWithTag(tag).performScrollTo().performClick()

    @Test fun gridSlidersClampToSpecRanges() {
        show()
        repeat(8) { click("grid-cols-dec") }
        repeat(8) { click("grid-rows-dec") }
        assertEquals(GridSize(3, 4), layout.grid)
        repeat(12) { click("grid-cols-inc") }
        repeat(12) { click("grid-rows-inc") }
        assertEquals(GridSize(8, 10), layout.grid)
        // Steps at the limits are swallowed, so nothing out of range was ever requested.
        assertTrue(gridRequests.all { it.cols in 3..8 && it.rows in 4..10 })
    }

    @Test fun dockSlotsClampTo0to6() {
        show()
        repeat(8) { click("dock-slots-dec") }
        assertEquals(0, layout.dock.items.size)
        repeat(9) { click("dock-slots-inc") }
        assertEquals(6, layout.dock.items.size)
        assertTrue(dockRequests.all { it in 0..6 })
    }

    @Test fun badgesToggleOpensListenerSettingsWhenNotGranted() {
        access = false
        show()
        click("setting-badges")
        assertEquals(1, accessOpened)
        assertTrue(prefs.badges)
        click("setting-badges") // turning off never needs the system screen
        assertEquals(1, accessOpened)
        assertEquals(false, prefs.badges)
    }

    @Test fun badgesToggleStaysInAppWhenAccessAlreadyGranted() {
        access = true
        show()
        click("setting-badges")
        assertEquals(0, accessOpened)
        assertTrue(prefs.badges)
    }

    @Test fun iconPackListIncludesSystemDefault() {
        show()
        rule.onNodeWithTag("iconpack-system").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("iconpack-org.example.pack").performScrollTo().assertIsDisplayed()
        click("iconpack-org.example.pack")
        assertEquals("org.example.pack", prefs.iconPack)
        click("iconpack-system")
        assertEquals(null, prefs.iconPack)
    }

    @Test fun themePickerListsBundledPacks() {
        show()
        val expected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 5 else 4
        rule.onAllNodes(SemanticsMatcher("is a theme option") {
            (it.config.getOrNull(SemanticsProperties.TestTag) ?: "").startsWith("theme-")
        }).assertCountEquals(expected)
        listOf("ghostdeck", "cybercore-vortex-synthetic", "terminal-ghost-alpha", "cryo-terminal-blue-synthetic", "system")
            .forEach { rule.onNodeWithTag("theme-$it").performScrollTo().assertIsDisplayed() }
        click("theme-terminal-ghost-alpha")
        assertEquals("terminal-ghost-alpha", prefs.themePack)
    }

    @Test fun stepperButtonsHaveContextualLabels() {
        show()
        listOf("grid columns", "grid rows", "dock slots").forEach {
            rule.onNodeWithContentDescription("Decrease $it").performScrollTo().assertIsDisplayed()
            rule.onNodeWithContentDescription("Increase $it").performScrollTo().assertIsDisplayed()
        }
        // The label and its value are one TalkBack stop.
        rule.onNodeWithContentDescription("Grid columns, 5").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Increase grid columns").performClick()
        rule.onNodeWithContentDescription("Grid columns, 6").assertExists()
    }

    @Test fun unhideButtonNamesTheApp() {
        val maps = AppKey("com.maps", "com.maps.Main", 0)
        layout = layout.copy(hidden = setOf(maps))
        show(mapOf(maps to AppEntry(maps, "Maps", ProfileKind.PERSONAL)))
        rule.onNodeWithContentDescription("Unhide Maps").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(layout.hidden.isEmpty())
    }

    @Test fun fineGridAndSwipeMarksToggles() {
        show()
        rule.onNodeWithTag("setting-fine-grid").performScrollTo().assertIsOff()
        rule.onNodeWithTag("setting-swipe-marks").performScrollTo().assertIsOn()
        click("setting-fine-grid")
        click("setting-swipe-marks")
        assertTrue(prefs.fineGrid)
        assertEquals(false, prefs.swipeMarks)
        rule.onNodeWithTag("setting-fine-grid").assertIsOn()
        rule.onNodeWithTag("setting-swipe-marks").assertIsOff()
    }
}
