package net.cybercoretech.ghostdeck.home.edit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.cybercoretech.ghostdeck.edit.DragSource
import net.cybercoretech.ghostdeck.edit.DropOutcome
import net.cybercoretech.ghostdeck.edit.DropZone
import net.cybercoretech.ghostdeck.edit.EditController
import net.cybercoretech.ghostdeck.home.ItemTile
import net.cybercoretech.ghostdeck.home.TileEnv
import net.cybercoretech.ghostdeck.model.AppItem
import net.cybercoretech.ghostdeck.model.AppKey
import net.cybercoretech.ghostdeck.model.GridSize
import net.cybercoretech.ghostdeck.model.Item
import net.cybercoretech.ghostdeck.model.Layout
import net.cybercoretech.ghostdeck.model.SUB
import net.cybercoretech.ghostdeck.model.Target
import net.cybercoretech.ghostdeck.ui.theme.GhostdeckFonts
import net.cybercoretech.ghostdeck.ui.theme.GlassSurface
import net.cybercoretech.ghostdeck.ui.theme.LocalTokens

private val BAR_HEIGHT = 56.dp
private val GHOST_SIZE = 56.dp
private const val PREVIEW_ALPHA = 0.18f

/** The edit-mode drag state: the pure [EditController], the live finger position and what is under it. */
@Stable
class EditSession(layout: Layout, private val canUninstall: (AppKey) -> Boolean) {
    val geometry = DropGeometry()
    private val controller = EditController(layout, canUninstall)

    var dragItem by mutableStateOf<Item?>(null)
        private set
    var pointer by mutableStateOf(Offset.Zero)
        private set
    var zone by mutableStateOf<DropZone>(DropZone.None)
        private set
    var valid by mutableStateOf(false)
        private set
    var fine by mutableStateOf(false)
        private set

    val isDragging: Boolean get() = dragItem != null

    /** Whether the dragged item may be uninstalled (an app that is not a system app). */
    val uninstallable: Boolean get() = (dragItem as? AppItem)?.let { canUninstall(it.app) } == true

    /** Adopts the layout the screen is showing; an active drag keeps its own snapshot until it ends. */
    fun sync(layout: Layout) { if (!isDragging) controller.resetTo(layout) }

    fun start(source: DragSource, item: Item, at: Offset) {
        controller.startDrag(source)
        dragItem = item
        pointer = at
        zone = DropZone.None
        valid = false
    }

    fun move(at: Offset, fine: Boolean, grid: GridSize) {
        val item = dragItem ?: return
        pointer = at
        this.fine = fine
        controller.fine = fine
        zone = geometry.zoneAt(at, item.id, grid, fine)
        valid = controller.hover(zone)
    }

    /** The last finished drop as an edit of whatever layout is current when it is applied. */
    var lastCommit: (Layout) -> Layout = { it }
        private set

    fun finish(): DropOutcome {
        lastCommit = controller.pendingTransform()
        val outcome = controller.drop()
        clear()
        return outcome
    }

    fun cancel() {
        controller.cancel()
        clear()
    }

    private fun clear() {
        dragItem = null
        zone = DropZone.None
        valid = false
        fine = false
    }
}

/** Top bar of edit mode: the Remove and Uninstall drop targets, "+ Apps" and "Done". */
@Composable
fun EditBar(
    session: EditSession,
    onAddApps: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onSettings: () -> Unit = {},
) {
    val tokens = LocalTokens.current
    val geometry = session.geometry
    DisposableEffect(geometry) { onDispose { geometry.remove = null; geometry.uninstall = null } }
    Row(
        modifier.fillMaxWidth().height(BAR_HEIGHT).padding(horizontal = 12.dp, vertical = 4.dp)
            .background(tokens.glass.copy(alpha = tokens.glassAlpha), RoundedCornerShape(18.dp)).padding(horizontal = 4.dp).testTag("edit-bar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DropTarget("Remove", "drop-remove", session.zone == DropZone.Remove, enabled = true, session.isDragging) { geometry.remove = it }
        DropTarget(
            "Uninstall", "drop-uninstall", session.zone == DropZone.Uninstall,
            enabled = !session.isDragging || session.uninstallable, session.isDragging,
        ) { geometry.uninstall = it }
        Box(Modifier.weight(1f))
        BarText("Settings", "edit-settings", tokens.muted, onSettings)
        BarText("+ Apps", "edit-add-apps", tokens.accent, onAddApps)
        BarText("Done", "edit-done", tokens.text, onDone)
    }
}

@Composable
private fun BarText(label: String, tag: String, color: Color, onClick: () -> Unit) {
    Text(
        label, Modifier.heightIn(min = MIN_TOUCH).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 12.dp).testTag(tag),
        color = color, fontFamily = GhostdeckFonts.ui, fontSize = 14.sp,
    )
}

@Composable
private fun DropTarget(
    label: String,
    tag: String,
    hovered: Boolean,
    enabled: Boolean,
    dragging: Boolean,
    onBounds: (androidx.compose.ui.geometry.Rect) -> Unit,
) {
    val tokens = LocalTokens.current
    val shape = RoundedCornerShape(14.dp)
    GlassSurface(
        Modifier.padding(start = 4.dp, end = 4.dp).height(40.dp).alpha(if (enabled) 1f else 0.35f).testTag(tag)
            .onGloballyPositioned { onBounds(it.boundsInRoot()) }
            .then(if (hovered) Modifier.border(BorderStroke(2.dp, tokens.error), shape) else Modifier),
        shape = shape,
    ) {
        Box(Modifier.fillMaxHeight().padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            Text(
                label, color = if (dragging && enabled) tokens.error else tokens.muted,
                fontFamily = GhostdeckFonts.ui, fontSize = 13.sp,
            )
        }
    }
}

/** The dragged icon under the finger, and an outline where a grid drop would land (accent: valid, red: refused). */
@Composable
fun DragLayer(session: EditSession, env: TileEnv, grid: GridSize) {
    val item = session.dragItem ?: return
    val tokens = LocalTokens.current
    val density = LocalDensity.current
    val ghostPx = with(density) { GHOST_SIZE.toPx() }
    (session.zone as? DropZone.OnTarget)?.let { zone ->
        val t = zone.target as? Target.Grid ?: return@let
        val (x, y) = if (session.fine) t.cell.x to t.cell.y else Math.floorDiv(t.cell.x, SUB) * SUB to Math.floorDiv(t.cell.y, SUB) * SUB
        session.geometry.cellRect(t.page, t.cell.copy(x = x, y = y), grid)?.let { r ->
            val color = if (session.valid) tokens.accent else tokens.error
            Box(
                Modifier.offset { IntOffset(r.left.toInt(), r.top.toInt()) }
                    .size(with(density) { r.width.toDp() }, with(density) { r.height.toDp() })
                    .background(color.copy(alpha = PREVIEW_ALPHA), RoundedCornerShape(16.dp))
                    .border(1.dp, color, RoundedCornerShape(16.dp))
                    .testTag("drop-preview"),
            )
        }
    }
    Box(
        Modifier.offset { IntOffset((session.pointer.x - ghostPx / 2).toInt(), (session.pointer.y - ghostPx / 2).toInt()) }
            .size(GHOST_SIZE).alpha(0.9f).clearAndSetSemantics { }.testTag("drag-ghost"),
    ) {
        ItemTile(item, env.copy(geometry = null, draggingId = null), GHOST_SIZE, showLabel = false, index = 0, modifier = Modifier.fillMaxSize())
    }
}
