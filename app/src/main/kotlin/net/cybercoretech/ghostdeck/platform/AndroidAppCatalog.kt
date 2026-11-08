package net.cybercoretech.ghostdeck.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import net.cybercoretech.ghostdeck.catalog.AppCatalog
import net.cybercoretech.ghostdeck.catalog.AppEntry
import net.cybercoretech.ghostdeck.catalog.CatalogEvent
import net.cybercoretech.ghostdeck.catalog.ProfileKind
import net.cybercoretech.ghostdeck.model.AppKey

/** LauncherApps-backed catalog covering the personal, work and Private Space profiles. */
class AndroidAppCatalog(context: Context) : AppCatalog {
    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val userManager = appContext.getSystemService(UserManager::class.java)

    override val events: Flow<CatalogEvent> = callbackFlow {
        fun snapshot(user: UserHandle) {
            val serial = userManager.getSerialNumberForUser(user)
            val kind = kindOf(user)
            trySend(CatalogEvent.Snapshot(serial, kind, launcherApps.getActivityList(null, user).map { it.toEntry(serial, kind) }))
        }

        for (user in launcherApps.profiles) {
            snapshot(user)
            trySend(CatalogEvent.ProfileAvailability(userManager.getSerialNumberForUser(user), available = !userManager.isQuietModeEnabled(user)))
        }

        fun upsert(packageName: String, user: UserHandle) {
            val serial = userManager.getSerialNumberForUser(user)
            val kind = kindOf(user)
            val entries = launcherApps.getActivityList(packageName, user).map { it.toEntry(serial, kind) }
            trySend(CatalogEvent.PackageUpserted(serial, packageName, entries))
        }

        fun remove(packageName: String, user: UserHandle) {
            trySend(CatalogEvent.PackageRemoved(userManager.getSerialNumberForUser(user), packageName))
        }

        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String, user: UserHandle) = upsert(packageName, user)
            override fun onPackageChanged(packageName: String, user: UserHandle) = upsert(packageName, user)
            override fun onPackageRemoved(packageName: String, user: UserHandle) = remove(packageName, user)
            override fun onPackagesAvailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) =
                packageNames.forEach { upsert(it, user) }
            override fun onPackagesUnavailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) {
                trySend(CatalogEvent.PackagesUnavailable(userManager.getSerialNumberForUser(user), packageNames.toList()))
            }
        }
        // Explicit handler: collection may run on a thread without a Looper (e.g. flowOn(IO)).
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val user = intent.userHandle() ?: return
                val available = when (intent.action) {
                    Intent.ACTION_MANAGED_PROFILE_AVAILABLE,
                    Intent.ACTION_MANAGED_PROFILE_UNLOCKED,
                    ACTION_PROFILE_AVAILABLE,
                    ACTION_PROFILE_ACCESSIBLE -> true
                    Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE,
                    ACTION_PROFILE_UNAVAILABLE -> false
                    else -> return
                }
                trySend(CatalogEvent.ProfileAvailability(userManager.getSerialNumberForUser(user), available))
                // Apps seen while the profile was locked may be missing or partial: re-read on unlock.
                if (available) snapshot(user)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                addAction(ACTION_PROFILE_AVAILABLE)
                addAction(ACTION_PROFILE_UNAVAILABLE)
                addAction(ACTION_PROFILE_ACCESSIBLE)
            }
        }
        // System broadcasts only; the not-exported flag exists from API 33 (receivers are exported by default below).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag") // flag does not exist below API 33
            appContext.registerReceiver(receiver, filter)
        }

        awaitClose {
            launcherApps.unregisterCallback(callback)
            appContext.unregisterReceiver(receiver)
        }
        // Bursts (boot, storage mount) must not drop PackageRemoved or ProfileAvailability(false).
    }.buffer(Channel.UNLIMITED)

    private fun kindOf(user: UserHandle): ProfileKind {
        if (user == android.os.Process.myUserHandle()) return ProfileKind.PERSONAL
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return ProfileKind.WORK
        return when (launcherApps.getLauncherUserInfo(user)?.userType) {
            UserManager.USER_TYPE_PROFILE_MANAGED -> ProfileKind.WORK
            UserManager.USER_TYPE_PROFILE_PRIVATE -> ProfileKind.PRIVATE
            else -> ProfileKind.OTHER
        }
    }

    private fun LauncherActivityInfo.toEntry(serial: Long, kind: ProfileKind) =
        AppEntry(AppKey(componentName.packageName, componentName.className, serial), label.toString(), kind)

    @Suppress("DEPRECATION") // typed getParcelableExtra needs API 33; minSdk is 30
    private fun Intent.userHandle(): UserHandle? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(Intent.EXTRA_USER, UserHandle::class.java)
        else getParcelableExtra(Intent.EXTRA_USER)

    private companion object {
        // Intent.ACTION_PROFILE_AVAILABLE/UNAVAILABLE (API 35), spelled out so the file compiles and lints cleanly.
        const val ACTION_PROFILE_AVAILABLE = "android.intent.action.PROFILE_AVAILABLE"
        const val ACTION_PROFILE_UNAVAILABLE = "android.intent.action.PROFILE_UNAVAILABLE"
        const val ACTION_PROFILE_ACCESSIBLE = "android.intent.action.PROFILE_ACCESSIBLE"
    }
}
