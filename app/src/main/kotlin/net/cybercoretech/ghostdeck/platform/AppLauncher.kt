package net.cybercoretech.ghostdeck.platform

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import androidx.core.net.toUri
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import net.cybercoretech.ghostdeck.model.AppKey
import net.cybercoretech.ghostdeck.model.ShortcutItem

interface AppLauncher {
    fun launch(key: AppKey, bounds: Rect?)
    fun launchShortcut(item: ShortcutItem, bounds: Rect?)
    fun appInfo(key: AppKey)
    fun uninstall(key: AppKey)
}

private const val TAG = "Ghostdeck"

class AndroidAppLauncher(context: Context) : AppLauncher {
    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val userManager = appContext.getSystemService(UserManager::class.java)

    private fun user(serial: Long): UserHandle? = userManager.getUserForSerialNumber(serial)

    private inline fun guarded(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: SecurityException) {
            Log.w(TAG, "$what refused", e)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "$what: activity not found", e)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "$what: not available", e)
        }
    }

    override fun launch(key: AppKey, bounds: Rect?) = guarded("launch ${key.packageName}") {
        val user = user(key.userSerial) ?: return
        launcherApps.startMainActivity(ComponentName(key.packageName, key.className), user, bounds, null)
    }

    override fun launchShortcut(item: ShortcutItem, bounds: Rect?) = guarded("shortcut ${item.shortcutId}") {
        val user = user(item.userSerial) ?: return
        launcherApps.startShortcut(item.packageName, item.shortcutId, bounds, null, user)
    }

    override fun appInfo(key: AppKey) = guarded("app info ${key.packageName}") {
        val user = user(key.userSerial) ?: return
        launcherApps.startAppDetailsActivity(ComponentName(key.packageName, key.className), user, null, null)
    }

    override fun uninstall(key: AppKey) = guarded("uninstall ${key.packageName}") {
        val intent = Intent(Intent.ACTION_DELETE, "package:${key.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        user(key.userSerial)?.let { intent.putExtra(Intent.EXTRA_USER, it) }
        appContext.startActivity(intent)
    }
}

private var shadeFailureLogged = false

/**
 * Opens the notification shade through the hidden StatusBarManager (as other launchers do).
 * If a future Android blocks it the gesture does nothing; the failure is logged once.
 */
fun expandNotificationShade(context: Context) {
    runCatching {
        val service = context.getSystemService("statusbar") ?: error("no statusbar service")
        service.javaClass.getMethod("expandNotificationsPanel").invoke(service)
    }.onFailure {
        if (!shadeFailureLogged) {
            shadeFailureLogged = true
            Log.w(TAG, "Cannot expand the notification shade", it)
        }
    }
}
