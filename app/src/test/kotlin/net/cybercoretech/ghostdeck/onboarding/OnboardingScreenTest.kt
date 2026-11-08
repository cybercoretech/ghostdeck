package net.cybercoretech.ghostdeck.onboarding

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import net.cybercoretech.ghostdeck.R
import net.cybercoretech.ghostdeck.model.Prefs
import net.cybercoretech.ghostdeck.testutil.RobolectricMotion
import net.cybercoretech.ghostdeck.ui.theme.ThemeRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnboardingScreenTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    // The one way the screen can reach the platform. Every request is recorded; nothing may fire before the user asks.
    private val requests = mutableListOf<String>()
    private var prefs = Prefs()

    @Before fun setUp() = RobolectricMotion.disableAnimations(ctx)

    private fun show() = rule.setContent {
        OnboardingScreen(
            pack = ThemeRepository(ctx).load("ghostdeck"),
            onSetAsHome = { requests += "home-role" },
            onFinish = { prefs = prefs.copy(onboardingDone = true) },
        )
    }

    @Test fun showsAllSevenCharterLines() {
        show()
        rule.onNodeWithText(ctx.getString(R.string.charter_title)).assertExists()
        val lines = ctx.resources.getStringArray(R.array.charter_lines)
        assertEquals(7, lines.size)
        lines.forEach { rule.onNodeWithText(it).assertExists() }
    }

    @Test fun noPermissionRequestedOnFirstScreen() {
        show()
        // Render and interact with the charter step first.
        rule.onNodeWithText(ctx.getString(R.string.charter_title)).assertExists()
        rule.onNodeWithTag("onboarding-continue").performScrollTo()
        rule.waitForIdle()
        assertEquals(emptyList<String>(), requests)
        rule.onNodeWithTag("onboarding-continue").performClick()
        // The home-screen step is showing, and still nothing has been requested.
        rule.onNodeWithTag("onboarding-set-home").assertExists()
        assertEquals(emptyList<String>(), requests)
        assertFalse(prefs.onboardingDone)
        rule.onNodeWithTag("onboarding-set-home").performClick()
        assertEquals(listOf("home-role"), requests)
    }

    @Test fun continueThenNotNowFinishesOnboarding() {
        show()
        rule.onNodeWithTag("onboarding-continue").performScrollTo().performClick()
        assertFalse(prefs.onboardingDone)
        rule.onNodeWithText("Not now").performClick()
        assertTrue(prefs.onboardingDone)
        assertEquals(emptyList<String>(), requests)
    }

    @Test fun titleIsAHeading() {
        show()
        rule.onNodeWithTag("onboarding-title").assert(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Heading))
    }
}
