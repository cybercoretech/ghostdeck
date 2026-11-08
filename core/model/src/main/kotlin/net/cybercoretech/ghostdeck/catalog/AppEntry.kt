package net.cybercoretech.ghostdeck.catalog

import net.cybercoretech.ghostdeck.model.AppKey

enum class ProfileKind { PERSONAL, WORK, PRIVATE, OTHER }

data class AppEntry(val key: AppKey, val label: String, val profile: ProfileKind)
