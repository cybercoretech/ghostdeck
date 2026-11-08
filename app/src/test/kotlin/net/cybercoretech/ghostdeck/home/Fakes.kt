package net.cybercoretech.ghostdeck.home

import android.graphics.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import net.cybercoretech.ghostdeck.catalog.AppCatalog
import net.cybercoretech.ghostdeck.catalog.CatalogEvent
import net.cybercoretech.ghostdeck.icons.IconProvider
import net.cybercoretech.ghostdeck.model.AppKey
import net.cybercoretech.ghostdeck.model.ShortcutItem
import net.cybercoretech.ghostdeck.platform.AppLauncher

class FakeCatalog : AppCatalog {
    val flow = MutableSharedFlow<CatalogEvent>(extraBufferCapacity = 64)
    override val events: Flow<CatalogEvent> = flow
}

class FakeLauncher : AppLauncher {
    val launched = mutableListOf<AppKey>()
    val shortcuts = mutableListOf<ShortcutItem>()
    /** The bounds passed with each launch, in order. */
    val launchBounds = mutableListOf<Rect?>()
    /** Threads isSystemApp was called on, and the answer it gives (default: system, so not uninstallable). */
    val systemAppThreads = java.util.Collections.synchronizedList(mutableListOf<Thread>())
    var systemApp = true
    override fun isSystemApp(key: AppKey): Boolean { systemAppThreads += Thread.currentThread(); return systemApp }
    override fun launch(key: AppKey, bounds: Rect?) { launched += key; launchBounds += bounds }
    override fun launchShortcut(item: ShortcutItem, bounds: Rect?) { shortcuts += item }
    override fun appInfo(key: AppKey) = Unit
    override fun uninstall(key: AppKey) = Unit
}

class FakeIcons : IconProvider {
    /** Every (key, size) requested, in order. */
    val requests = java.util.Collections.synchronizedList(mutableListOf<Pair<AppKey, Int>>())

    /** A bitmap-less stand-in: Robolectric's legacy graphics cannot allocate real bitmaps. */
    override fun icon(key: AppKey, sizePx: Int): ImageBitmap = requests.add(key to sizePx).let { object : ImageBitmap {
        override val width = sizePx
        override val height = sizePx
        override val colorSpace = ColorSpaces.Srgb
        override val hasAlpha = true
        override val config = ImageBitmapConfig.Argb8888
        override fun prepareToDraw() = Unit
        override fun readPixels(buffer: IntArray, startX: Int, startY: Int, width: Int, height: Int, bufferOffset: Int, stride: Int) = Unit
    } }
}
