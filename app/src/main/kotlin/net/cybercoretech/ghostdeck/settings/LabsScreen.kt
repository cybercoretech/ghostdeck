package net.cybercoretech.ghostdeck.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.cybercoretech.ghostdeck.model.Prefs
import net.cybercoretech.ghostdeck.ui.components.GhostButton

/**
 * Experiments and debugging (spec 10a.7). Nothing a normal user needs lives here. This is page content: it is
 * shown inside [SettingsScreen]'s scaffold.
 */
@Composable
fun LabsScreen(
    prefs: Prefs,
    onPrefs: ((Prefs) -> Prefs) -> Unit,
    onExportDiagnostics: () -> Unit,
    onLock: () -> Unit,
) {
    Muted("Experiments and debugging aids. They may change or disappear.")
    ToggleRow(
        "Grid bounds overlay", "Warning: draws an outline around every home grid cell.",
        prefs.debugGridBounds, "labs-grid-bounds",
    ) { on -> onPrefs { it.copy(debugGridBounds = on) } }
    Muted("Export diagnostics writes a text file with the app and Android versions and your grid and pack ids. You choose where it goes; nothing is sent anywhere.")
    GhostButton("Export diagnostics", onExportDiagnostics, Modifier.fillMaxWidth(), tag = "labs-export")
    GhostButton("Lock Labs", onLock, Modifier.fillMaxWidth(), tag = "labs-lock")
}
