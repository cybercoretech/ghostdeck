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
) : IconProvider {
    private val cache = LruCache<Triple<AppKey, Int, Int>, ImageBitmap>(CACHE_ENTRIES)
    private var activePack: String? = null
    private var mapping: Map<ComponentName, String> = emptyMap()

    override fun icon(key: AppKey, sizePx: Int): ImageBitmap {
        val pack = packProvider() ?: return base.icon(key, sizePx)
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
        val bitmap = render(pack, key, sizePx, tint)
        synchronized(cache) { if (pack == activePack) cache.put(cacheKey, bitmap) }
        return bitmap
    }

    private fun render(pack: String, key: AppKey, size: Int, tint: Int): ImageBitmap {
        val name = synchronized(cache) { mapping[ComponentName(key.packageName, key.className)] }
        val packed = name?.let { packDrawable(pack, it) }
        return when {
            packed != null -> draw(packed, size)
            else -> monochrome(key, size, tint) ?: base.icon(key, size)
        }
    }

    @Suppress("DiscouragedApi") // icon packs are looked up by name by design
    private fun packDrawable(pack: String, name: String): Drawable? = runCatching {
        val res = context.packageManager.getResourcesForApplication(pack)
        val id = res.getIdentifier(name, "drawable", pack)
        if (id == 0) null else res.getDrawable(id, null)
    }.getOrNull()

    private fun monochrome(key: AppKey, size: Int, tint: Int): ImageBitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val mono = (fallbackDrawable(key) as? AdaptiveIconDrawable)?.monochrome?.mutate() ?: return null
        mono.colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN)
        // Adaptive layers are 108dp with a 72dp visible window, so overscan by a quarter each side.
        return draw(mono, size, overscan = size / 4)
    }

    private fun draw(drawable: Drawable, size: Int, overscan: Int = 0): ImageBitmap {
        val bmp = createBitmap(size, size)
        drawable.setBounds(-overscan, -overscan, size + overscan, size + overscan)
        drawable.draw(Canvas(bmp))
        return bmp.asImageBitmap()
    }
}
