package net.cybercoretech.ghostdeck.home

import net.cybercoretech.ghostdeck.catalog.AppEntry
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
)
