package net.cybercoretech.ghostdeck.home

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import net.cybercoretech.ghostdeck.model.GridSize
import net.cybercoretech.ghostdeck.model.Page
import net.cybercoretech.ghostdeck.model.SUB

private val MAX_ICON = 56.dp
private const val ICON_FRACTION = 0.6f

/** Places items in sub-units: one sub-unit is half a cell, so half-offset items sit between columns. */
@Composable
fun PageGrid(page: Page, grid: GridSize, env: TileEnv, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().testTag("page-grid")) {
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
