package net.cybercoretech.ghostdeck

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import net.cybercoretech.ghostdeck.data.ConfigStore
import net.cybercoretech.ghostdeck.model.AppItem
import net.cybercoretech.ghostdeck.model.AppKey
import net.cybercoretech.ghostdeck.model.Deck
import net.cybercoretech.ghostdeck.model.Dock
import net.cybercoretech.ghostdeck.model.GhostdeckConfig
import net.cybercoretech.ghostdeck.model.Layout
import net.cybercoretech.ghostdeck.model.Page
import net.cybercoretech.ghostdeck.model.Placed
import net.cybercoretech.ghostdeck.model.Prefs
import net.cybercoretech.ghostdeck.model.cell
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Launches the real MainActivity on a device with a seeded config (onboarding done, two pages).
 * Run with animations off (`adb shell settings put global animator_duration_scale 0`) so the pager settles.
 * The device's own config is backed up before the run and restored after it.
 */
@RunWith(AndroidJUnit4::class)
class HomeSmokeTest {
    private val seed = object : ExternalResource() {
        private val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "config")
        private val store = ConfigStore(dir)
        private var backup: String? = null

        override fun before() {
            backup = store.file.takeIf { it.exists() }?.readText()
            val dockApp = AppItem("dock-a", AppKey("com.android.settings", "com.android.settings.Settings", 0))
            val page0 = Page(listOf(Placed(AppItem("p0", AppKey("com.android.chrome", "com.google.android.apps.chrome.Main", 0)), cell(0, 0))))
            val page1 = Page(listOf(Placed(AppItem("p1", AppKey("com.google.android.deskclock", "com.android.deskclock.DeskClock", 0)), cell(0, 0))))
            store.save(
                GhostdeckConfig(
                    decks = listOf(Deck("home", "Home", "home", layout = Layout(pages = listOf(page0, page1), dock = Dock(listOf(dockApp, null, null))))),
                    activeDeckId = "home",
                    prefs = Prefs(onboardingDone = true),
                ),
            )
        }

        override fun after() {
            val saved = backup
            if (saved == null) store.file.delete() else store.file.writeText(saved)
        }
    }
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(seed).around(compose)

    @Test fun dockShowsAndPagerSwipesToSecondPage() {
        compose.onNodeWithTag("item-dock-a").assertIsDisplayed()
        compose.onNodeWithTag("item-p0").assertIsDisplayed()
        compose.onNodeWithTag("page-grid").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithTag("item-p1").assertIsDisplayed()
    }
}
