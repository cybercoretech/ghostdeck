package net.cybercoretech.ghostdeck.icons

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.UserManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.graphics.createBitmap
import androidx.compose.ui.graphics.asImageBitmap
import net.cybercoretech.ghostdeck.model.AppKey

interface IconProvider {
    fun icon(key: AppKey, sizePx: Int): ImageBitmap
}

private const val CACHE_ENTRIES = 300

/** Badged launcher icons (work/private badge included), cached by key and size. */
class AndroidIconProvider(context: Context) : IconProvider {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val cache = LruCache<Pair<AppKey, Int>, ImageBitmap>(CACHE_ENTRIES)

    override fun icon(key: AppKey, sizePx: Int): ImageBitmap {
        val cacheKey = key to sizePx
        synchronized(cache) { cache.get(cacheKey) }?.let { return it }
        val bitmap = render(key, sizePx)
        synchronized(cache) { cache.put(cacheKey, bitmap) }
        return bitmap
    }

    private fun render(key: AppKey, size: Int): ImageBitmap {
        val bmp = createBitmap(size, size)
        val canvas = Canvas(bmp)
        val drawable = runCatching {
            val user = userManager.getUserForSerialNumber(key.userSerial) ?: error("no user")
            launcherApps.getActivityList(key.packageName, user)
                .firstOrNull { it.componentName == ComponentName(key.packageName, key.className) }
                ?.getBadgedIcon(0)
        }.getOrNull()
        if (drawable != null) {
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
        } else {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 255, 255, 255) }
            canvas.drawCircle(size / 2f, size / 2f, size * 0.4f, paint)
        }
        return bmp.asImageBitmap()
    }
}
