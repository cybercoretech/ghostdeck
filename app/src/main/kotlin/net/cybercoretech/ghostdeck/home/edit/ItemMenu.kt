package net.cybercoretech.ghostdeck.home.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.cybercoretech.ghostdeck.catalog.AppEntry
import net.cybercoretech.ghostdeck.home.rememberSheetAlpha
import net.cybercoretech.ghostdeck.model.AppItem
import net.cybercoretech.ghostdeck.model.FolderItem
import net.cybercoretech.ghostdeck.model.Item
import net.cybercoretech.ghostdeck.model.ItemAction
import net.cybercoretech.ghostdeck.model.LaunchApp
import net.cybercoretech.ghostdeck.model.LaunchShortcut
import net.cybercoretech.ghostdeck.model.ShortcutItem
import net.cybercoretech.ghostdeck.model.ShowShortcuts
import net.cybercoretech.ghostdeck.platform.AppShortcut
import net.cybercoretech.ghostdeck.ui.theme.GhostdeckFonts
import net.cybercoretech.ghostdeck.ui.theme.GlassSurface
import net.cybercoretech.ghostdeck.ui.theme.LocalTokens

private val LIST_MAX_HEIGHT: Dp = 360.dp

/**
 * The long-press menu of a home item: the app's shortcuts (rows can be dragged onto the home screen),
 * App info, Remove, Hide and the swipe-up action picker. While a shortcut is being dragged [dragging]
 * keeps the menu composed (so the drag gesture survives) but invisible.
 */
@Composable
fun ItemMenu(
    item: Item,
    shortcuts: List<AppShortcut>,
    apps: List<AppEntry>,
    dragging: Boolean,
    startInSwipePicker: Boolean,
    onLaunchShortcut: (AppShortcut) -> Unit,
    onShortcutDragStart: (AppShortcut, Offset) -> Unit,
    onShortcutDrag: (Offset) -> Unit,
    onShortcutDragEnd: () -> Unit,
    onAppInfo: () -> Unit,
    onRemove: () -> Unit,
    onHide: () -> Unit,
    onSwipeAction: (ItemAction?) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalTokens.current
    val alpha = rememberSheetAlpha()
    var picking by remember { mutableStateOf(startInSwipePicker) }
    Box(
        Modifier.fillMaxSize().alpha(if (dragging) 0f else 1f).background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            Modifier.padding(24.dp).fillMaxWidth().testTag("item-menu")
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            alpha = alpha,
        ) {
            if (picking) {
                SwipeActionPicker(
                    owner = item, apps = apps, shortcuts = shortcuts,
                    onChoose = { onSwipeAction(it); onDismiss() },
                    onBack = { picking = false },
                )
            } else {
                Column(Modifier.padding(vertical = 12.dp)) {
                    Text(
                        menuTitle(item), Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = tokens.text, fontFamily = GhostdeckFonts.display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp,
                    )
                    if (item is AppItem) shortcuts.take(MAX_SHORTCUTS).forEach { shortcut ->
                        ShortcutRow(shortcut, onLaunchShortcut, onShortcutDragStart, onShortcutDrag, onShortcutDragEnd)
                    }
                    if (item is AppItem) MenuRow("App info", "menu-app-info", onAppInfo)
                    if (item is AppItem || item is ShortcutItem) MenuRow("Swipe-up action…", "menu-swipe", { picking = true })
                    if (item is AppItem) MenuRow("Hide", "menu-hide", onHide)
                    MenuRow("Remove", "menu-remove", onRemove, danger = true)
                }
            }
        }
    }
}

private const val MAX_SHORTCUTS = 5

/** Minimum touch target (spec section 13). */
internal val MIN_TOUCH: Dp = 48.dp

private fun menuTitle(item: Item): String = when (item) {
    is AppItem -> item.customLabel ?: item.app.packageName.substringAfterLast('.').replaceFirstChar(Char::uppercase)
    is ShortcutItem -> item.label
    is FolderItem -> item.name
}

@Composable
private fun MenuRow(label: String, tag: String, onClick: () -> Unit, danger: Boolean = false) {
    val tokens = LocalTokens.current
    Text(
        label,
        Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp).testTag(tag),
        color = if (danger) tokens.error else tokens.text, fontFamily = GhostdeckFonts.ui, fontSize = 15.sp,
    )
}

@Composable
private fun ShortcutRow(
    shortcut: AppShortcut,
    onLaunch: (AppShortcut) -> Unit,
    onDragStart: (AppShortcut, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    val tokens = LocalTokens.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    Text(
        shortcut.label,
        Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH)
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(shortcut) {
                detectDragGestures(
                    onDragStart = { onDragStart(shortcut, origin + it) },
                    onDrag = { change, _ -> change.consume(); onDrag(origin + change.position) },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                )
            }
            .clickable { onLaunch(shortcut) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("menu-shortcut-${shortcut.id}"),
        color = tokens.accentAlt, fontFamily = GhostdeckFonts.ui, fontSize = 15.sp,
    )
}

/**
 * Chooses what swiping up on [owner] does: nothing, open the owner's shortcut list (apps only),
 * one of the owner's shortcuts, or launch another app. [onChoose] gets `null` for "None".
 */
@Composable
fun SwipeActionPicker(
    owner: Item,
    apps: List<AppEntry>,
    shortcuts: List<AppShortcut>,
    onChoose: (ItemAction?) -> Unit,
    onBack: () -> Unit,
) {
    val tokens = LocalTokens.current
    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "Swipe-up action", Modifier.padding(horizontal = 16.dp, vertical = 8.dp).clickable(onClick = onBack).testTag("swipe-back"),
            color = tokens.text, fontFamily = GhostdeckFonts.display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp,
        )
        LazyColumn(Modifier.heightIn(max = LIST_MAX_HEIGHT)) {
            item { MenuRow("None", "swipe-none", { onChoose(null) }) }
            if (owner is AppItem) item { MenuRow("Show shortcuts", "swipe-show-shortcuts", { onChoose(ShowShortcuts) }) }
            items(shortcuts, key = { "s-${it.id}" }) { s ->
                MenuRow(s.label, "swipe-shortcut-${s.id}", { onChoose(LaunchShortcut(s.packageName, s.id, s.userSerial)) })
            }
            item {
                Text(
                    "Open app", Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                    color = tokens.muted, fontFamily = GhostdeckFonts.mono, fontSize = 11.sp,
                )
            }
            items(apps, key = { "a-${it.key.userSerial}/${it.key.packageName}/${it.key.className}" }) { entry ->
                MenuRow(entry.label, "swipe-app-${entry.key.packageName}", { onChoose(LaunchApp(entry.key)) })
            }
        }
    }
}
