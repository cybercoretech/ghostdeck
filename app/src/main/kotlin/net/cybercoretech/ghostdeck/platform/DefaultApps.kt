package net.cybercoretech.ghostdeck.platform

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import androidx.core.net.toUri
import android.os.Process
import android.os.UserManager
import android.provider.MediaStore
import net.cybercoretech.ghostdeck.model.AppKey

/** Dialer, SMS, browser, camera, in that order; the ones the device lacks are skipped. */
fun interface DefaultAppsResolver {
    fun resolve(): List<AppKey>
}

class AndroidDefaultApps(context: Context) : DefaultAppsResolver {
    private val appContext = context.applicationContext

    override fun resolve(): List<AppKey> {
        val pm = appContext.packageManager
        val launcherApps = appContext.getSystemService(LauncherApps::class.java)
        val me = Process.myUserHandle()
        val serial = appContext.getSystemService(UserManager::class.java).getSerialNumberForUser(me)
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, "smsto:".toUri()),
            Intent(Intent.ACTION_VIEW, "http://example.com".toUri()).addCategory(Intent.CATEGORY_BROWSABLE),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
        )
        return intents.mapNotNull { intent ->
            val pkg = pm.resolveActivity(intent, 0)?.activityInfo?.packageName?.takeIf { it != "android" }
                ?: pm.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.firstOrNull { it != "android" }
            pkg?.let { launcherApps.getActivityList(it, me).firstOrNull() }?.let {
                AppKey(it.componentName.packageName, it.componentName.className, serial)
            }
        }.distinct()
    }
}
