package net.cybercoretech.ghostdeck.home.edit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.floor
import kotlin.math.roundToInt
import net.cybercoretech.ghostdeck.edit.DropZone
import net.cybercoretech.ghostdeck.model.Cell
import net.cybercoretech.ghostdeck.model.GridSize
import net.cybercoretech.ghostdeck.model.SUB
import net.cybercoretech.ghostdeck.model.Target

/** Where things are on screen (root coordinates), reported by the tiles, grids, dock and edit bar. */
class DropGeometry {
    class Entry(val rect: Rect, val layer: Int)

    val items = HashMap<String, Entry>()
    val grids = HashMap<Int, Rect>()
    var dock: Rect? = null
    var dockSlots = 0
    var remove: Rect? = null
    var uninstall: Rect? = null

    /** The topmost tile under [p]; folder-sheet tiles sit on a higher layer than the home screen. */
    fun itemAt(p: Offset): String? =
        items.entries.filter { it.value.rect.contains(p) }.maxByOrNull { it.value.layer }?.key

    /**
     * The drop zone under [p]. Only the middle of a tile counts as "onto the item" so that neighbouring
     * cells stay reachable. In whole mode the raw cell is the one under the finger; in fine mode the
     * icon is centred on the finger and positioned to the nearest half cell.
     */
    fun zoneAt(p: Offset, draggedId: String?, grid: GridSize, fine: Boolean): DropZone {
        if (remove?.contains(p) == true) return DropZone.Remove
        if (uninstall?.contains(p) == true) return DropZone.Uninstall
        items.entries
            .filter { it.key != draggedId && it.value.rect.inner().contains(p) }
            .maxByOrNull { it.value.layer }
            ?.let { return DropZone.OnItem(it.key) }
        val d = dock
        if (d != null && dockSlots > 0 && d.contains(p)) {
            return DropZone.OnTarget(Target.DockSlot(((p.x - d.left) / d.width * dockSlots).toInt().coerceIn(0, dockSlots - 1)))
        }
        for ((page, r) in grids) {
            if (!r.contains(p)) continue
            val subX = (p.x - r.left) / (r.width / (grid.cols * SUB))
            val subY = (p.y - r.top) / (r.height / (grid.rows * SUB))
            val x = if (fine) (subX - SUB / 2f).roundToInt() else floor(subX).toInt()
            val y = if (fine) (subY - SUB / 2f).roundToInt() else floor(subY).toInt()
            return DropZone.OnTarget(
                Target.Grid(page, Cell(x.coerceIn(0, grid.cols * SUB - SUB), y.coerceIn(0, grid.rows * SUB - SUB))),
            )
        }
        return DropZone.None
    }

    /** On-screen rectangle of a grid drop, for the preview outline. */
    fun cellRect(page: Int, cell: Cell, grid: GridSize): Rect? {
        val r = grids[page] ?: return null
        val w = r.width / (grid.cols * SUB)
        val h = r.height / (grid.rows * SUB)
        return Rect(r.left + cell.x * w, r.top + cell.y * h, r.left + (cell.x + cell.spanX) * w, r.top + (cell.y + cell.spanY) * h)
    }

    private fun Rect.inner(): Rect = deflate(minOf(width, height) * 0.25f)
}
