package net.cybercoretech.ghostdeck.badges

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The only facts about a notification that badges need. Never its text, title or extras. */
data class BadgeNotification(
    val packageName: String,
    val userSerial: Long,
    val isOngoing: Boolean,
    val isGroupSummary: Boolean,
    val canShowBadge: Boolean,
)

/** Active notification counts keyed `"$packageName/$userSerial"`. Empty until the user grants notification access. */
object BadgeCounts {
    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts

    /** False while the badges pref is off: the listener then skips all work. Set by the view model. */
    @get:JvmName("isWanted") @Volatile var wanted: Boolean = true
        set(value) {
            val turnedOn = value && !field
            field = value
            if (turnedOn) refreshHook?.invoke() // counts were dropped while off: read them again now
        }

    /** Set by the bound listener service so turning badges on does not wait for the next notification. */
    @Volatile var refreshHook: (() -> Unit)? = null

    fun key(packageName: String, userSerial: Long) = "$packageName/$userSerial"

    fun recompute(active: List<BadgeNotification>) {
        _counts.value = active
            .filter { it.canShowBadge && !it.isOngoing && !it.isGroupSummary }
            .groupingBy { key(it.packageName, it.userSerial) }
            .eachCount()
    }
}
