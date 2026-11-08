package net.cybercoretech.ghostdeck.home

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import net.cybercoretech.ghostdeck.model.GridSize
import net.cybercoretech.ghostdeck.model.Page
import net.cybercoretech.ghostdeck.model.SUB

private val MAX_ICON = 56.dp
private const val ICON_FRACTION = 0.6f

/** Places items in sub-units: one sub-unit is half a cell, so half-offset items sit between columns. */
@Composable
fun PageGrid(page: Page, grid: GridSize, env: TileEnv, modifier: Modifier = Modifier, pageIndex: Int = 0) {
    val geometry = env.geometry
    if (geometry != null) DisposableEffect(pageIndex) { onDispose { geometry.grids.remove(pageIndex) } }
    BoxWithConstraints(
        modifier.fillMaxSize().testTag("page-grid")
            .then(if (env.gridBounds) Modifier.drawBehind { drawCellOutlines(grid) } else Modifier).then(
            if (geometry == null) Modifier else Modifier.onGloballyPositioned { geometry.grids[pageIndex] = it.boundsInRoot() },
        ),
    ) {
        val subW = maxWidth / (grid.cols * SUB)
        val subH = maxHeight / (grid.rows * SUB)
        page.placed.forEachIndexed { index, placed ->
            val c = placed.cell
            val w = subW * c.spanX
            val h = subH * c.spanY
            ItemTile(
                item = placed.item, env = env, iconSize = minOf(w * ICON_FRACTION, h * ICON_FRACTION, MAX_ICON),
                showLabel = true, index = index,
                modifier = Modifier.offset(subW * c.x, subH * c.y).size(w, h),
            )
        }
    }
}

/** Labs debug aid: a hairline rectangle around each whole cell. */
private fun DrawScope.drawCellOutlines(grid: GridSize) {
    val w = size.width / grid.cols
    val h = size.height / grid.rows
    for (c in 0 until grid.cols) for (r in 0 until grid.rows) {
        drawRect(Color(0xFFFF4DA6), Offset(c * w, r * h), Size(w, h), style = Stroke(1f))
    }
}
