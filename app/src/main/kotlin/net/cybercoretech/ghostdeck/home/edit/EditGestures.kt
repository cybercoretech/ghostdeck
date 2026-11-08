package net.cybercoretech.ghostdeck.home.edit

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull
import net.cybercoretech.ghostdeck.edit.DragSource
import net.cybercoretech.ghostdeck.edit.DropOutcome
import net.cybercoretech.ghostdeck.model.GridSize
import net.cybercoretech.ghostdeck.model.Item

/** What the gesture layer needs from the screen; re-read on every gesture so the modifier never restarts. */
class EditCallbacks(
    /** Item long-press and drag are on (off while a menu or the picker is open). */
    val enabled: Boolean,
    val allowEmptyLongPress: Boolean,
    val grid: GridSize,
    /** `Prefs.fineGrid`: half-cell drops without the second finger. */
    val fineByDefault: Boolean,
    val resolve: (String) -> Item?,
    val onItemLongPress: (Item) -> Unit,
    val onDragStart: (Item) -> Unit,
    val onEmptyLongPress: () -> Unit,
    val onDragFinished: (DropOutcome) -> Unit,
)

/**
 * Long-press on an item opens its menu; moving on from there drags it. Long-press on empty space is the
 * single edit-mode trigger. Watches in the Initial pass so a long press can take the gesture from the
 * tile's own tap and the pager; holding a second finger while dragging switches to half-cell drops.
 */
fun Modifier.editGestures(session: EditSession, callbacks: State<EditCallbacks>): Modifier =
    pointerInput(session) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!callbacks.value.enabled) return@awaitEachGesture
            val hitId = session.geometry.itemAt(down.position)
            val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                endedOrMoved(down, viewConfiguration.touchSlop)
            }
            if (ended != null) return@awaitEachGesture // a tap or a swipe: not ours

            val cb = callbacks.value
            val item = hitId?.let(cb.resolve)
            if (item == null) {
                if (cb.allowEmptyLongPress) cb.onEmptyLongPress()
                return@awaitEachGesture
            }
            cb.onItemLongPress(item)
            try {
                var dragging = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    event.changes.forEach { it.consume() }
                    if (change == null || !change.pressed) {
                        if (dragging) callbacks.value.onDragFinished(session.finish())
                        break
                    }
                    if (!dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        dragging = true
                        callbacks.value.onDragStart(item)
                        session.start(DragSource.Existing(item.id), item, change.position)
                    }
                    if (dragging) {
                        val now = callbacks.value
                        session.move(change.position, now.fineByDefault || event.changes.count { it.pressed } > 1, now.grid)
                    }
                }
            } finally {
                if (session.isDragging) session.cancel()
            }
        }
    }

/** Returns when the pointer lifts, moves past the touch slop, or something else claimed it. */
private suspend fun AwaitPointerEventScope.endedOrMoved(down: PointerInputChange, slop: Float): Boolean {
    while (true) {
        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return true
        if (!change.pressed || change.isConsumed || (change.position - down.position).getDistance() > slop) return true
    }
}
