package net.cybercoretech.ghostdeck

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import net.cybercoretech.ghostdeck.catalog.AppCatalog
import net.cybercoretech.ghostdeck.catalog.CatalogEvent
import net.cybercoretech.ghostdeck.data.ConfigStore
import net.cybercoretech.ghostdeck.icons.AndroidIconProvider
import net.cybercoretech.ghostdeck.icons.IconPackIconProvider
import net.cybercoretech.ghostdeck.icons.IconPackRepository
import net.cybercoretech.ghostdeck.icons.IconProvider
import net.cybercoretech.ghostdeck.icons.invalidateFor
import net.cybercoretech.ghostdeck.platform.AndroidAppCatalog
import net.cybercoretech.ghostdeck.platform.AndroidAppLauncher
import net.cybercoretech.ghostdeck.platform.AndroidDefaultApps
import net.cybercoretech.ghostdeck.platform.AppLauncher
import net.cybercoretech.ghostdeck.platform.DefaultAppsResolver

/** Manual DI: one of each, created lazily. All config access goes through the single-thread [io]. */
class AppContainer(app: Application) {
    val io = Executors.newSingleThreadExecutor { Thread(it, "ghostdeck-io") }.asCoroutineDispatcher()
    val store = ConfigStore(File(app.filesDir, "config"))
    val launcher: AppLauncher = AndroidAppLauncher(app)
    /** Selected icon pack and accent colour, pushed in from the UI state (see MainActivity). */
    @Volatile var iconPack: String? = null
    @Volatile var accentArgb: Int = 0xFFA78BFA.toInt()
    private val baseIcons = AndroidIconProvider(app)
    val iconPacks = IconPackRepository(app)
    val icons: IconProvider = IconPackIconProvider(
        base = baseIcons, repo = iconPacks, context = app,
        packProvider = { iconPack }, accent = { accentArgb }, fallbackDrawable = baseIcons::drawable,
    )
    /** Every catalog event first evicts the icons it makes stale, before the view model sees it. */
    val catalog: AppCatalog = IconInvalidatingCatalog(
        AndroidAppCatalog(app).let {
            if (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) LoggingCatalog(it) else it
        },
        icons,
    )
    val defaults: DefaultAppsResolver = AndroidDefaultApps(app)
}

class GhostdeckApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

private class IconInvalidatingCatalog(private val inner: AppCatalog, private val icons: IconProvider) : AppCatalog by inner {
    override val events: Flow<CatalogEvent> = inner.events.onEach(icons::invalidateFor)
}

/** Debug builds only: one log line per catalog event under tag Ghostdeck. */
private class LoggingCatalog(private val inner: AppCatalog) : AppCatalog by inner {
    override val events: Flow<CatalogEvent> = inner.events.onEach { e ->
        Log.d("Ghostdeck", when (e) {
            is CatalogEvent.Snapshot -> "catalog Snapshot user=${e.userSerial} profile=${e.profile} apps=${e.entries.size}"
            else -> "catalog $e"
        })
    }
}
