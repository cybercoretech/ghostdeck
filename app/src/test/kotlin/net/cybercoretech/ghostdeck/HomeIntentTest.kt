package net.cybercoretech.ghostdeck

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withContext
import net.cybercoretech.ghostdeck.testutil.RobolectricMotion
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35]) // 35 is the highest SDK this Robolectric release supports; compileSdk is 36
class HomeIntentTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val controllers = mutableListOf<ActivityController<MainActivity>>()
    private val scope = TestScope(UnconfinedTestDispatcher())

    @Before fun animationsOff() = RobolectricMotion.disableAnimations(ctx)

    private fun launch() = Robolectric.buildActivity(MainActivity::class.java).setup().also { controllers += it }

    /**
     * The view model reads the config and the app catalog on the app's own io thread. Let that work finish and
     * tear the activity down inside the test, so nothing touches the Robolectric environment after it is reset
     * (that surfaced as UncaughtExceptionsBeforeTest in whichever Compose test ran next).
     */
    @After fun tearDown() {
        scope.cancel()
        val io = (ctx as GhostdeckApp).container.io
        runBlocking { withContext(io) {} }
        controllers.forEach { if (!it.get().isDestroyed) it.pause().stop().destroy() }
        runBlocking { withContext(io) {} }
    }

    private fun homeIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    @Test fun mainActivityHandlesHome() {
        val i = homeIntent().setPackage(ctx.packageName)
        val resolved = ctx.packageManager.resolveActivity(i, 0)!!
        assertEquals(MainActivity::class.java.name, resolved.activityInfo.name)
    }

    /** Counts the home presses the activity emits from now on. */
    private fun ActivityController<MainActivity>.countPresses(): () -> Int {
        var count = 0
        get().homePresses.onEach { count++ }.launchIn(scope)
        return { count }
    }

    @Test fun homeIntentWhileResumedEmitsHomePress() {
        val c = launch()
        val count = c.countPresses()
        c.newIntent(homeIntent())
        assertEquals(1, count())
    }

    @Test fun homeIntentWhilePausedDoesNotEmit() {
        val c = launch()
        val count = c.countPresses()
        c.pause()
        c.newIntent(homeIntent())
        assertEquals(0, count())
    }

    @Test fun nonHomeIntentWhileResumedDoesNotEmit() {
        val c = launch()
        val count = c.countPresses()
        c.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
        assertEquals(0, count())
    }

    @Test fun backAtRootOnlyLeavesEditMode() {
        val c = launch()
        val activity = c.get()
        activity.viewModel.setEditing(true)
        activity.onBackPressedDispatcher.onBackPressed()
        assertFalse("first Back leaves edit mode", activity.viewModel.state.value.editing)
        activity.onBackPressedDispatcher.onBackPressed()
        assertFalse("second Back does nothing at the root", activity.isFinishing)
        assertTrue(activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
    }
}
