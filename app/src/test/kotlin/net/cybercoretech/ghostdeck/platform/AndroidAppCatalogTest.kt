package net.cybercoretech.ghostdeck.platform

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.Executor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import net.cybercoretech.ghostdeck.catalog.CatalogEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35]) // Robolectric's SDK ceiling
class AndroidAppCatalogTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun unlockSnapshotRunsOffMainThread() {
        val queued = mutableListOf<Runnable>()
        val executor = Executor { queued += it } // stands in for the background thread: nothing runs inline
        val events = mutableListOf<CatalogEvent>()
        val scope = TestScope(UnconfinedTestDispatcher())
        val job: Job = AndroidAppCatalog(ctx, executor).events.onEach { events += it }.launchIn(scope)
        shadowOf(Looper.getMainLooper()).idle()
        events.clear()

        ctx.sendBroadcast(Intent(Intent.ACTION_MANAGED_PROFILE_UNLOCKED).putExtra(Intent.EXTRA_USER, Process.myUserHandle()))
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue("availability is emitted first", events.single() is CatalogEvent.ProfileAvailability)
        assertEquals("the snapshot was handed to the executor, not run in onReceive", 1, queued.size)

        queued.single().run()
        assertTrue(events.last() is CatalogEvent.Snapshot)
        assertEquals(2, events.size)
        job.cancel()
    }
}
