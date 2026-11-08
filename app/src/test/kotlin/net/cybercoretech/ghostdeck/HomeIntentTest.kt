package net.cybercoretech.ghostdeck

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import net.cybercoretech.ghostdeck.testutil.RobolectricMotion
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeIntentTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before fun animationsOff() = RobolectricMotion.disableAnimations(ctx)

    private val controllers = mutableListOf<org.robolectric.android.controller.ActivityController<MainActivity>>()

    private fun launch() = Robolectric.buildActivity(MainActivity::class.java).setup().also { controllers += it }

    /**
     * The view model reads the config and the app catalog on the app's own io thread. Let that work finish and
     * tear the activity down inside the test, so nothing touches the Robolectric environment after it is reset
     * (that surfaced as UncaughtExceptionsBeforeTest in whichever Compose test ran next).
     */
    @After fun tearDown() {
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

    private fun collectPresses(controller: org.robolectric.android.controller.ActivityController<MainActivity>): () -> Int {
        var count = 0
        val scope = TestScope(UnconfinedTestDispatcher())
        controller.get().homePresses.onEach { count++ }.launchIn(scope)
        return { count }
    }

    @Test fun homeIntentWhileResumedEmitsHomePress() {
        val c = launch()
        val count = collectPresses(c)
        c.newIntent(homeIntent())
        assertEquals(1, count())
    }

    @Test fun homeIntentWhilePausedDoesNotEmit() {
        val c = launch()
        val count = collectPresses(c)
        c.pause()
        c.newIntent(homeIntent())
        assertEquals(0, count())
    }

    @Test fun nonHomeIntentWhileResumedDoesNotEmit() {
        val c = launch()
        val count = collectPresses(c)
        c.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
        assertEquals(0, count())
    }
}
