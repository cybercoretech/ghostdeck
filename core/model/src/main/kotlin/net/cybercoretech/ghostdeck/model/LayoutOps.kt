package net.cybercoretech.ghostdeck.model

enum class LayoutError { OutOfBounds, Occupied, NestedFolder, DockFull, UnknownItem, InvalidTarget }

sealed interface LayoutResult {
    data class Ok(val layout: Layout) : LayoutResult
    data class Err(val error: LayoutError) : LayoutResult
}

sealed interface Target {
    data class Grid(val page: Int, val cell: Cell) : Target
    data class DockSlot(val index: Int) : Target
    data class IntoFolder(val folderId: String) : Target
}

// ---- geometry -------------------------------------------------------------

private fun Cell.overlaps(o: Cell): Boolean =
    x < o.x + o.spanX && o.x < x + spanX && y < o.y + o.spanY && o.y < y + spanY

private fun GridSize.contains(c: Cell): Boolean =
    c.spanX > 0 && c.spanY > 0 && c.x >= 0 && c.y >= 0 &&
        c.x + c.spanX <= cols * SUB && c.y + c.spanY <= rows * SUB

private fun <T> List<T>.replaceAt(i: Int, v: T): List<T> = mapIndexed { k, e -> if (k == i) v else e }

// ---- internal rewriting ---------------------------------------------------

/** A folder with one child collapses into it; with none it disappears. */
private fun collapse(folder: FolderItem, rest: List<Item>): Item? = when (rest.size) {
    0 -> null
    1 -> rest[0]
    else -> folder.copy(children = rest)
}

/** Drops every item matching [drop], at top level and inside folders. */
private fun rewrite(item: Item, drop: (Item) -> Boolean): Item? {
    if (drop(item)) return null
    if (item !is FolderItem) return item
    val rest = item.children.filterNot(drop)
    return if (rest.size == item.children.size) item else collapse(item, rest)
}

private fun Layout.dropWhere(drop: (Item) -> Boolean): Layout = copy(
    pages = pages.map { page ->
        Page(page.placed.mapNotNull { p -> rewrite(p.item, drop)?.let { Placed(it, p.cell) } })
    },
    dock = Dock(dock.items.map { it?.let { i -> rewrite(i, drop) } }),
)

private fun Layout.mapItems(f: (Item) -> Item): Layout {
    fun one(item: Item): Item {
        val m = f(item)
        return if (m is FolderItem) m.copy(children = m.children.map(f)) else m
    }
    return copy(
        pages = pages.map { page -> Page(page.placed.map { Placed(one(it.item), it.cell) }) },
        dock = Dock(dock.items.map { it?.let(::one) }),
    )
}

private fun Layout.withPage(page: Int, placed: List<Placed>): Layout =
    if (page == pages.size) copy(pages = pages + Page(placed))
    else copy(pages = pages.replaceAt(page, Page(placed)))

// ---- queries --------------------------------------------------------------

fun Layout.findItem(itemId: String): Item? {
    fun search(item: Item?): Item? = when {
        item == null -> null
        item.id == itemId -> item
        item is FolderItem -> item.children.firstOrNull { it.id == itemId }
        else -> null
    }
    pages.forEach { page -> page.placed.forEach { search(it.item)?.let { found -> return found } } }
    dock.items.forEach { search(it)?.let { found -> return found } }
    return null
}

fun Layout.allAppKeys(): Set<AppKey> {
    val keys = mutableSetOf<AppKey>()
    fun visit(item: Item?) {
        when (item) {
            is AppItem -> keys += item.app
            is FolderItem -> item.children.forEach(::visit)
            else -> Unit
        }
    }
    pages.forEach { p -> p.placed.forEach { visit(it.item) } }
    dock.items.forEach(::visit)
    return keys
}

fun Layout.firstFreeCell(spanX: Int = SUB, spanY: Int = SUB): Pair<Int, Cell> {
    for ((pi, page) in pages.withIndex()) {
        var y = 0
        while (y + spanY <= grid.rows * SUB) {
            var x = 0
            while (x + spanX <= grid.cols * SUB) {
                val c = Cell(x, y, spanX, spanY)
                if (page.placed.none { it.cell.overlaps(c) }) return pi to c
                x += SUB
            }
            y += SUB
        }
    }
    return pages.size to Cell(0, 0, spanX, spanY)
}

// ---- mutations ------------------------------------------------------------

fun Layout.place(item: Item, page: Int, cell: Cell): LayoutResult {
    if (page < 0 || page > pages.size) return LayoutResult.Err(LayoutError.OutOfBounds)
    if (!grid.contains(cell)) return LayoutResult.Err(LayoutError.OutOfBounds)
    val existing = if (page == pages.size) emptyList() else pages[page].placed
    if (existing.any { it.cell.overlaps(cell) }) return LayoutResult.Err(LayoutError.Occupied)
    return LayoutResult.Ok(withPage(page, existing + Placed(item, cell)))
}

fun Layout.remove(itemId: String): Layout = dropWhere { it.id == itemId }

fun Layout.removeApp(key: AppKey): Layout = dropWhere { it is AppItem && it.app == key }

private class Detached(val layout: Layout, val item: Item)

private fun Layout.detach(itemId: String): Detached? {
    val item = findItem(itemId) ?: return null
    return Detached(remove(itemId), item)
}

fun Layout.move(itemId: String, to: Target): LayoutResult {
    val detached = detach(itemId) ?: return LayoutResult.Err(LayoutError.UnknownItem)
    val item = detached.item
    val base = detached.layout
    return when (to) {
        is Target.Grid -> base.place(item, to.page, to.cell)
        is Target.DockSlot -> {
            val slots = base.dock.items
            when {
                to.index !in slots.indices -> LayoutResult.Err(LayoutError.OutOfBounds)
                slots[to.index] != null -> LayoutResult.Err(LayoutError.Occupied)
                else -> LayoutResult.Ok(base.copy(dock = Dock(slots.replaceAt(to.index, item))))
            }
        }
        is Target.IntoFolder -> {
            if (item is FolderItem) return LayoutResult.Err(LayoutError.NestedFolder)
            val folder = findItem(to.folderId) ?: return LayoutResult.Err(LayoutError.UnknownItem)
            if (folder !is FolderItem) return LayoutResult.Err(LayoutError.InvalidTarget)
            if (folder.children.any { it.id == itemId }) return LayoutResult.Ok(this)
            val updated = base.mapItems {
                if (it.id == to.folderId && it is FolderItem) it.copy(children = it.children + item) else it
            }
            LayoutResult.Ok(updated)
        }
    }
}

fun Layout.createFolder(draggedId: String, ontoId: String, name: String = "Folder"): LayoutResult {
    val dragged = findItem(draggedId)
    val onto = findItem(ontoId)
    if (dragged == null || onto == null) return LayoutResult.Err(LayoutError.UnknownItem)
    if (draggedId == ontoId) return LayoutResult.Err(LayoutError.InvalidTarget)
    if (dragged is FolderItem) return LayoutResult.Err(LayoutError.NestedFolder)
    if (onto is FolderItem) return LayoutResult.Err(LayoutError.InvalidTarget)
    // onto must be top-level (grid or dock), not a folder child
    val topLevel = pages.any { p -> p.placed.any { it.item.id == ontoId } } ||
        dock.items.any { it?.id == ontoId }
    if (!topLevel) return LayoutResult.Err(LayoutError.InvalidTarget)

    val folder = FolderItem(ItemIds.new(), name, listOf(onto, dragged))
    val base = remove(draggedId)
    val result = base.copy(
        pages = base.pages.map { page ->
            Page(page.placed.map { if (it.item.id == ontoId) Placed(folder, it.cell) else it })
        },
        dock = Dock(base.dock.items.map { if (it?.id == ontoId) folder else it }),
    )
    return LayoutResult.Ok(result)
}

fun Layout.resize(newGrid: GridSize): Layout {
    fun fits(c: Cell) = newGrid.contains(c)
    val all = pages.flatMapIndexed { pi, page -> page.placed.map { pi to it } }
    val (kept, overflow) = all.partition { fits(it.second.cell) }
    val keptPages = pages.indices.map { pi -> Page(kept.filter { it.first == pi }.map { it.second }) }
    var out = copy(grid = newGrid, pages = keptPages)
    val ordered = overflow.sortedWith(
        compareBy({ it.first }, { it.second.cell.y }, { it.second.cell.x }),
    )
    for ((_, p) in ordered) {
        val spanX = minOf(p.cell.spanX, newGrid.cols * SUB)
        val spanY = minOf(p.cell.spanY, newGrid.rows * SUB)
        val (page, cell) = out.firstFreeCell(spanX, spanY)
        val existing = if (page == out.pages.size) emptyList() else out.pages[page].placed
        out = out.withPage(page, existing + Placed(p.item, cell))
    }
    return out
}

fun Layout.setSwipeUp(itemId: String, action: ItemAction?): LayoutResult {
    val item = findItem(itemId) ?: return LayoutResult.Err(LayoutError.UnknownItem)
    if (item !is AppItem && item !is ShortcutItem) return LayoutResult.Err(LayoutError.InvalidTarget)
    return LayoutResult.Ok(
        mapItems {
            when {
                it.id != itemId -> it
                it is AppItem -> it.copy(swipeUp = action)
                it is ShortcutItem -> it.copy(swipeUp = action)
                else -> it
            }
        },
    )
}

fun Layout.remapApp(stale: AppKey, replacement: AppKey): Layout =
    mapItems { if (it is AppItem && it.app == stale) it.copy(app = replacement) else it }
        .let { l -> if (stale in l.hidden) l.copy(hidden = l.hidden - stale + replacement) else l }

fun Layout.dropEmptyTrailingPages(): Layout {
    var end = pages.size
    while (end > 1 && pages[end - 1].placed.isEmpty()) end--
    return if (end == pages.size) this else copy(pages = pages.take(end))
}
