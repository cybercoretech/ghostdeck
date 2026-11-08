package net.cybercoretech.ghostdeck.catalog

import kotlinx.coroutines.flow.Flow
import net.cybercoretech.ghostdeck.model.AppKey
import java.text.Collator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

sealed interface CatalogEvent {
    data class Snapshot(val userSerial: Long, val profile: ProfileKind, val entries: List<AppEntry>) : CatalogEvent
    data class PackageUpserted(val userSerial: Long, val packageName: String, val entries: List<AppEntry>) : CatalogEvent
    data class PackageRemoved(val userSerial: Long, val packageName: String) : CatalogEvent
    /** Temporary (e.g. storage unmounted): entries are hidden, layout items are kept. */
    data class PackagesUnavailable(val userSerial: Long, val packageNames: List<String>) : CatalogEvent
    data class ProfileAvailability(val userSerial: Long, val available: Boolean) : CatalogEvent
}

/** Source of catalog events; implemented on Android by `AndroidAppCatalog`. */
interface AppCatalog {
    val events: Flow<CatalogEvent>
}

data class CatalogState(
    val entries: Map<AppKey, AppEntry> = emptyMap(),
    val unavailableUsers: Set<Long> = emptySet(),
    val profiles: Map<Long, ProfileKind> = emptyMap(),
) {
    fun reduce(event: CatalogEvent): CatalogState = when (event) {
        is CatalogEvent.Snapshot -> copy(
            entries = entries.filterKeys { it.userSerial != event.userSerial } + event.entries.associateBy { it.key },
            profiles = profiles + (event.userSerial to event.profile),
        )
        is CatalogEvent.PackageUpserted -> copy(
            entries = entries.withoutPackage(event.userSerial, event.packageName) + event.entries.associateBy { it.key },
        )
        is CatalogEvent.PackageRemoved -> copy(entries = entries.withoutPackage(event.userSerial, event.packageName))
        is CatalogEvent.PackagesUnavailable -> copy(
            entries = event.packageNames.fold(entries) { acc, pkg -> acc.withoutPackage(event.userSerial, pkg) },
        )
        is CatalogEvent.ProfileAvailability -> copy(
            unavailableUsers = if (event.available) unavailableUsers - event.userSerial else unavailableUsers + event.userSerial,
        )
    }

    /** Apps of available profiles, sorted by label ignoring case, in the order of [locale]. */
    fun visibleApps(locale: Locale = Locale.getDefault()): List<AppEntry> {
        val collator = collatorFor(locale)
        return entries.values
            .filter { it.key.userSerial !in unavailableUsers }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }
}

// Collators are costly to build and Collator.compare is thread-safe, so keep one per locale.
private val collators = ConcurrentHashMap<Locale, Collator>()

private fun collatorFor(locale: Locale): Collator =
    collators.getOrPut(locale) { Collator.getInstance(locale).apply { strength = Collator.SECONDARY } }

private fun Map<AppKey, AppEntry>.withoutPackage(userSerial: Long, packageName: String): Map<AppKey, AppEntry> =
    filterKeys { !(it.userSerial == userSerial && it.packageName == packageName) }
