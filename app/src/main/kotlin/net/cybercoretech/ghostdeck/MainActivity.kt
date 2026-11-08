package net.cybercoretech.ghostdeck

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import net.cybercoretech.ghostdeck.badges.BadgeListenerService
import net.cybercoretech.ghostdeck.edit.setHidden
import net.cybercoretech.ghostdeck.onboarding.OnboardingScreen
import net.cybercoretech.ghostdeck.platform.HomeRole
import net.cybercoretech.ghostdeck.platform.barIconsDark
import net.cybercoretech.ghostdeck.platform.colorHintsOf
import net.cybercoretech.ghostdeck.settings.HomeSettingsHost
import net.cybercoretech.ghostdeck.settings.LabsUnlock
import net.cybercoretech.ghostdeck.settings.SettingsScreen
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import net.cybercoretech.ghostdeck.home.HomeScreen
import net.cybercoretech.ghostdeck.home.HomeViewModel
import net.cybercoretech.ghostdeck.platform.expandNotificationShade
import net.cybercoretech.ghostdeck.ui.theme.ThemeRepository
import net.cybercoretech.ghostdeck.ui.theme.tokensFor

class MainActivity : ComponentActivity() {
    private val _homePresses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when Home is pressed while Ghostdeck is already the foreground home screen. */
    val homePresses: SharedFlow<Unit> = _homePresses

    private val container get() = (application as GhostdeckApp).container

    private val viewModel: HomeViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(container.store, container.catalog, container.defaults, container.io) as T
        }
    }

    private val homeRole by lazy { HomeRole(this) }
    private val labsUnlock = LabsUnlock(SystemClock::elapsedRealtime).also { unlock ->
        val handler = Handler(Looper.getMainLooper())
        unlock.scheduler = { delay, action ->
            val r = Runnable(action)
            handler.postDelayed(r, delay)
            val cancel: () -> Unit = { handler.removeCallbacks(r) }
            cancel
        }
        unlock.onHoldUnlocked = { if (settingsOpen) unlockLabs() }
    }
    private var settingsOpen by mutableStateOf(false)
    private var notificationAccess by mutableStateOf(false)

    /** The system wallpaper's colour hints; decides the bar icon colour on home. */
    private var wallpaperHints by mutableStateOf<Int?>(null)
    private val wallpaperColorsListener = WallpaperManager.OnColorsChangedListener { colors, which ->
        if (which and WallpaperManager.FLAG_SYSTEM != 0) wallpaperHints = colorHintsOf(colors)
    }

    private fun wallpaperManager(): WallpaperManager? = runCatching { WallpaperManager.getInstance(this) }.getOrNull()

    private fun applyBarStyle(darkIcons: Boolean) {
        val style = if (darkIcons) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT) else SystemBarStyle.dark(Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    private fun finishOnboarding() = viewModel.updatePrefs { it.copy(onboardingDone = true) }

    // Onboarding ends whether or not the user accepted the role: they were asked, and Settings is not the place to nag.
    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { finishOnboarding() }

    private val diagnosticsExport = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) writeDiagnostics(uri)
    }

    private fun writeDiagnostics(uri: Uri) {
        val state = viewModel.state.value
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        val text = buildString {
            appendLine("Ghostdeck $version")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Grid ${state.layout.grid.cols}x${state.layout.grid.rows}, dock ${state.layout.dock.items.size} slots")
            appendLine("Theme pack ${state.prefs.themePack}")
            appendLine("Icon pack ${state.prefs.iconPack ?: "system"}")
        }
        val saved = runCatching { contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) } }
        Toast.makeText(this, if (saved.isSuccess) "Diagnostics saved" else "Couldn't save diagnostics", Toast.LENGTH_SHORT).show()
    }

    private fun unlockLabs() {
        viewModel.updatePrefs { it.copy(labsUnlocked = true) }
        Toast.makeText(this, "Labs unlocked", Toast.LENGTH_SHORT).show()
    }

    /** Deep-links to Ghostdeck's own entry in the notification access list, or the plain list on older builds. */
    private fun openNotificationAccess() {
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(this, BadgeListenerService::class.java).flattenToString())
        try {
            startActivity(detail)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    private fun hasNotificationAccess(): Boolean =
        Settings.Secure.getString(contentResolver, "enabled_notification_listeners").orEmpty()
            .split(':').any { ComponentName.unflattenFromString(it)?.packageName == packageName }

    override fun onResume() {
        super.onResume()
        notificationAccess = hasNotificationAccess()
    }

    // Volume Down is consumed only while Settings is up: held for 2 s it opens Labs (spec 10a.7).
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (settingsOpen && keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            // A fresh press discards any hold left over from a missed key-up.
            if (event.repeatCount == 0) labsUnlock.onVolumeDown(false)
            if (labsUnlock.onVolumeDown(true)) unlockLabs()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (settingsOpen && keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            labsUnlock.onVolumeDown(false)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyBarStyle(darkIcons = false)
        // Colour hints need no permission (unlike reading wallpaper pixels).
        wallpaperManager()?.let { wm ->
            wallpaperHints = runCatching { colorHintsOf(wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)) }.getOrNull()
            runCatching { wm.addOnColorsChangedListener(wallpaperColorsListener, Handler(Looper.getMainLooper())) }
        }
        // At the root of a launcher, Back does nothing, except to leave edit mode.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.state.value.editing) viewModel.setEditing(false)
            }
        })
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val themes = remember { ThemeRepository(applicationContext) }
            val pack = remember(state.prefs.themePack) { themes.load(state.prefs.themePack) }
            container.iconPack = state.prefs.iconPack
            container.accentArgb = tokensFor(pack, null).accent.toArgb()
            val homeShowing = state.loaded && state.prefs.onboardingDone && !settingsOpen
            val darkBarIcons = barIconsDark(wallpaperHints, homeShowing)
            LaunchedEffect(darkBarIcons) { applyBarStyle(darkBarIcons) }
            when {
                !state.loaded -> Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(pack.roles.getValue("bg").toInt(16) or 0xFF000000.toInt())))
                !state.prefs.onboardingDone -> OnboardingScreen(
                    pack = pack,
                    onSetAsHome = { if (!homeRole.request(roleRequest)) finishOnboarding() },
                    onFinish = ::finishOnboarding,
                )
                else -> {
                    HomeSettingsHost(showSettings = settingsOpen, home = {
                    // Re-create the home tree when the pack changes so every icon is re-requested.
                    key(state.prefs.iconPack) {
                        HomeScreen(
                            state = state,
                            pack = pack,
                            launcher = container.launcher,
                            icons = container.icons,
                            homePresses = homePresses,
                            onPageChanged = viewModel::goToPage,
                            onDismissIssue = viewModel::dismissLoadIssue,
                            onEmptyLongPress = { viewModel.setEditing(true) },
                            onDoneEditing = { viewModel.setEditing(false) },
                            onLayoutChange = viewModel::updateLayout,
                            onOpenSettings = { settingsOpen = true },
                            onSwipeUp = {}, // Command Bar arrives in a later milestone
                            onSwipeDown = { expandNotificationShade(this) },
                        )
                    }
                    }, settings = {
                        val iconPacks = remember { container.iconPacks.installed() }
                        SettingsScreen(
                            pack = pack,
                            themes = themes.available(),
                            iconPacks = iconPacks,
                            prefs = state.prefs,
                            layout = state.layout,
                            apps = state.apps,
                            versionName = remember { packageManager.getPackageInfo(packageName, 0).versionName.orEmpty() },
                            notificationAccessGranted = notificationAccess,
                            labsUnlock = labsUnlock,
                            onPrefs = viewModel::updatePrefs,
                            onGrid = viewModel::resizeGrid,
                            onDockSlots = viewModel::resizeDock,
                            onUnhide = { k -> viewModel.updateLayout { it.setHidden(k, false) } },
                            onOpenNotificationAccess = ::openNotificationAccess,
                            onLabsUnlocked = ::unlockLabs,
                            onExportDiagnostics = { diagnosticsExport.launch("ghostdeck-diagnostics.txt") },
                            onClose = { settingsOpen = false },
                        )
                    })
                }
            }
        }
    }

    override fun onDestroy() {
        wallpaperManager()?.let { runCatching { it.removeOnColorsChangedListener(wallpaperColorsListener) } }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val isHome = intent.action == Intent.ACTION_MAIN && Intent.CATEGORY_HOME in intent.categories.orEmpty()
        if (isHome && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            settingsOpen = false
            _homePresses.tryEmit(Unit)
        }
    }
}
