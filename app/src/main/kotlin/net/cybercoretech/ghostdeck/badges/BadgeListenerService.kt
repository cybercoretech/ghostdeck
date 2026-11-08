package net.cybercoretech.ghostdeck.badges

import android.content.Context
import android.os.UserManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Feeds [BadgeCounts]. Bound by the system only once the user grants notification access in system settings.
 *
 * Privacy: this reads only the package, user, ongoing and group-summary flags and the badge permission of the
 * notification's channel. It never reads or stores notification text, titles or extras.
 *
 * `canShowBadge` comes from [NotificationListenerService.Ranking] (channel plus the app-level badge switch). Ranking is
 * available to every listener, so there is no `getNotificationChannels` call, no SecurityException path and no cache.
 */
class BadgeListenerService : NotificationListenerService() {
    override fun onListenerConnected() = refresh()
    override fun onListenerDisconnected() = BadgeCounts.recompute(emptyList())
    override fun onNotificationPosted(sbn: StatusBarNotification) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification) = refresh()

    private fun refresh() {
        val users = getSystemService(Context.USER_SERVICE) as UserManager
        val ranking = Ranking()
        val active = runCatching { activeNotifications }.getOrNull().orEmpty().map { sbn ->
            val known = currentRanking.getRanking(sbn.key, ranking)
            val canShow = !known || (ranking.canShowBadge() && (ranking.channel?.canShowBadge() ?: true))
            BadgeNotification(
                packageName = sbn.packageName,
                userSerial = users.getSerialNumberForUser(sbn.user),
                isOngoing = sbn.isOngoing,
                isGroupSummary = sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0,
                canShowBadge = canShow,
            )
        }
        BadgeCounts.recompute(active)
    }
}
