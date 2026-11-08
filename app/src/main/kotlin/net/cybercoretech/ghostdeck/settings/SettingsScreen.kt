package net.cybercoretech.ghostdeck.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.cybercoretech.ghostdeck.R
import net.cybercoretech.ghostdeck.catalog.AppEntry
import net.cybercoretech.ghostdeck.icons.IconPack
import net.cybercoretech.ghostdeck.model.AppKey
import net.cybercoretech.ghostdeck.model.GridSize
import net.cybercoretech.ghostdeck.model.Layout
import net.cybercoretech.ghostdeck.model.Prefs
import net.cybercoretech.ghostdeck.theme.ThemePack
import net.cybercoretech.ghostdeck.ui.components.GhostButton
import net.cybercoretech.ghostdeck.ui.theme.GhostdeckFonts
import net.cybercoretech.ghostdeck.ui.theme.GhostdeckTheme
import net.cybercoretech.ghostdeck.ui.theme.GlassSurface
import net.cybercoretech.ghostdeck.ui.theme.LocalTokens
import net.cybercoretech.ghostdeck.ui.theme.tokensFor

private const val PAGE_MAIN = 0
private const val PAGE_ABOUT = 1
private const val PAGE_LABS = 2

private val COLS = 3..8
private val ROWS = 4..10
private val DOCK = 0..6

/**
 * The M1 settings. Everything is a callback so the screen holds no state of its own beyond which page is showing.
 * Back leaves a sub-page first, then calls [onClose].
 */
@Composable
fun SettingsScreen(
    pack: ThemePack,
    themes: List<ThemePack>,
    iconPacks: List<IconPack>,
    prefs: Prefs,
    layout: Layout,
    apps: Map<AppKey, AppEntry>,
    versionName: String,
    notificationAccessGranted: Boolean,
    labsUnlock: LabsUnlock,
    onPrefs: ((Prefs) -> Prefs) -> Unit,
    onGrid: (GridSize) -> Unit,
    onDockSlots: (Int) -> Unit,
    onUnhide: (AppKey) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onLabsUnlocked: () -> Unit,
    onExportDiagnostics: () -> Unit,
    onClose: () -> Unit,
) {
    GhostdeckTheme(pack) {
        val tokens = LocalTokens.current
        var page by rememberSaveable { mutableIntStateOf(PAGE_MAIN) }
        BackHandler { if (page == PAGE_MAIN) onClose() else page = PAGE_MAIN }
        Column(Modifier.fillMaxSize().background(tokens.ground).windowInsetsPadding(WindowInsets.systemBars).testTag("settings-screen")) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                GhostButton("Back", { if (page == PAGE_MAIN) onClose() else page = PAGE_MAIN }, tag = "settings-back")
                Text(
                    when (page) { PAGE_ABOUT -> "About & Privacy"; PAGE_LABS -> "Labs"; else -> "Settings" },
                    color = tokens.text, fontFamily = GhostdeckFonts.display, fontSize = 22.sp,
                )
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                when (page) {
                    PAGE_ABOUT -> AboutPage(versionName) { if (labsUnlock.onVersionTap()) onLabsUnlocked() }
                    PAGE_LABS -> LabsScreen(
                        prefs = prefs, onPrefs = onPrefs, onExportDiagnostics = onExportDiagnostics,
                        onLock = {
                            labsUnlock.lock()
                            onPrefs { it.copy(labsUnlocked = false, debugGridBounds = false) }
                            page = PAGE_MAIN
                        },
                    )
                    else -> MainPage(
                        themes, iconPacks, prefs, layout, apps, notificationAccessGranted,
                        onPrefs, onGrid, onDockSlots, onUnhide, onOpenNotificationAccess,
                        onAbout = { page = PAGE_ABOUT }, onLabs = { page = PAGE_LABS },
                    )
                }
            }
        }
    }
}

@Composable
private fun MainPage(
    themes: List<ThemePack>,
    iconPacks: List<IconPack>,
    prefs: Prefs,
    layout: Layout,
    apps: Map<AppKey, AppEntry>,
    notificationAccessGranted: Boolean,
    onPrefs: ((Prefs) -> Prefs) -> Unit,
    onGrid: (GridSize) -> Unit,
    onDockSlots: (Int) -> Unit,
    onUnhide: (AppKey) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onAbout: () -> Unit,
    onLabs: () -> Unit,
) {
    SectionTitle("Theme")
    themes.forEach { p -> ThemeOption(p, selected = p.id == prefs.themePack) { onPrefs { it.copy(themePack = p.id) } } }

    SectionTitle("Home")
    ToggleRow("Fine positioning", "Place icons on half-cell steps. Long-press still snaps to whole cells when off.", prefs.fineGrid, "setting-fine-grid") {
        onPrefs { p -> p.copy(fineGrid = it) }
    }
    ToggleRow("Swipe marks", "Show a small dot under icons that have a swipe-up action.", prefs.swipeMarks, "setting-swipe-marks") {
        onPrefs { p -> p.copy(swipeMarks = it) }
    }
    StepperRow("Grid columns", layout.grid.cols, COLS, "grid-cols") { onGrid(GridSize(it, layout.grid.rows)) }
    StepperRow("Grid rows", layout.grid.rows, ROWS, "grid-rows") { onGrid(GridSize(layout.grid.cols, it)) }
    StepperRow("Dock slots", layout.dock.items.size, DOCK, "dock-slots") { onDockSlots(it) }

    SectionTitle("Icons")
    ChoiceRow("System default", "iconpack-system", selected = prefs.iconPack == null) { onPrefs { it.copy(iconPack = null) } }
    iconPacks.forEach { ip ->
        ChoiceRow(ip.label, "iconpack-${ip.packageName}", selected = prefs.iconPack == ip.packageName) { onPrefs { it.copy(iconPack = ip.packageName) } }
    }

    SectionTitle("Notifications")
    ToggleRow(
        "Notification dots",
        "Needs notification access so Ghostdeck can see which apps have notifications. It only counts them; nothing leaves the device.",
        prefs.badges, "setting-badges",
    ) { on ->
        onPrefs { it.copy(badges = on) }
        if (on && !notificationAccessGranted) onOpenNotificationAccess()
    }

    SectionTitle("Hidden apps")
    if (layout.hidden.isEmpty()) Muted("No hidden apps.")
    layout.hidden.sortedBy { apps[it]?.label?.lowercase() ?: it.packageName }.forEach { key ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val name = apps[key]?.label ?: key.packageName
            BodyText(name, Modifier.weight(1f))
            GhostButton("Unhide", { onUnhide(key) }, tag = "unhide-${key.packageName}-${key.userSerial}", contentDescription = "Unhide $name")
        }
    }

    SectionTitle("About")
    NavRow("About & Privacy", "about-privacy", onAbout)
    if (prefs.labsUnlocked) NavRow("Labs", "labs-row", onLabs)
}

@Composable
private fun AboutPage(versionName: String, onVersionTap: () -> Unit) {
    val tokens = LocalTokens.current
    Text(stringResource(R.string.charter_title), color = tokens.accent, fontFamily = GhostdeckFonts.display, fontSize = 20.sp)
    stringArrayResource(R.array.charter_lines).forEachIndexed { i, line ->
        Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${i + 1}", color = tokens.accentAlt, fontFamily = GhostdeckFonts.mono, fontSize = 14.sp)
            BodyText(line)
        }
    }
    Spacer(Modifier.size(12.dp))
    // Seven quick taps here open Labs (spec 10a.7); nothing on screen hints at it.
    Text(
        "Version $versionName",
        Modifier.defaultMinSize(minHeight = 48.dp).clickable(onClick = onVersionTap).padding(vertical = 12.dp).testTag("about-version"),
        color = tokens.text, fontFamily = GhostdeckFonts.mono, fontSize = 14.sp,
    )
    BodyText("Ghostdeck is free software under the GNU General Public License, version 3 or later (GPL-3.0-or-later). You may use, study, share and change it; the full licence text is in the source repository.")
    Muted("Bundled fonts: Space Grotesk, Inter and JetBrains Mono, under the SIL Open Font License 1.1.")
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(), Modifier.padding(top = 20.dp, bottom = 4.dp),
        color = LocalTokens.current.accentAlt, fontFamily = GhostdeckFonts.mono, fontSize = 12.sp,
    )
}

@Composable
internal fun BodyText(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = LocalTokens.current.text, fontFamily = GhostdeckFonts.ui, fontSize = 15.sp, lineHeight = 21.sp)
}

@Composable
internal fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = LocalTokens.current.muted, fontFamily = GhostdeckFonts.ui, fontSize = 13.sp, lineHeight = 18.sp)
}

@Composable
internal fun ToggleRow(title: String, subtitle: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).testTag(tag)
            .toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            BodyText(title)
            Muted(subtitle)
        }
        Switch(
            checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = tokens.accent, checkedThumbColor = tokens.ground),
        )
    }
}

@Composable
private fun StepperRow(title: String, value: Int, range: IntRange, tag: String, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        // Label and value are one TalkBack stop: "Grid columns, 5".
        BodyText(title, Modifier.weight(1f).semantics { contentDescription = "$title, $value" })
        GhostButton("-", { if (value - 1 in range) onChange(value - 1) }, tag = "$tag-dec", contentDescription = "Decrease ${title.lowercase()}")
        Text(
            "$value", Modifier.testTag("$tag-value").clearAndSetSemantics {},
            color = LocalTokens.current.text, fontFamily = GhostdeckFonts.mono, fontSize = 16.sp,
        )
        GhostButton("+", { if (value + 1 in range) onChange(value + 1) }, tag = "$tag-inc", contentDescription = "Increase ${title.lowercase()}")
    }
}

@Composable
private fun ChoiceRow(label: String, tag: String, selected: Boolean, onSelect: () -> Unit) {
    val tokens = LocalTokens.current
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag(tag)
            .selectable(selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(16.dp).border(2.dp, if (selected) tokens.accent else tokens.muted, CircleShape)
                .padding(3.dp).background(if (selected) tokens.accent else Color.Transparent, CircleShape),
        )
        BodyText(label, Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun NavRow(label: String, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag(tag).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) { BodyText(label, Modifier.weight(1f)) }
}

/** One pack, drawn in its own colours: a live glass swatch over its ground. */
@Composable
private fun ThemeOption(pack: ThemePack, selected: Boolean, onSelect: () -> Unit) {
    val outer = LocalTokens.current
    GhostdeckTheme(pack) {
        val t = LocalTokens.current
        val shape = RoundedCornerShape(18.dp)
        Box(
            Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("theme-${pack.id}")
                .selectable(selected, role = Role.RadioButton, onClick = onSelect)
                .background(t.ground, shape)
                .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) outer.accent else t.border), shape)
                .padding(10.dp),
        ) {
            GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(pack.name, Modifier.weight(1f), color = t.text, fontFamily = GhostdeckFonts.ui, fontSize = 15.sp)
                    listOf(t.accent, tokensFor(pack).accentAlt, t.muted).forEach {
                        Box(Modifier.padding(start = 6.dp).size(14.dp).background(it, CircleShape))
                    }
                }
            }
        }
    }
}
