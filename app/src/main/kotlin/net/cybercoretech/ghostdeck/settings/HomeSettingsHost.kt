package net.cybercoretech.ghostdeck.settings

import androidx.compose.runtime.Composable

/**
 * Shows Settings or the home screen, never both. Home is not composed while Settings is up, so touches on empty
 * Settings areas cannot reach its tiles and TalkBack has no hidden tree to focus. Home's own state (page, edit mode)
 * lives in the view model, so it comes back as it was.
 */
@Composable
fun HomeSettingsHost(showSettings: Boolean, home: @Composable () -> Unit, settings: @Composable () -> Unit) {
    if (showSettings) settings() else home()
}
