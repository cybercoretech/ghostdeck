package net.cybercoretech.ghostdeck.catalog

import net.cybercoretech.ghostdeck.model.Layout
import net.cybercoretech.ghostdeck.model.allAppKeys
import net.cybercoretech.ghostdeck.model.remapApp
import net.cybercoretech.ghostdeck.model.removeApp

/**
 * Keeps the saved layout in step with a catalog event. Only package removal edits the layout;
 * a locked profile keeps its items so they reappear on unlock.
 */
fun Layout.reconcile(event: CatalogEvent, state: CatalogState): Layout = when (event) {
    is CatalogEvent.PackageRemoved -> allAppKeys()
        .filter { it.userSerial == event.userSerial && it.packageName == event.packageName }
        .fold(this) { layout, key -> layout.removeApp(key) }
    else -> this
}

/**
 * Remaps layout apps whose launcher component vanished (e.g. an update renamed the activity)
 * when the package now exposes exactly one launcher activity for that user.
 */
fun Layout.resolveStale(state: CatalogState): Layout {
    val stale = allAppKeys().filter { it !in state.entries && it.userSerial !in state.unavailableUsers }
    return stale.fold(this) { layout, key ->
        val candidates = state.entries.keys.filter { it.packageName == key.packageName && it.userSerial == key.userSerial }
        if (candidates.size == 1) layout.remapApp(key, candidates.single()) else layout
    }
}
