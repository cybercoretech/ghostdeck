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
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        val count = collectPresses(c)
        c.newIntent(homeIntent())
        assertEquals(1, count())
    }

    @Test fun homeIntentWhilePausedDoesNotEmit() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        val count = collectPresses(c)
        c.pause()
        c.newIntent(homeIntent())
        assertEquals(0, count())
    }

    @Test fun nonHomeIntentWhileResumedDoesNotEmit() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        val count = collectPresses(c)
        c.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
        assertEquals(0, count())
    }
}
