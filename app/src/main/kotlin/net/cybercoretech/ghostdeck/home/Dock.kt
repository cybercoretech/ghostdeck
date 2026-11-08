package net.cybercoretech.ghostdeck.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.cybercoretech.ghostdeck.model.Dock
import net.cybercoretech.ghostdeck.ui.theme.GlassSurface

private val DOCK_ICON = 48.dp

/** Whole-slot dock on a glass strip. Draws nothing for a zero-slot dock. */
@Composable
fun Dock(dock: Dock, env: TileEnv, modifier: Modifier = Modifier) {
    if (dock.items.isEmpty()) return
    GlassSurface(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 4.dp)) {
            dock.items.forEachIndexed { index, item -> Slot(index) {
                if (item != null) ItemTile(item, env, DOCK_ICON, showLabel = false, index = index, modifier = Modifier.fillMaxSize())
            } }
        }
    }
}

@Composable
private fun RowScope.Slot(index: Int, content: @Composable () -> Unit) {
    Box(Modifier.weight(1f).fillMaxSize()) { content() }
}
