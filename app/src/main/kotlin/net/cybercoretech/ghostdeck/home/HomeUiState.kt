package net.cybercoretech.ghostdeck.home

import net.cybercoretech.ghostdeck.catalog.AppEntry
import net.cybercoretech.ghostdeck.catalog.ProfileKind
import net.cybercoretech.ghostdeck.model.AppKey
import net.cybercoretech.ghostdeck.model.Layout
import net.cybercoretech.ghostdeck.model.Prefs

/** [apps] holds every known entry, including those of unavailable profiles (kept for their labels). */
data class HomeUiState(
    val layout: Layout,
    val apps: Map<AppKey, AppEntry>,
    val unavailableUsers: Set<Long>,
    val currentPage: Int = 0,
    val editing: Boolean = false,
    val badges: Map<String, Int> = emptyMap(),
    val loadIssue: String? = null,
    val prefs: Prefs = Prefs(),
    /** False until the config has been read, so first-run screens do not flash before the saved prefs arrive. */
    val loaded: Boolean = false,
    /** Kind of every profile a catalog snapshot has arrived for. A user missing here has not been read yet. */
    val profiles: Map<Long, ProfileKind> = emptyMap(),
)
