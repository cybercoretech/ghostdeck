package net.cybercoretech.ghostdeck.icons

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import net.cybercoretech.ghostdeck.catalog.AppEntry
import net.cybercoretech.ghostdeck.catalog.CatalogEvent
import net.cybercoretech.ghostdeck.catalog.ProfileKind
import net.cybercoretech.ghostdeck.model.AppKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidIconProviderTest {
    private class Stub(override val width: Int, val fromDrawable: Drawable?) : ImageBitmap {
        override val height = width
        override val colorSpace = ColorSpaces.Srgb
        override val hasAlpha = true
        override val config = ImageBitmapConfig.Argb8888
        override fun readPixels(buffer: IntArray, startX: Int, startY: Int, width: Int, height: Int, bufferOffset: Int, stride: Int) = Unit
        override fun prepareToDraw() = Unit
    }

    private val key = AppKey("com.a", "com.a.Main", 0)
    private val work = AppKey("com.a", "com.a.Main", 10)
    private var drawable: Drawable? = ColorDrawable(1)
    private val lookups = mutableListOf<AppKey>()
    private val densities = mutableListOf<Int>()
    private val provider = AndroidIconProvider(
        source = { k, density -> lookups += k; densities += density; drawable },
        draw = { d, size -> Stub(size, d) },
        densityDpi = 420,
        maxBytes = 3 * 100 * 100 * 4,
    )

    @Test fun repeatedCallIsServedFromCache() {
        val first = provider.icon(key, 48)
        assertSame(first, provider.icon(key, 48))
        assertEquals(1, lookups.size)
    }

    @Test fun fallbackIsNotCached() {
        drawable = null // e.g. a locked profile or an app mid-update: no activity to draw
        val placeholder = provider.icon(key, 48) as Stub
        assertEquals(null, placeholder.fromDrawable)
        drawable = ColorDrawable(2)
        val real = provider.icon(key, 48) as Stub
        assertSame(drawable, real.fromDrawable)
        assertEquals(2, lookups.size)
    }

    @Test fun packageUpdateEvictsIcon() {
        val before = provider.icon(key, 48)
        val otherUser = provider.icon(work, 48)
        val gen = provider.generation.value
        drawable = ColorDrawable(3)
        provider.invalidateFor(CatalogEvent.PackageUpserted(0, "com.a", listOf(AppEntry(key, "A", ProfileKind.PERSONAL))))
        assertNotSame(before, provider.icon(key, 48))
        assertSame("other profiles keep their icons", otherUser, provider.icon(work, 48))
        assertEquals(gen + 1, provider.generation.value)
    }

    @Test fun unlockEvictsWholeProfile() {
        val before = provider.icon(work, 48)
        provider.invalidateFor(CatalogEvent.ProfileAvailability(10, available = true))
        assertNotSame(before, provider.icon(work, 48))
    }

    @Test fun lockDoesNotEvict() {
        val before = provider.icon(work, 48)
        val gen = provider.generation.value
        provider.invalidateFor(CatalogEvent.ProfileAvailability(10, available = false))
        assertSame(before, provider.icon(work, 48))
        assertEquals(gen, provider.generation.value)
    }

    @Test fun requestsIconAtDeviceDensity() {
        provider.icon(key, 48)
        assertEquals(listOf(420), densities)
    }

    @Test fun evictsByBytes() {
        // Budget: three 100x100 bitmaps. A fourth pushes the oldest out.
        val first = provider.icon(AppKey("com.a", "A", 0), 100)
        provider.icon(AppKey("com.b", "B", 0), 100)
        provider.icon(AppKey("com.c", "C", 0), 100)
        provider.icon(AppKey("com.d", "D", 0), 100)
        assertNotSame("the oldest entry was evicted", first, provider.icon(AppKey("com.a", "A", 0), 100))
        assertEquals(5, lookups.size)
    }
}
