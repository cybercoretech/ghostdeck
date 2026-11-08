package net.cybercoretech.ghostdeck.badges

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BadgeCountsTest {
    @Before fun reset() = BadgeCounts.recompute(emptyList())

    private fun n(
        pkg: String, user: Long = 0, ongoing: Boolean = false, summary: Boolean = false, canShow: Boolean = true,
    ) = BadgeNotification(pkg, user, ongoing, summary, canShow)

    @Test fun countsPerPackageAndUser() {
        BadgeCounts.recompute(listOf(n("com.a"), n("com.a"), n("com.a", user = 10), n("com.b")))
        assertEquals(mapOf("com.a/0" to 2, "com.a/10" to 1, "com.b/0" to 1), BadgeCounts.counts.value)
    }

    @Test fun ignoresOngoingAndGroupSummaries() {
        BadgeCounts.recompute(listOf(n("com.a", ongoing = true), n("com.a", summary = true), n("com.a")))
        assertEquals(mapOf("com.a/0" to 1), BadgeCounts.counts.value)
    }

    @Test fun respectsChannelCanShowBadge() {
        BadgeCounts.recompute(listOf(n("com.a", canShow = false), n("com.b")))
        assertEquals(mapOf("com.b/0" to 1), BadgeCounts.counts.value)
    }

    @Test fun emptyWhenNoneActive() {
        BadgeCounts.recompute(listOf(n("com.a")))
        BadgeCounts.recompute(emptyList())
        assertTrue(BadgeCounts.counts.value.isEmpty())
    }
}
