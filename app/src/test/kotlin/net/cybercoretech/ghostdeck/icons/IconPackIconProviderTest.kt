package net.cybercoretech.ghostdeck.icons

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.test.core.app.ApplicationProvider
import net.cybercoretech.ghostdeck.model.AppKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class Bmp(override val width: Int) : ImageBitmap {
    override val height = width
    override val colorSpace = ColorSpaces.Srgb
    override val hasAlpha = true
    override val config = ImageBitmapConfig.Argb8888
    override fun readPixels(buffer: IntArray, startX: Int, startY: Int, width: Int, height: Int, bufferOffset: Int, stride: Int) = Unit
    override fun prepareToDraw() = Unit
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IconPackIconProviderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val key = AppKey("com.a", "com.a.Main", 0)
    private val other = AppKey("com.z", "com.z.Main", 0)

    private class CountingBase : IconProvider {
        var calls = 0
        val result = Bmp(1)
        override fun icon(key: AppKey, sizePx: Int): ImageBitmap { calls++; return result }
    }

    private class FakeRepo(context: Context) : IconPackRepository(context) {
        var loads = 0
        override fun load(pack: String): Map<ComponentName, String> {
            loads++
            return mapOf(ComponentName("com.a", "com.a.Main") to "a_icon")
        }
    }

    private val base = CountingBase()
    private val repo = FakeRepo(context)
    private var pack: String? = "pack.one"
    private val loaded = mutableListOf<Pair<String, String>>()
    private val packBitmap = Bmp(2)
    private val provider = IconPackIconProvider(
        base, repo, context, packProvider = { pack },
        drawableLoader = { p, n -> loaded += p to n; ColorDrawable(0) as Drawable },
        drawer = { _, _, _ -> Bmp(loaded.size + 100) },
    )

    @Test fun noPackSelectedPassesThroughToBase() {
        pack = null
        assertSame(base.result, provider.icon(key, 48))
        assertEquals(0, repo.loads)
    }

    @Test fun packDrawableUsedWhenComponentMapped() {
        val bmp = provider.icon(key, 48)
        assertEquals(listOf("pack.one" to "a_icon"), loaded)
        assertEquals(0, base.calls)
        assertEquals(101, bmp.width)
    }

    @Test fun unmappedComponentFallsBackToBase() {
        assertSame(base.result, provider.icon(other, 48))
        assertEquals(emptyList<Pair<String, String>>(), loaded)
        assertEquals(1, base.calls)
    }

    @Test fun repeatedCallIsServedFromCache() {
        provider.icon(key, 48); provider.icon(key, 48)
        assertEquals(1, loaded.size)
    }

    @Test fun changingPackClearsCache() {
        provider.icon(key, 48)
        pack = "pack.two"
        provider.icon(key, 48)
        assertEquals(listOf("pack.one" to "a_icon", "pack.two" to "a_icon"), loaded)
        assertEquals(2, repo.loads)
    }

    @Test fun clearingPackToNoneClearsCache() {
        provider.icon(key, 48)
        pack = null
        provider.icon(key, 48)
        pack = "pack.one"
        provider.icon(key, 48)
        assertEquals(2, loaded.size)
        assertEquals(2, repo.loads)
    }

    @Test fun invalidateEvictsPackIconAndReachesBase() {
        val evicted = mutableListOf<Pair<Long, String?>>()
        val trackingBase = object : IconProvider {
            override fun icon(key: AppKey, sizePx: Int): ImageBitmap = Bmp(1)
            override fun invalidate(userSerial: Long, packageName: String?) { evicted += userSerial to packageName }
        }
        val p = IconPackIconProvider(
            trackingBase, repo, context, packProvider = { pack },
            drawableLoader = { pk, n -> loaded += pk to n; ColorDrawable(0) as Drawable },
            drawer = { _, _, _ -> Bmp(loaded.size + 100) },
        )
        p.icon(key, 48)
        p.invalidate(0, "com.a")
        p.icon(key, 48)
        assertEquals("re-rendered after invalidation", 2, loaded.size)
        assertEquals(listOf(0L to "com.a"), evicted)
    }

    @Test fun baseFallbackIsNotCachedByPack() {
        provider.icon(other, 48); provider.icon(other, 48)
        assertEquals("base owns its own caching, including not caching placeholders", 2, base.calls)
    }
}
