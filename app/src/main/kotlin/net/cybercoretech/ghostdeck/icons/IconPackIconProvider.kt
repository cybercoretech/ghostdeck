package net.cybercoretech.ghostdeck.icons

import android.content.ComponentName
import android.content.Context
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.flow.StateFlow
import net.cybercoretech.ghostdeck.model.AppKey

private const val CACHE_ENTRIES = 300

/**
 * Applies the selected icon pack on top of [base]. With no pack selected it is a pure pass-through.
 * Icons the pack doesn't cover use [base], or, on API 33+, the app's monochrome layer tinted with [accent].
 *
 * @param fallbackDrawable the app's own icon drawable, for the monochrome layer.
 */
class IconPackIconProvider(
    private val base: IconProvider,
    private val repo: IconPackRepository,
    private val context: Context,
    private val packProvider: () -> String?,
    private val accent: () -> Int = { 0xFFFFFFFF.toInt() },
    private val fallbackDrawable: (AppKey) -> Drawable? = { null },
    private val drawableLoader: (pack: String, name: String) -> Drawable? = { pack, name -> loadPackDrawable(context, pack, name) },
    private val drawer: (Drawable, size: Int, overscan: Int) -> ImageBitmap = ::drawToBitmap,
) : IconProvider {
    private val cache = LruCache<Triple<AppKey, Int, Int>, ImageBitmap>(CACHE_ENTRIES)
    private var activePack: String? = null
    private var mapping: Map<ComponentName, String> = emptyMap()

    /** Every invalidation goes through [base] too, so its generation covers both caches. */
    override val generation: StateFlow<Int> get() = base.generation

    override fun invalidate(userSerial: Long, packageName: String?) {
        synchronized(cache) {
            cache.snapshot().keys
                .filter { (k, _, _) -> k.userSerial == userSerial && (packageName == null || k.packageName == packageName) }
                .forEach { cache.remove(it) }
        }
        base.invalidate(userSerial, packageName)
    }

    override fun icon(key: AppKey, sizePx: Int): ImageBitmap {
        val pack = packProvider()
        if (pack == null) {
            synchronized(cache) { // back to system icons: forget everything from the old pack
                if (activePack != null) { cache.evictAll(); activePack = null; mapping = emptyMap() }
            }
            return base.icon(key, sizePx)
        }
        val tint = accent()
        val cacheKey = Triple(key, sizePx, tint)
        synchronized(cache) {
            if (pack != activePack) { // pack changed: drop everything rendered for the old one
                cache.evictAll()
                activePack = pack
                mapping = repo.load(pack)
            }
            cache.get(cacheKey)?.let { return it }
        }
        // Only icons this class drew are cached here; base icons are cached (or not, for placeholders) by base.
        val bitmap = render(pack, key, sizePx, tint) ?: return base.icon(key, sizePx)
        synchronized(cache) { if (pack == activePack) cache.put(cacheKey, bitmap) }
        return bitmap
    }

    private fun render(pack: String, key: AppKey, size: Int, tint: Int): ImageBitmap? {
        val name = synchronized(cache) { mapping[ComponentName(key.packageName, key.className)] }
        val packed = name?.let { drawableLoader(pack, it) }
        return if (packed != null) drawer(packed, size, 0) else monochrome(key, size, tint)
    }

    private fun monochrome(key: AppKey, size: Int, tint: Int): ImageBitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val mono = (fallbackDrawable(key) as? AdaptiveIconDrawable)?.monochrome?.mutate() ?: return null
        mono.colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN)
        // Adaptive layers are 108dp with a 72dp visible window, so overscan by a quarter each side.
        return drawer(mono, size, size / 4)
    }
}

@Suppress("DiscouragedApi") // icon packs are looked up by name by design
private fun loadPackDrawable(context: Context, pack: String, name: String): Drawable? = runCatching {
    val res = context.packageManager.getResourcesForApplication(pack)
    val id = res.getIdentifier(name, "drawable", pack)
    if (id == 0) null else res.getDrawable(id, null)
}.getOrNull()

private fun drawToBitmap(drawable: Drawable, size: Int, overscan: Int): ImageBitmap {
    val bmp = createBitmap(size, size)
    drawable.setBounds(-overscan, -overscan, size + overscan, size + overscan)
    drawable.draw(Canvas(bmp))
    return bmp.asImageBitmap()
}
