package net.cybercoretech.ghostdeck.badges

import android.content.Context
import android.os.UserHandle
import android.os.UserManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentHashMap

/**
 * Feeds [BadgeCounts]. Bound by the system only once the user grants notification access in system settings.
 *
 * Privacy: this reads only the package, user, ongoing and group-summary flags and the badge permission of the
 * notification's channel. It never reads or stores notification text, titles or extras.
 *
 * `canShowBadge` comes from [NotificationListenerService.Ranking] (channel plus the app-level badge switch). Ranking is
 * available to every listener, so there is no `getNotificationChannels` call and no SecurityException path.
 * A channel's badge switch changes arrive as ranking updates, so those recompute the counts too.
 */
open class BadgeListenerService : NotificationListenerService() {
    private val serials = ConcurrentHashMap<UserHandle, Long>()

    override fun onListenerConnected() {
        BadgeCounts.refreshHook = ::refresh
        refresh()
    }

    override fun onListenerDisconnected() {
        BadgeCounts.refreshHook = null
        BadgeCounts.recompute(emptyList())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification) = refresh()
    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) = refresh()

    /** The few facts read from one notification (see the privacy note above). */
    internal data class Active(val packageName: String, val key: String, val user: UserHandle, val isOngoing: Boolean, val isGroupSummary: Boolean)

    /** The active notifications; empty if the listener is not bound right now. */
    internal open fun activeNotificationFacts(): List<Active> = runCatching { activeNotifications }.getOrNull().orEmpty().map { sbn ->
        Active(
            sbn.packageName, sbn.key, sbn.user, sbn.isOngoing,
            isGroupSummary = sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0,
        )
    }

    /** Whether the notification's channel and app allow a badge. A notification the ranking does not know counts as allowed. */
    internal open fun canShowBadge(key: String): Boolean {
        val ranking = Ranking()
        return !currentRanking.getRanking(key, ranking) || (ranking.canShowBadge() && (ranking.channel?.canShowBadge() ?: true))
    }

    /** The serial number of [user]; a binder call, so asked once per user. */
    private fun serialOf(user: UserHandle): Long = serials.getOrPut(user) { lookupSerial(user) }

    internal open fun lookupSerial(user: UserHandle): Long =
        (getSystemService(Context.USER_SERVICE) as UserManager).getSerialNumberForUser(user)

    private fun refresh() {
        // Badges are off in Settings: do no work (and keep nothing) until they are turned on again.
        if (!BadgeCounts.wanted) {
            BadgeCounts.recompute(emptyList())
            return
        }
        BadgeCounts.recompute(
            activeNotificationFacts().map { n ->
                BadgeNotification(
                    packageName = n.packageName,
                    userSerial = serialOf(n.user),
                    isOngoing = n.isOngoing,
                    isGroupSummary = n.isGroupSummary,
                    canShowBadge = canShowBadge(n.key),
                )
            },
        )
    }
}
