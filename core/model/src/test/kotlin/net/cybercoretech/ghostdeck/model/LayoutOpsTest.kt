package net.cybercoretech.ghostdeck.model

import net.cybercoretech.ghostdeck.model.LayoutError.*
import net.cybercoretech.ghostdeck.model.LayoutResult.Err
import net.cybercoretech.ghostdeck.model.LayoutResult.Ok
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutOpsTest {
    private fun key(n: String, user: Long = 0) = AppKey("p.$n", "C", user)
    private fun app(n: String, user: Long = 0) = AppItem(n, key(n, user))
    private fun ok(r: LayoutResult): Layout = (r as? Ok ?: error("expected Ok but was $r")).layout
    private fun cellOf(l: Layout, id: String): Cell? =
        l.pages.flatMap { it.placed }.firstOrNull { it.item.id == id }?.cell

    @Test fun placeRejectsOutOfBounds() { assertEquals(Err(OutOfBounds), Layout().place(app("a"), 0, cell(5, 0))) }
    @Test fun halfStepAtRightEdgeOutOfBounds() { assertEquals(Err(OutOfBounds), Layout().place(app("a"), 0, Cell(9, 0))) }
    @Test fun placeRejectsOccupied() {
        val l = ok(Layout().place(app("a"), 0, cell(0, 0)))
        assertEquals(Err(Occupied), l.place(app("b"), 0, cell(0, 0)))
    }
    @Test fun halfOffsetOverlapIsOccupied() {
        val l = ok(Layout().place(app("a"), 0, cell(0, 0)))
        assertEquals(Err(Occupied), l.place(app("b"), 0, Cell(1, 0)))
    }
    @Test fun halfOffsetBesideIsFree() {
        val l = ok(Layout().place(app("a"), 0, cell(0, 0)))
        assertTrue(l.place(app("b"), 0, Cell(2, 1)) is Ok)
    }
    @Test fun placeOnNextPageAppends() {
        assertEquals(2, ok(Layout().place(app("a"), 1, Cell(0, 0))).pages.size)
    }
    @Test fun placeBeyondNextPageOutOfBounds() {
        assertEquals(Err(OutOfBounds), Layout().place(app("a"), 2, Cell(0, 0)))
    }

    @Test fun resizeKeepsHalfOffsetsInBounds() {
        val l = ok(Layout().place(app("a"), 0, Cell(3, 1)))
        val l2 = ok(l.place(app("b"), 0, Cell(8, 0)))
        val r = l2.resize(GridSize(4, 5))
        assertEquals(Cell(3, 1), cellOf(r, "a"))
        val b = cellOf(r, "b")!!
        assertTrue(b.isWholeAligned)
        assertTrue(b.x + b.spanX <= 8 && b.y + b.spanY <= 10)
        assertEquals(setOf(key("a"), key("b")), r.allAppKeys())
    }

    @Test fun resizeReflowsOverflowWithoutLoss() {
        var l = Layout()
        for (r in 0 until 6) for (c in 0 until 5) l = ok(l.place(app("a$r$c"), 0, cell(c, r)))
        assertEquals(30, l.allAppKeys().size)
        val out = l.resize(GridSize(4, 5))
        assertEquals(30, out.allAppKeys().size)
        assertTrue(out.pages.size >= 2)
        out.pages.flatMap { it.placed }.forEach {
            assertTrue(it.cell.x + it.cell.spanX <= 8 && it.cell.y + it.cell.spanY <= 10)
        }
    }

    @Test fun swipeUpOnlyForAppsAndShortcuts() {
        val folder = FolderItem("f", "F", listOf(app("c1"), app("c2")))
        var l = ok(Layout().place(app("a"), 0, cell(0, 0)))
        l = ok(l.place(folder, 0, cell(1, 0)))
        val act = LaunchApp(key("z"))
        val a = ok(l.setSwipeUp("a", act))
        assertEquals(act, (a.findItem("a") as AppItem).swipeUp)
        assertEquals(Err(InvalidTarget), l.setSwipeUp("f", act))
        val child = ok(l.setSwipeUp("c1", ShowShortcuts))
        assertEquals(ShowShortcuts, (child.findItem("c1") as AppItem).swipeUp)
        assertEquals(Err(UnknownItem), l.setSwipeUp("nope", act))
        val cleared = ok(a.setSwipeUp("a", null))
        assertNull((cleared.findItem("a") as AppItem).swipeUp)
    }

    @Test fun dockSlotMoves() {
        val dock = Dock(listOf(app("d0"), null, app("d2"), null, null))
        var l = Layout(dock = dock)
        l = ok(l.place(app("g"), 0, cell(0, 0)))
        assertEquals(Err(Occupied), l.move("g", Target.DockSlot(2)))
        assertEquals(Err(OutOfBounds), l.move("g", Target.DockSlot(5)))
        val moved = ok(l.move("g", Target.DockSlot(1)))
        assertEquals("g", moved.dock.items[1]?.id)
        assertNull(cellOf(moved, "g"))
        assertThrows(IllegalArgumentException::class.java) { Dock(List(7) { null }) }
    }

    @Test fun moveWithinGridIgnoresSelf() {
        val l = ok(Layout().place(app("a"), 0, cell(0, 0)))
        val moved = ok(l.move("a", Target.Grid(0, Cell(1, 0))))
        assertEquals(Cell(1, 0), cellOf(moved, "a"))
    }

    @Test fun moveIntoFolder() {
        var l = ok(Layout().place(FolderItem("f", "F", listOf(app("c1"), app("c2"))), 0, cell(0, 0)))
        l = ok(l.place(app("a"), 0, cell(1, 0)))
        val m = ok(l.move("a", Target.IntoFolder("f")))
        assertEquals(listOf("c1", "c2", "a"), (m.findItem("f") as FolderItem).children.map { it.id })
        assertNull(cellOf(m, "a"))
        assertEquals(Err(InvalidTarget), l.move("a", Target.IntoFolder("a")))
    }

    @Test fun folderCannotNest() {
        var l = ok(Layout().place(FolderItem("f1", "A", listOf(app("a"), app("b"))), 0, cell(0, 0)))
        l = ok(l.place(FolderItem("f2", "B", listOf(app("c"), app("d"))), 0, cell(1, 0)))
        assertEquals(Err(NestedFolder), l.move("f1", Target.IntoFolder("f2")))
    }

    @Test fun removeAppCollapsesFolder() {
        val l = ok(Layout().place(FolderItem("f", "F", listOf(app("a"), app("b"))), 0, cell(1, 1)))
        val r = l.removeApp(key("a"))
        assertEquals(app("b"), r.pages[0].placed.single().item)
        assertEquals(cell(1, 1), r.pages[0].placed.single().cell)
    }

    @Test fun removeAppRemovesEmptyFolderAndDockEntries() {
        var l = Layout(dock = Dock(listOf(app("a"), null)))
        l = ok(l.place(app("a2"), 0, cell(0, 0)))
        val r = l.removeApp(key("a"))
        assertNull(r.dock.items[0])
        assertEquals(1, r.pages[0].placed.size)
    }

    @Test fun removeAppOnlyMatchesSameUser() {
        var l = ok(Layout().place(AppItem("u0", AppKey("p", "C", 0)), 0, cell(0, 0)))
        l = ok(l.place(AppItem("u10", AppKey("p", "C", 10)), 0, cell(1, 0)))
        val r = l.removeApp(AppKey("p", "C", 10))
        assertNotNull(r.findItem("u0"))
        assertNull(r.findItem("u10"))
    }

    @Test fun gridBoundsValidated() {
        assertThrows(IllegalArgumentException::class.java) { GridSize(9, 6) }
        assertThrows(IllegalArgumentException::class.java) { GridSize(5, 3) }
    }

    @Test fun createFolderFromTwoApps() {
        var l = ok(Layout().place(app("onto"), 0, cell(0, 0)))
        l = ok(l.place(app("dragged"), 0, cell(2, 2)))
        val r = ok(l.createFolder("dragged", "onto", "Games"))
        val placed = r.pages[0].placed
        assertEquals(1, placed.size)
        val f = placed.single().item as FolderItem
        assertEquals("Games", f.name)
        assertEquals(listOf("onto", "dragged"), f.children.map { it.id })
        assertEquals(cell(0, 0), placed.single().cell)
        assertTrue(r.place(app("x"), 0, cell(2, 2)) is Ok)
    }

    @Test fun createFolderRejectsFolders() {
        var l = ok(Layout().place(FolderItem("f", "F", listOf(app("a"), app("b"))), 0, cell(0, 0)))
        l = ok(l.place(app("c"), 0, cell(1, 0)))
        assertEquals(Err(NestedFolder), l.createFolder("f", "c"))
        assertEquals(Err(InvalidTarget), l.createFolder("c", "f"))
    }

    @Test fun firstFreeCellSkipsOccupiedAndAppendsPage() {
        var l = ok(Layout().place(app("a"), 0, cell(0, 0)))
        assertEquals(0 to cell(1, 0), l.firstFreeCell())
        l = Layout(grid = GridSize(3, 4))
        for (r in 0 until 4) for (c in 0 until 3) l = ok(l.place(app("a$r$c"), 0, cell(c, r)))
        assertEquals(1 to Cell(0, 0), l.firstFreeCell())
    }

    @Test fun remapAppUpdatesItemsAndHidden() {
        val stale = key("old")
        val fresh = key("new")
        var l = Layout(hidden = setOf(stale))
        l = ok(l.place(AppItem("a", stale), 0, cell(0, 0)))
        val r = l.remapApp(stale, fresh)
        assertEquals(setOf(fresh), r.allAppKeys())
        assertEquals(setOf(fresh), r.hidden)
    }

    @Test fun dropEmptyTrailingPagesKeepsOne() {
        val l = Layout(pages = listOf(Page(), Page(), Page()))
        assertEquals(1, l.dropEmptyTrailingPages().pages.size)
        val l2 = ok(Layout(pages = listOf(Page(), Page())).place(app("a"), 0, cell(0, 0)))
        assertEquals(1, l2.dropEmptyTrailingPages().pages.size)
        val l3 = ok(Layout(pages = listOf(Page(), Page())).place(app("a"), 1, cell(0, 0)))
        assertEquals(2, l3.dropEmptyTrailingPages().pages.size)
    }

    @Test fun removeDeletesAnywhere() {
        var l = ok(Layout().place(app("a"), 0, cell(0, 0)))
        l = ok(l.place(FolderItem("f", "F", listOf(app("c1"), app("c2"), app("c3"))), 0, cell(1, 0)))
        assertNull(l.remove("a").findItem("a"))
        assertEquals(2, (l.remove("c1").findItem("f") as FolderItem).children.size)
    }

    @Test fun configHelpers() {
        val d1 = Deck("d1", "One", "x", layout = Layout())
        val d2 = Deck("d2", "Two", "y", layout = Layout())
        val c = GhostdeckConfig(decks = listOf(d1, d2), activeDeckId = "d2")
        assertEquals(d2, c.activeDeck())
        val c2 = c.updateActiveLayout { ok(it.place(app("a"), 0, cell(0, 0))) }
        assertEquals(setOf(key("a")), c2.activeDeck().layout.allAppKeys())
        assertTrue(c2.decks[0].layout.allAppKeys().isEmpty())
    }
}
