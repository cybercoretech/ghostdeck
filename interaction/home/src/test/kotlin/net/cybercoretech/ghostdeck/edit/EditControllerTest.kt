package net.cybercoretech.ghostdeck.edit

import net.cybercoretech.ghostdeck.model.AppItem
import net.cybercoretech.ghostdeck.model.AppKey
import net.cybercoretech.ghostdeck.model.Cell
import net.cybercoretech.ghostdeck.model.Dock
import net.cybercoretech.ghostdeck.model.FolderItem
import net.cybercoretech.ghostdeck.model.Layout
import net.cybercoretech.ghostdeck.model.LayoutError
import net.cybercoretech.ghostdeck.model.Page
import net.cybercoretech.ghostdeck.model.Placed
import net.cybercoretech.ghostdeck.model.ShortcutItem
import net.cybercoretech.ghostdeck.model.Target
import net.cybercoretech.ghostdeck.model.cell
import net.cybercoretech.ghostdeck.model.findItem
import net.cybercoretech.ghostdeck.model.removeApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditControllerTest {
    private fun key(n: String) = AppKey("com.$n", "com.$n.Main", 0)
    private fun app(n: String) = AppItem(n, key(n))

    private val base = Layout(
        pages = listOf(Page(listOf(Placed(app("a"), cell(0, 0)), Placed(app("b"), cell(1, 0))))),
        dock = Dock(List(5) { null }),
    )

    private fun EditController.dropOn(zone: DropZone): DropOutcome { hover(zone); return drop() }
    private fun EditController.layoutAfter(outcome: DropOutcome): Layout = (outcome as DropOutcome.Updated).layout

    @Test fun dragToEmptyCellMoves() {
        val c = EditController(base)
        c.startDrag(DragSource.Existing("a"))
        assertTrue(c.hover(DropZone.OnTarget(Target.Grid(0, cell(3, 2)))))
        val out = c.drop() as DropOutcome.Updated
        assertEquals(cell(3, 2), out.layout.pages[0].placed.single { it.item.id == "a" }.cell)
        assertEquals(out.layout, c.layout)
    }

    @Test fun dragOntoAppCreatesFolder() {
        val c = EditController(base)
        c.startDrag(DragSource.Existing("a"))
        assertTrue(c.hover(DropZone.OnItem("b")))
        val out = c.layoutAfter(c.drop())
        val folder = out.pages[0].placed.single().item as FolderItem
        assertEquals(setOf("a", "b"), folder.children.map { it.id }.toSet())
    }

    @Test fun dragOntoFolderMovesIn() {
        val folder = FolderItem("f", "F", listOf(app("x"), app("y")))
        val layout = base.copy(pages = listOf(Page(base.pages[0].placed + Placed(folder, cell(2, 0)))))
        val c = EditController(layout)
        c.startDrag(DragSource.Existing("a"))
        val out = c.layoutAfter(c.dropOn(DropZone.OnItem("f")))
        assertEquals(3, (out.findItem("f") as FolderItem).children.size)
    }

    @Test fun dragFolderOntoFolderRejectedNested() {
        val f1 = FolderItem("f1", "F1", listOf(app("x"), app("y")))
        val f2 = FolderItem("f2", "F2", listOf(app("p"), app("q")))
        val layout = Layout(pages = listOf(Page(listOf(Placed(f1, cell(0, 0)), Placed(f2, cell(1, 0))))))
        val c = EditController(layout)
        c.startDrag(DragSource.Existing("f1"))
        assertFalse(c.hover(DropZone.OnItem("f2")))
        assertEquals(DropOutcome.Rejected(LayoutError.NestedFolder), c.drop())
        assertEquals(layout, c.layout)
    }

    @Test fun dropOnOccupiedRejected() {
        val c = EditController(base)
        c.startDrag(DragSource.Existing("a"))
        assertFalse(c.hover(DropZone.OnTarget(Target.Grid(0, cell(1, 0)))))
        assertEquals(DropOutcome.Rejected(LayoutError.Occupied), c.drop())
    }

    @Test fun newItemFromPickerPlaces() {
        val c = EditController(base)
        val shortcut = ShortcutItem("s1", "com.chrome", "new-tab", 0, "New tab")
        c.startDrag(DragSource.New(shortcut))
        assertTrue(c.hover(DropZone.OnTarget(Target.Grid(0, cell(4, 5)))))
        val out = c.layoutAfter(c.drop())
        assertEquals(cell(4, 5), out.pages[0].placed.single { it.item.id == "s1" }.cell)
        assertEquals("no stray page is left behind", 1, out.pages.size)
    }

    @Test fun newItemOntoDockSlotAndOntoAppFolder() {
        val c = EditController(base)
        c.startDrag(DragSource.New(app("n")))
        val docked = c.layoutAfter(c.dropOn(DropZone.OnTarget(Target.DockSlot(2))))
        assertEquals("n", docked.dock.items[2]?.id)

        val c2 = EditController(base)
        c2.startDrag(DragSource.New(app("n")))
        val folded = c2.layoutAfter(c2.dropOn(DropZone.OnItem("a")))
        assertEquals(setOf("a", "n"), (folded.pages[0].placed.single { it.item is FolderItem }.item as FolderItem).children.map { it.id }.toSet())
    }

    @Test fun newItemOntoNewTrailingPage() {
        val c = EditController(base)
        c.startDrag(DragSource.New(app("n")))
        val out = c.layoutAfter(c.dropOn(DropZone.OnTarget(Target.Grid(1, cell(0, 0)))))
        assertEquals(2, out.pages.size)
        assertEquals("n", out.pages[1].placed.single().item.id)
    }

    @Test fun uninstallOnlyForApps() {
        val shortcut = ShortcutItem("s", "com.chrome", "new-tab", 0, "New tab")
        val layout = base.copy(pages = listOf(Page(base.pages[0].placed + Placed(shortcut, cell(2, 0)))))
        val c = EditController(layout)
        c.startDrag(DragSource.Existing("s"))
        assertFalse(c.hover(DropZone.Uninstall))

        c.cancel()
        c.startDrag(DragSource.Existing("a"))
        assertTrue(c.hover(DropZone.Uninstall))
        assertEquals(DropOutcome.UninstallRequested(key("a")), c.drop())
        assertEquals("layout waits for the package to really go", layout, c.layout)
    }

    @Test fun uninstallRefusedWhenPolicySaysSystemApp() {
        val c = EditController(base, canUninstall = { it != key("a") })
        c.startDrag(DragSource.Existing("a"))
        assertFalse(c.hover(DropZone.Uninstall))
        c.cancel()
        c.startDrag(DragSource.Existing("b"))
        assertTrue(c.hover(DropZone.Uninstall))
    }

    @Test fun removeDropsExistingItem() {
        val c = EditController(base)
        c.startDrag(DragSource.Existing("a"))
        val out = c.layoutAfter(c.dropOn(DropZone.Remove))
        assertNull(out.findItem("a"))
        c.startDrag(DragSource.New(app("n")))
        assertFalse("nothing to remove from a picker drag", c.hover(DropZone.Remove))
    }

    @Test fun cancelRestoresLayout() {
        val c = EditController(base)
        c.startDrag(DragSource.Existing("a"))
        c.hover(DropZone.OnTarget(Target.Grid(0, cell(3, 2))))
        c.cancel()
        assertEquals(base, c.layout)
        assertNull(c.dragging)
        assertEquals(DropOutcome.Cancelled, c.drop())
    }

    @Test fun dropOnNothingIsCancelled() {
        val c = EditController(base)
        c.startDrag(DragSource.Existing("a"))
        assertFalse(c.hover(DropZone.None))
        assertEquals(DropOutcome.Cancelled, c.drop())
        assertEquals(base, c.layout)
    }

    @Test fun emptyTrailingPageDroppedAfterMove() {
        val layout = base.copy(pages = base.pages + Page(listOf(Placed(app("z"), cell(0, 0)))))
        val c = EditController(layout)
        c.startDrag(DragSource.Existing("z"))
        val out = c.layoutAfter(c.dropOn(DropZone.OnTarget(Target.Grid(0, cell(3, 3)))))
        assertEquals(1, out.pages.size)
    }

    @Test fun wholeModeSnapsToCells() {
        val c = EditController(base)
        assertEquals(2 to 0, c.snap(3, 1))
        c.startDrag(DragSource.Existing("a"))
        c.hover(DropZone.OnTarget(Target.Grid(0, Cell(5, 3))))
        val out = c.layoutAfter(c.drop())
        assertEquals(Cell(4, 2), out.pages[0].placed.single { it.item.id == "a" }.cell)
    }

    @Test fun fineModeAllowsHalfCellDrop() {
        val c = EditController(base)
        c.fine = true
        assertEquals(3 to 1, c.snap(3, 1))
        c.startDrag(DragSource.Existing("a"))
        val out = c.layoutAfter(c.dropOn(DropZone.OnTarget(Target.Grid(0, Cell(5, 3)))))
        assertEquals(Cell(5, 3), out.pages[0].placed.single { it.item.id == "a" }.cell)
    }

    @Test fun dockAndFolderIgnoreFine() {
        val folder = FolderItem("f", "F", listOf(app("x"), app("y")))
        val layout = base.copy(pages = listOf(Page(base.pages[0].placed + Placed(folder, cell(2, 0)))))
        for (fine in listOf(false, true)) {
            val c = EditController(layout)
            c.fine = fine
            c.startDrag(DragSource.Existing("a"))
            assertEquals("a", c.layoutAfter(c.dropOn(DropZone.OnTarget(Target.DockSlot(1)))).dock.items[1]?.id)
            c.startDrag(DragSource.Existing("b"))
            assertTrue(c.hover(DropZone.OnTarget(Target.IntoFolder("f"))))
            assertEquals(3, (c.layoutAfter(c.drop()).findItem("f") as FolderItem).children.size)
        }
    }

    @Test fun placeFirstFreeUsesNextEmptyCell() {
        val out = base.placeFirstFree(app("n"))
        assertEquals(cell(2, 0), out.pages[0].placed.single { it.item.id == "n" }.cell)
    }

    @Test fun hidingTakesAppOffHomeAndUnhidingKeepsItOff() {
        val hiddenLayout = base.setHidden(key("a"), true)
        assertNull(hiddenLayout.findItem("a"))
        assertEquals(setOf(key("a")), hiddenLayout.hidden)
        val back = hiddenLayout.setHidden(key("a"), false)
        assertTrue(back.hidden.isEmpty())
        assertNull(back.findItem("a"))
    }

    @Test fun pendingTransformReappliesTheDropToTheCurrentLayout() {
        val c = EditController(base)
        c.startDrag(DragSource.Existing("a"))
        assertTrue(c.hover(DropZone.OnTarget(Target.Grid(0, cell(3, 2)))))
        val commit = c.pendingTransform()
        val current = base.removeApp(key("b"))
        val out = commit(current)
        assertNull("b stays removed", out.findItem("b"))
        assertEquals(cell(3, 2), out.pages[0].placed.single { it.item.id == "a" }.cell)
    }

    @Test fun pendingTransformLeavesLayoutAloneWhenDraggedItemIsGone() {
        val c = EditController(base)
        c.startDrag(DragSource.Existing("a"))
        c.hover(DropZone.OnTarget(Target.Grid(0, cell(3, 2))))
        val commit = c.pendingTransform()
        val current = base.removeApp(key("a"))
        assertEquals(current, commit(current))
    }

    @Test fun pendingTransformWithoutValidHoverChangesNothing() {
        val c = EditController(base)
        c.startDrag(DragSource.Existing("a"))
        c.hover(DropZone.OnTarget(Target.Grid(0, cell(1, 0))))
        assertEquals(base, c.pendingTransform()(base))
    }
}
