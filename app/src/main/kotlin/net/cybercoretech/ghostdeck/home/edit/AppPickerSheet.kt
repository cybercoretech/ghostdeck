package net.cybercoretech.ghostdeck.home.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.cybercoretech.ghostdeck.catalog.AppEntry
import net.cybercoretech.ghostdeck.catalog.ProfileKind
import net.cybercoretech.ghostdeck.home.AppImage
import net.cybercoretech.ghostdeck.home.rememberSheetAlpha
import net.cybercoretech.ghostdeck.icons.IconProvider
import net.cybercoretech.ghostdeck.model.AppKey
import net.cybercoretech.ghostdeck.ui.theme.GhostdeckFonts
import net.cybercoretech.ghostdeck.ui.theme.GlassSurface
import net.cybercoretech.ghostdeck.ui.theme.LocalTokens

private val ROW_ICON = 40.dp

/** The apps that match [query] (case-insensitive); hidden ones only when [showHidden]. */
internal fun pickerRows(apps: List<AppEntry>, hidden: Set<AppKey>, query: String, showHidden: Boolean): List<AppEntry> {
    val q = query.trim()
    return apps.filter { (showHidden || it.key !in hidden) && (q.isEmpty() || it.label.contains(q, ignoreCase = true)) }
}

private fun ProfileKind.badge(): String? = when (this) {
    ProfileKind.WORK -> "Work"
    ProfileKind.PRIVATE -> "Private"
    else -> null
}

/**
 * Bottom sheet listing [apps] (the catalog's visible apps) to add to the home screen. Tap adds,
 * the Hide button toggles the hidden state. [onPick] gets the entry; the caller places it.
 */
@Composable
fun AppPickerSheet(
    apps: List<AppEntry>,
    hidden: Set<AppKey>,
    icons: IconProvider,
    onPick: (AppEntry) -> Unit,
    onToggleHidden: (AppKey) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalTokens.current
    val alpha = rememberSheetAlpha()
    var query by remember { mutableStateOf("") }
    var showHidden by remember { mutableStateOf(false) }
    val rows = remember(apps, hidden, query, showHidden) { pickerRows(apps, hidden, query, showHidden) }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        GlassSurface(
            Modifier.statusBarsPadding().navigationBarsPadding().padding(12.dp).fillMaxWidth().fillMaxHeight(0.85f).testTag("app-picker")
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            alpha = alpha,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Add apps", color = tokens.text, fontFamily = GhostdeckFonts.display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = tokens.text, fontFamily = GhostdeckFonts.ui, fontSize = 16.sp),
                    cursorBrush = SolidColor(tokens.accent),
                    modifier = Modifier.fillMaxWidth().testTag("picker-search"),
                    decorationBox = { field ->
                        Box(
                            Modifier.fillMaxWidth().background(tokens.border.copy(alpha = 0.35f), RoundedCornerShape(12.dp)).padding(12.dp),
                        ) {
                            if (query.isEmpty()) Text("Search apps", color = tokens.muted, fontFamily = GhostdeckFonts.ui, fontSize = 16.sp)
                            field()
                        }
                    },
                )
                Row(
                    Modifier.fillMaxWidth().clickable { showHidden = !showHidden }.testTag("picker-show-hidden"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Show hidden", Modifier.weight(1f), color = tokens.text, fontFamily = GhostdeckFonts.ui, fontSize = 14.sp)
                    Switch(checked = showHidden, onCheckedChange = null)
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(rows, key = { "${it.key.userSerial}/${it.key.packageName}/${it.key.className}" }) { entry ->
                        PickerRow(entry, entry.key in hidden, icons, onPick, onToggleHidden)
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    entry: AppEntry,
    isHidden: Boolean,
    icons: IconProvider,
    onPick: (AppEntry) -> Unit,
    onToggleHidden: (AppKey) -> Unit,
) {
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().clickable { onPick(entry) }.padding(vertical = 4.dp).testTag("picker-row-${entry.key.packageName}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppImage(entry.key, ROW_ICON, icons)
        Text(
            entry.label, Modifier.weight(1f), color = if (isHidden) tokens.muted else tokens.text,
            fontFamily = GhostdeckFonts.ui, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        entry.profile.badge()?.let { badge ->
            Text(
                badge, Modifier.testTag("picker-badge-${entry.key.packageName}"),
                color = tokens.accentAlt, fontFamily = GhostdeckFonts.mono, fontSize = 11.sp,
            )
        }
        TextButton(onClick = { onToggleHidden(entry.key) }, Modifier.testTag("picker-hide-${entry.key.packageName}")) {
            Text(if (isHidden) "Unhide" else "Hide", color = tokens.accent, fontFamily = GhostdeckFonts.ui, fontSize = 13.sp)
        }
    }
}
