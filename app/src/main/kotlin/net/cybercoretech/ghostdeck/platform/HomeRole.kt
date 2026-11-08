package net.cybercoretech.ghostdeck.platform

import android.app.role.RoleManager
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher

/** Wraps the HOME role so onboarding can ask to become the default launcher. */
class HomeRole(private val activity: ComponentActivity) {
    private val roleManager: RoleManager = activity.getSystemService(RoleManager::class.java)

    fun isHeld(): Boolean = roleManager.isRoleHeld(RoleManager.ROLE_HOME)

    /** Launches the system role prompt, only when the role is available and not already held. */
    fun request(launcher: ActivityResultLauncher<Intent>) {
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_HOME) || isHeld()) return
        launcher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
    }
}
