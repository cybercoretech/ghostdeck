package net.cybercoretech.ghostdeck.model

import kotlinx.serialization.Serializable

/** Sub-units per cell (spec 6.1). */
const val SUB = 2

/** Position and span in sub-units. */
@Serializable
data class Cell(val x: Int, val y: Int, val spanX: Int = SUB, val spanY: Int = SUB)

/** Whole-cell helper. */
fun cell(col: Int, row: Int, spanCols: Int = 1, spanRows: Int = 1): Cell =
    Cell(col * SUB, row * SUB, spanCols * SUB, spanRows * SUB)

val Cell.isWholeAligned: Boolean
    get() = x % SUB == 0 && y % SUB == 0 && spanX % SUB == 0 && spanY % SUB == 0

@Serializable
data class Placed(val item: Item, val cell: Cell)

@Serializable
data class Page(val placed: List<Placed> = emptyList())

@Serializable
data class GridSize(val cols: Int = 5, val rows: Int = 6) {
    init {
        require(cols in 3..8 && rows in 4..10) { "grid $cols x $rows out of range" }
    }
}

@Serializable
data class Dock(val items: List<Item?> = List(5) { null }) {
    init {
        require(items.size in 0..6) { "dock size ${items.size} out of range" }
    }
}

@Serializable
data class Layout(
    val grid: GridSize = GridSize(),
    val pages: List<Page> = listOf(Page()),
    val dock: Dock = Dock(),
    val hidden: Set<AppKey> = emptySet(),
)
