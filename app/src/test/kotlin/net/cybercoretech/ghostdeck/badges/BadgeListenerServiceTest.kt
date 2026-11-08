package net.cybercoretech.ghostdeck.badges

import android.os.Process
import android.os.UserHandle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BadgeListenerServiceTest {
    internal class TestService : BadgeListenerService() {
        var active = listOf<Active>()
        val badgeAllowed = HashMap<String, Boolean>()
        var serialLookups = 0
        override fun activeNotificationFacts() = active
        override fun canShowBadge(key: String) = badgeAllowed[key] ?: true
        override fun lookupSerial(user: UserHandle): Long { serialLookups++; return 0 }
    }

    @Before fun reset() { BadgeCounts.wanted = true; BadgeCounts.recompute(emptyList()) }
    @After fun restore() { BadgeCounts.wanted = true; BadgeCounts.refreshHook = null; BadgeCounts.recompute(emptyList()) }

    private fun service() = Robolectric.setupService(TestService::class.java)
    private fun fact(key: String) = BadgeListenerService.Active("com.a", key, Process.myUserHandle(), isOngoing = false, isGroupSummary = false)

    @Test fun rankingUpdateRecomputesCounts() {
        val s = service()
        s.active = listOf(fact("k1"), fact("k2"))
        s.onNotificationRankingUpdate(null)
        assertEquals(2, BadgeCounts.counts.value["com.a/0"])
        s.badgeAllowed["k2"] = false // the user switched this channel's badge off
        s.onNotificationRankingUpdate(null)
        assertEquals(1, BadgeCounts.counts.value["com.a/0"])
    }

    @Test fun serialLookupIsCachedPerUser() {
        val s = service()
        s.active = listOf(fact("k1"))
        repeat(5) { s.onNotificationRankingUpdate(null) }
        assertEquals("five refreshes, one binder call", 1, s.serialLookups)
    }

    @Test fun nothingIsComputedWhileBadgesAreOff() {
        val s = service()
        s.active = listOf(fact("k1"))
        BadgeCounts.wanted = false
        s.onNotificationRankingUpdate(null)
        assertTrue(BadgeCounts.counts.value.isEmpty())
        assertEquals(0, s.serialLookups)
    }
}
