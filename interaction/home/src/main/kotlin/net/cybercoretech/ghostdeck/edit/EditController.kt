package net.cybercoretech.ghostdeck.edit

import net.cybercoretech.ghostdeck.model.AppItem
import net.cybercoretech.ghostdeck.model.AppKey
import net.cybercoretech.ghostdeck.model.Cell
import net.cybercoretech.ghostdeck.model.FolderItem
import net.cybercoretech.ghostdeck.model.Item
import net.cybercoretech.ghostdeck.model.Layout
import net.cybercoretech.ghostdeck.model.LayoutError
import net.cybercoretech.ghostdeck.model.LayoutResult
import net.cybercoretech.ghostdeck.model.SUB
import net.cybercoretech.ghostdeck.model.Target
import net.cybercoretech.ghostdeck.model.createFolder
import net.cybercoretech.ghostdeck.model.dropEmptyTrailingPages
import net.cybercoretech.ghostdeck.model.findItem
import net.cybercoretech.ghostdeck.model.firstFreeCell
import net.cybercoretech.ghostdeck.model.move
import net.cybercoretech.ghostdeck.model.place
import net.cybercoretech.ghostdeck.model.remove
import net.cybercoretech.ghostdeck.model.removeApp

sealed interface DragSource {
    /** An item already on the home screen, in the dock or in a folder. */
    data class Existing(val itemId: String) : DragSource

    /** An item that is not placed yet, e.g. a shortcut dragged out of the item menu. */
    data class New(val item: Item) : DragSource
}

sealed interface DropZone {
    data class OnTarget(val target: Target) : DropZone
    data class OnItem(val itemId: String) : DropZone
    data object Remove : DropZone
    data object Uninstall : DropZone
    data object None : DropZone
}

sealed interface DropOutcome {
    data class Updated(val layout: Layout) : DropOutcome
    data class Rejected(val error: LayoutError) : DropOutcome
    data class UninstallRequested(val key: AppKey) : DropOutcome
    data object Cancelled : DropOutcome
}

/**
 * Pure drag and drop logic for edit mode. The layout only changes on a successful [drop];
 * [hover] evaluates the drop that would happen so the UI can show whether it is valid.
 * [canUninstall] lets the platform veto system apps.
 */
class EditController(initial: Layout, private val canUninstall: (AppKey) -> Boolean = { true }) {
    var layout: Layout = initial
        private set

    /** false: grid drops snap to whole cells; true: half cells (spec 6.1). The dock and folders are always whole-slot. */
    var fine: Boolean = false

    var dragging: DragSource? = null
        private set

    private var pending: DropOutcome = DropOutcome.Cancelled

    /** Adopts a layout that changed from outside (catalog events, a menu action) and ends any drag. */
    fun resetTo(layout: Layout) {
        this.layout = layout
        cancel()
    }

    fun startDrag(source: DragSource) {
        dragging = source
        pending = DropOutcome.Cancelled
    }

    /** True when dropping in [zone] right now would change something valid. */
    fun hover(zone: DropZone): Boolean {
        pending = evaluate(zone)
        return pending is DropOutcome.Updated || pending is DropOutcome.UninstallRequested
    }

    fun drop(): DropOutcome {
        val outcome = pending
        if (outcome is DropOutcome.Updated) layout = outcome.layout
        cancel()
        return outcome
    }

    fun cancel() {
        dragging = null
        pending = DropOutcome.Cancelled
    }

    /** Rounds raw half-cell coordinates down to whole cells unless [fine]. */
    fun snap(rawSubX: Int, rawSubY: Int): Pair<Int, Int> =
        if (fine) rawSubX to rawSubY else Math.floorDiv(rawSubX, SUB) * SUB to Math.floorDiv(rawSubY, SUB) * SUB

    private fun evaluate(zone: DropZone): DropOutcome {
        val source = dragging ?: return DropOutcome.Cancelled
        // A new item is staged on a trailing page so every drop reduces to moving an existing item.
        val (base, id, item) = when (source) {
            is DragSource.Existing -> {
                val found = layout.findItem(source.itemId) ?: return DropOutcome.Rejected(LayoutError.UnknownItem)
                Triple(layout, source.itemId, found)
            }
            is DragSource.New -> {
                val staged = layout.place(source.item, layout.pages.size, Cell(0, 0)) as? LayoutResult.Ok
                    ?: return DropOutcome.Rejected(LayoutError.OutOfBounds)
                Triple(staged.layout, source.item.id, source.item)
            }
        }
        val result: LayoutResult = when (zone) {
            DropZone.None -> return DropOutcome.Cancelled
            DropZone.Remove ->
                if (source is DragSource.Existing) LayoutResult.Ok(base.remove(id)) else return DropOutcome.Cancelled
            DropZone.Uninstall ->
                return if (item is AppItem && canUninstall(item.app)) DropOutcome.UninstallRequested(item.app) else DropOutcome.Cancelled
            is DropZone.OnTarget -> when (val t = zone.target) {
                is Target.Grid -> {
                    if (t.page > layout.pages.size) return DropOutcome.Rejected(LayoutError.OutOfBounds)
                    val (x, y) = snap(t.cell.x, t.cell.y)
                    base.move(id, t.copy(cell = t.cell.copy(x = x, y = y)))
                }
                else -> base.move(id, t)
            }
            is DropZone.OnItem ->
                if (base.findItem(zone.itemId) is FolderItem) base.move(id, Target.IntoFolder(zone.itemId))
                else base.createFolder(id, zone.itemId)
        }
        return when (result) {
            is LayoutResult.Ok -> DropOutcome.Updated(result.layout.dropEmptyTrailingPages())
            is LayoutResult.Err -> DropOutcome.Rejected(result.error)
        }
    }
}

/** Puts [item] in the first free whole cell (a new page if every page is full). */
fun Layout.placeFirstFree(item: Item): Layout {
    val (page, cell) = firstFreeCell()
    return (place(item, page, cell) as? LayoutResult.Ok)?.layout ?: this
}

/** Hiding also takes the app off the home screen; unhiding only lifts the flag (the user places it again). */
fun Layout.setHidden(key: AppKey, hide: Boolean): Layout =
    if (hide) removeApp(key).let { it.copy(hidden = it.hidden + key) } else copy(hidden = hidden - key)
