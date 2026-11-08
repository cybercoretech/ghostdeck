package net.cybercoretech.ghostdeck.catalog

import net.cybercoretech.ghostdeck.model.AppItem
import net.cybercoretech.ghostdeck.model.AppKey
import net.cybercoretech.ghostdeck.model.Cell
import net.cybercoretech.ghostdeck.model.Layout
import net.cybercoretech.ghostdeck.model.LayoutResult
import net.cybercoretech.ghostdeck.model.allAppKeys
import net.cybercoretech.ghostdeck.model.place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogStateTest {
    private fun entry(pkg: String, cls: String, label: String, user: Long = 0, kind: ProfileKind = ProfileKind.PERSONAL) =
        AppEntry(AppKey(pkg, cls, user), label, kind)

    private fun snapshot(user: Long, kind: ProfileKind, vararg e: AppEntry) =
        CatalogEvent.Snapshot(user, kind, e.toList())

    private fun layoutWith(vararg keys: AppKey): Layout {
        var l = Layout()
        keys.forEachIndexed { i, k ->
            l = (l.place(AppItem("i$i", k), 0, Cell(i * 2, 0)) as LayoutResult.Ok).layout
        }
        return l
    }

    @Test fun snapshotPopulates() {
        val s = CatalogState().reduce(snapshot(0, ProfileKind.PERSONAL, entry("a", "A", "Alpha"), entry("b", "B", "Beta")))
        assertEquals(2, s.entries.size)
        assertEquals(ProfileKind.PERSONAL, s.profiles[0])
    }

    @Test fun snapshotReplacesOnlyThatUsersEntries() {
        val s = CatalogState()
            .reduce(snapshot(0, ProfileKind.PERSONAL, entry("a", "A", "Alpha")))
            .reduce(snapshot(10, ProfileKind.WORK, entry("w", "W", "Work", 10, ProfileKind.WORK)))
            .reduce(snapshot(0, ProfileKind.PERSONAL, entry("b", "B", "Beta")))
        assertEquals(setOf(AppKey("b", "B", 0), AppKey("w", "W", 10)), s.entries.keys)
    }

    @Test fun upsertReplacesPackageEntries() {
        val s = CatalogState()
            .reduce(snapshot(0, ProfileKind.PERSONAL, entry("p", "Old", "Old"), entry("q", "Q", "Q")))
            .reduce(CatalogEvent.PackageUpserted(0, "p", listOf(entry("p", "New", "New"))))
        assertEquals(setOf(AppKey("p", "New", 0), AppKey("q", "Q", 0)), s.entries.keys)
    }

    @Test fun removeDropsEntries() {
        val s = CatalogState()
            .reduce(snapshot(0, ProfileKind.PERSONAL, entry("p", "A", "A"), entry("p", "B", "B"), entry("q", "Q", "Q")))
            .reduce(CatalogEvent.PackageRemoved(0, "p"))
        assertEquals(setOf(AppKey("q", "Q", 0)), s.entries.keys)
    }

    @Test fun profileUnavailableHidesButKeepsEntries() {
        val s = CatalogState()
            .reduce(snapshot(0, ProfileKind.PERSONAL, entry("a", "A", "Alpha")))
            .reduce(snapshot(10, ProfileKind.PRIVATE, entry("s", "S", "Secret", 10, ProfileKind.PRIVATE)))
            .reduce(CatalogEvent.ProfileAvailability(10, false))
        assertEquals(listOf("Alpha"), s.visibleApps().map { it.label })
        assertEquals(2, s.entries.size)
        val back = s.reduce(CatalogEvent.ProfileAvailability(10, true))
        assertEquals(2, back.visibleApps().size)
    }

    @Test fun visibleAppsSortedCaseInsensitively() {
        val s = CatalogState().reduce(
            snapshot(0, ProfileKind.PERSONAL, entry("c", "C", "charlie"), entry("a", "A", "Alpha"), entry("b", "B", "bravo")),
        )
        assertEquals(listOf("Alpha", "bravo", "charlie"), s.visibleApps().map { it.label })
    }

    @Test fun profileUnavailableKeepsLayoutItems() {
        val state = CatalogState().reduce(snapshot(10, ProfileKind.WORK, entry("w", "W", "Work", 10, ProfileKind.WORK)))
        val layout = layoutWith(AppKey("w", "W", 10))
        val event = CatalogEvent.ProfileAvailability(10, false)
        assertEquals(layout, layout.reconcile(event, state.reduce(event)))
    }

    @Test fun packageRemovedRemovesLayoutItems() {
        val keep = AppKey("q", "Q", 0)
        val layout = layoutWith(AppKey("p", "A", 0), AppKey("p", "B", 0), AppKey("p", "A", 10), keep)
        val event = CatalogEvent.PackageRemoved(0, "p")
        val out = layout.reconcile(event, CatalogState().reduce(event))
        assertEquals(setOf(keep, AppKey("p", "A", 10)), out.allAppKeys())
    }

    @Test fun staleComponentRemapsToSoleLauncherActivity() {
        val state = CatalogState().reduce(snapshot(0, ProfileKind.PERSONAL, entry("p", "New", "P")))
        val out = layoutWith(AppKey("p", "Old", 0)).resolveStale(state)
        assertEquals(setOf(AppKey("p", "New", 0)), out.allAppKeys())
    }

    @Test fun staleWithMultipleCandidatesLeftAlone() {
        val state = CatalogState().reduce(snapshot(0, ProfileKind.PERSONAL, entry("p", "N1", "P1"), entry("p", "N2", "P2")))
        val layout = layoutWith(AppKey("p", "Old", 0))
        assertEquals(layout, layout.resolveStale(state))
    }

    @Test fun staleOnUnavailableUserLeftAlone() {
        val state = CatalogState()
            .reduce(snapshot(10, ProfileKind.WORK, entry("p", "New", "P", 10, ProfileKind.WORK)))
            .reduce(CatalogEvent.ProfileAvailability(10, false))
        val layout = layoutWith(AppKey("p", "Old", 10))
        assertTrue(layout == layout.resolveStale(state))
    }
}
