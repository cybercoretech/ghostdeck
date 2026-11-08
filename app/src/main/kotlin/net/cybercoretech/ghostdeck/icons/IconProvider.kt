package net.cybercoretech.ghostdeck.icons

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.UserManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.graphics.createBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import net.cybercoretech.ghostdeck.catalog.CatalogEvent
import net.cybercoretech.ghostdeck.model.AppKey

interface IconProvider {
    fun icon(key: AppKey, sizePx: Int): ImageBitmap

    /** Forgets cached icons of [packageName] for [userSerial], or of every package of that user when null. */
    fun invalidate(userSerial: Long, packageName: String? = null) = Unit

    /** Bumps on every invalidation, so on-screen icons know to ask again. */
    val generation: StateFlow<Int> get() = NEVER_CHANGES
}

private val NEVER_CHANGES = MutableStateFlow(0)

/** Evicts what a catalog event makes stale: an updated or unmounted package, or a whole profile on unlock or re-read. */
fun IconProvider.invalidateFor(event: CatalogEvent) {
    when (event) {
        is CatalogEvent.PackageUpserted -> invalidate(event.userSerial, event.packageName)
        is CatalogEvent.PackageRemoved -> invalidate(event.userSerial, event.packageName)
        is CatalogEvent.PackagesUnavailable -> event.packageNames.forEach { invalidate(event.userSerial, it) }
        is CatalogEvent.ProfileAvailability -> if (event.available) invalidate(event.userSerial)
        is CatalogEvent.Snapshot -> invalidate(event.userSerial)
    }
}

private const val CACHE_ENTRIES = 300

/**
 * Badged launcher icons (work/private badge included), cached by key and size.
 *
 * @param source the app's own launcher drawable, or null when it can't be resolved right now.
 * @param draw renders a drawable (or the placeholder for null) at a size.
 */
class AndroidIconProvider(
    private val source: (AppKey) -> Drawable?,
    private val draw: (Drawable?, Int) -> ImageBitmap = ::renderIcon,
) : IconProvider {
    constructor(context: Context) : this(launcherDrawables(context))

    private val cache = LruCache<Pair<AppKey, Int>, ImageBitmap>(CACHE_ENTRIES)
    private val _generation = MutableStateFlow(0)
    override val generation: StateFlow<Int> = _generation

    override fun icon(key: AppKey, sizePx: Int): ImageBitmap {
        val cacheKey = key to sizePx
        synchronized(cache) { cache.get(cacheKey) }?.let { return it }
        val drawable = source(key)
        val bitmap = draw(drawable, sizePx)
        // The placeholder is never cached: a locked profile or an app mid-update has no icon *yet*.
        if (drawable != null) synchronized(cache) { cache.put(cacheKey, bitmap) }
        return bitmap
    }

    override fun invalidate(userSerial: Long, packageName: String?) {
        synchronized(cache) {
            cache.snapshot().keys
                .filter { (k, _) -> k.userSerial == userSerial && (packageName == null || k.packageName == packageName) }
                .forEach { cache.remove(it) }
        }
        _generation.update { it + 1 }
    }

    /** The app's own launcher icon (badged), or null if it can't be resolved. */
    fun drawable(key: AppKey): Drawable? = source(key)
}

private fun launcherDrawables(context: Context): (AppKey) -> Drawable? {
    val launcherApps = context.getSystemService(LauncherApps::class.java)
    val userManager = context.getSystemService(UserManager::class.java)
    return { key ->
        runCatching {
            val user = userManager.getUserForSerialNumber(key.userSerial) ?: error("no user")
            launcherApps.getActivityList(key.packageName, user)
                .firstOrNull { it.componentName == ComponentName(key.packageName, key.className) }
                ?.getBadgedIcon(0)
        }.getOrNull()
    }
}

/** Draws [drawable], or a faint placeholder circle when it is null. */
private fun renderIcon(drawable: Drawable?, size: Int): ImageBitmap {
    val bmp = createBitmap(size, size)
    val canvas = Canvas(bmp)
    if (drawable != null) {
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
    } else {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 255, 255, 255) }
        canvas.drawCircle(size / 2f, size / 2f, size * 0.4f, paint)
    }
    return bmp.asImageBitmap()
}
