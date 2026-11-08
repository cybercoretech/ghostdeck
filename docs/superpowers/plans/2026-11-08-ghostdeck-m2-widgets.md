# Ghostdeck M2 (Universal Widgets, Void, Left Panel, Spectral) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Widgets of every kind (Android AppWidgets, Ghostdeck tiles, the System tile) live on the home grid and in manual stacks, surface only when relevant (Void + Haunt), appear in an optional left panel, and apps an icon pack doesn't cover get Spectral Glass tiles; the Spectral launcher icon ships too.

**Architecture:** Two foundation tasks define the shared contracts (model items and the app-side widget dispatcher). Four **lanes** then run **in parallel in separate worktrees** because their file sets don't overlap; each lane only adds files under its own folder and exposes composables/providers through the dispatcher. One integration task wires everything into the home screen, edit mode, picker and Settings, then the smoke/docs task closes the milestone.

**Tech Stack:** as M1 (Kotlin 2.2.21, AGP 9.4.1, Compose BOM 2025.10.00, kotlinx-serialization 1.9.0, coroutines 1.10.2, JUnit 4.13.2, Robolectric 4.16.1).

**Spec:** `docs/superpowers/specs/2026-11-07-ghostdeck-mvp-design.md` (rev 6): §2b.1 (Void), §2b.4 (Haunt), §2b.7 (Spectral Glass), §6.4 (left panel), §9.0–9.1.1 (widgets, stacks, tiles, System tile), §13 (quality bars), §16 (M2 row).

## Global Constraints

- Everything in M1's Global Constraints still applies (no INTERNET, no up-front permissions, `allowBackup=false`, half-cell positions `SUB = 2`, JSON config `"schema": 1`, trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` after the 👻 footer, `adb -s emulator-5554`, never `pkill -f`).
- Branch `feat/m2-widgets` from `main` **after the M1 PR merges**. Lanes branch from `feat/m2-widgets` at the commit after Task 2; the controller merges lanes back one at a time. Never stack PRs: one PR for M2.
- **Lane file ownership is strict:** a lane task may only create/modify files listed in its Files block. `HomeScreen.kt`, `PageGrid.kt`, `MainActivity.kt`, `GhostdeckApp.kt`, `SettingsScreen.kt`, `AndroidManifest.xml` and `core/model/**` belong to the foundation and integration tasks only.
- Tile sizes (spec §9.1): 1×1, 2×1, 2×2, 4×1, 4×2 cells (×`SUB` in sub-units). AppWidgets use their provider's min size rounded up to whole cells.
- AppWidget contents can't be restyled: frame only (corner radius, padding, optional background plate, opacity). Say so in the picker.
- Off-screen AppWidgets get no updates (`AppWidgetHostView` hidden when its page isn't visible); stacks inflate only the visible member plus one neighbour (§9.0.2).
- Void: a widget with `visibility = VOID` is drawn only while relevant and always shows a one-line reason when surfaced; `ALWAYS` is always drawn (§2b.1). Default for new tiles: `ALWAYS`; the user opts into Void per widget.
- Haunt (§2b.4): drift in 40 dp from the nearest edge with blur, fade out the same way; plain fade when Remove animations is on; ≤ 600 ms.
- System tile shows exactly: battery %, charging state, storage used/free, RAM in use, uptime, network type (Wi‑Fi/cellular/none, never addresses). The word "telemetry" never appears in the UI.
- Next event tile needs `READ_CALENDAR`, requested only when the user adds that tile. No calendar = the tile explains and offers a grant button; no nagging.
- Spectral (§2b.7): purpose tints talk `#a78bfa`, look `#f472b6`, go `#2dd4bf`, tools `#f0b35a` (theme packs override from `accent_hot`, `hot_pink`, `cyan`, `orange`); white glyph must meet 3:1 on the tile composited over white and black.
- **Deferred on purpose (rulings for this plan):** smart stack rotation and per-deck widget visibility need deck signals (M4); widgets inside folders and pinning widgets into Command Bar results need the Command Bar (M3); plugin tiles need the plugin API (M5); the Deck tile needs decks (M4).

## Review Focus

1. **A widget's provider app is uninstalled or updated** while the widget is on home: the item stays, shows a "widget unavailable" frame with Remove, and the `appWidgetId` is deleted from the host when the item is removed (no leaked ids). *(Test: Task 5 `providerRemovedShowsUnavailableFrame`, Task 7 `removingWidgetDeletesHostId`.)*
2. **The user cancels the bind/permission or configure screen** midway: no half-placed item, and the allocated id is deleted. *(Test: Task 5 `cancelledBindDeletesIdAndPlacesNothing`, `cancelledConfigureDeletesId`.)*
3. **A grid resize or shrink** with 4×2 widgets on it: widgets are moved, never dropped, and never overlap (reuses `Layout.resize`). *(Test: Task 1 `resizeKeepsWidgetsWithoutOverlap`.)*
4. **Void with nothing relevant**: the page shows no empty holes or jumping layout; Void widgets keep their cells reserved, exactly like locked Private Space items. *(Test: Task 6 `voidWidgetHiddenKeepsCellReserved`.)*
5. **Config written by M1** (no widget items, no new fields) loads unchanged in M2. *(Test: Task 1 `m1ConfigDecodesUnchanged`.)*

---

## Foundation (sequential, branch `feat/m2-widgets`)

### Task 1: Widget items in the model (`:core:model`)

**Files:**
- Modify: `core/model/src/main/kotlin/net/cybercoretech/ghostdeck/model/Items.kt`, `LayoutOps.kt`
- Create: `core/model/src/main/kotlin/net/cybercoretech/ghostdeck/model/Widgets.kt`, `core/model/src/main/kotlin/net/cybercoretech/ghostdeck/model/Purpose.kt`
- Test: `core/model/src/test/kotlin/net/cybercoretech/ghostdeck/model/WidgetOpsTest.kt`, `PurposeTest.kt`, extend `ConfigSerializationTest.kt`

**Interfaces (Produces; `@Serializable`, package `net.cybercoretech.ghostdeck.model`):**

```kotlin
enum class WidgetVisibility { ALWAYS, VOID }
@Serializable data class WidgetFrame(val plate: Boolean = true, val opacity: Float = 1f, val cornerDp: Int = 20)
enum class TileKind { CLOCK, NEXT_ALARM, BATTERY, QUICK_NOTE, NEXT_EVENT, SYSTEM }
@SerialName("appWidget") data class AppWidgetItem(override val id: String, val appWidgetId: Int, val provider: String /* flattened ComponentName */, val userSerial: Long,
    val frame: WidgetFrame = WidgetFrame(), val visibility: WidgetVisibility = WidgetVisibility.ALWAYS) : Item
@SerialName("tile") data class TileItem(override val id: String, val kind: TileKind, val style: String = "default", val note: String = "",
    val frame: WidgetFrame = WidgetFrame(), val visibility: WidgetVisibility = WidgetVisibility.ALWAYS) : Item
@SerialName("stack") data class StackItem(override val id: String, val members: List<Item> /* AppWidgetItem|TileItem only */, val shownIndex: Int = 0, val pinned: Boolean = false) : Item
val Item.isWidget: Boolean          // AppWidgetItem, TileItem, StackItem
enum class Purpose { TALK, LOOK, GO, TOOLS }
fun purposeFor(category: Int, handlesBrowsing: Boolean, handlesMaps: Boolean): Purpose   // ApplicationInfo.CATEGORY_* ints as plain Int constants
// AppItem gains: val purposeOverride: Purpose? = null
fun Layout.createStack(draggedId: String, ontoId: String): LayoutResult   // both widgets → StackItem at onto's cell, size = larger span; onto a stack → appended; non-widget → Err(InvalidTarget)
fun Layout.unstack(stackId: String, memberId: String): LayoutResult       // member placed via firstFreeCell; 1 member left → collapses to it
fun Layout.setSpan(itemId: String, spanX: Int, spanY: Int): LayoutResult   // widgets only; bounds + occupancy checked in sub-units
fun Layout.setWidgetVisibility(itemId: String, v: WidgetVisibility): LayoutResult
fun Layout.allAppWidgetIds(): Set<Int>                                     // includes stack members
```

- [ ] **Step 1: Write the failing tests.** `WidgetOpsTest`: `createStackFromTwoTiles`, `createStackRejectsApps` (`Err(InvalidTarget)`), `stackOntoStackAppends`, `unstackLastMemberCollapses`, `setSpanRejectsOverlap`, `setSpanRejectsOutOfBounds`, `setSpanRejectsApps`, `resizeKeepsWidgetsWithoutOverlap` (5×6 with a 4×2 tile at `cell(0,0,4,2)` and apps → `resize(GridSize(4,5))`: every item kept, no two items share a sub-unit), `allAppWidgetIdsIncludesStackMembers`. `PurposeTest`: `socialIsTalk` (CATEGORY_SOCIAL=4 → TALK), `audioVideoImageGameIsLook`, `mapsAndBrowsersAreGo` (CATEGORY_MAPS=6 or `handlesMaps`/`handlesBrowsing` → GO), `unknownIsTools`. `ConfigSerializationTest`: round-trip with every new item type, and `m1ConfigDecodesUnchanged` (a literal M1 JSON string from the M1 test fixtures decodes and re-encodes with identical items).
- [ ] **Step 2: Run** `./gradlew :core:model:test` → FAIL (unresolved references).
- [ ] **Step 3: Implement.** `place`/`move` already check spans; widgets reuse them. `StackItem` is never placed in the dock or a folder (`move` to `DockSlot`/`IntoFolder` with a widget → `Err(InvalidTarget)`; add that test too: `widgetCannotGoInDockOrFolder`).
- [ ] **Step 4: Run** `./gradlew :core:model:test` → PASS.
- [ ] **Step 5: Commit:** `feat(model): widget, tile and stack items; purpose mapping`

### Task 2: Widget dispatcher contract (`:app`)

**Files:**
- Create: `app/src/main/kotlin/net/cybercoretech/ghostdeck/widgets/WidgetSlot.kt`, `app/src/main/kotlin/net/cybercoretech/ghostdeck/widgets/WidgetRegistry.kt`
- Test: `app/src/test/kotlin/net/cybercoretech/ghostdeck/widgets/WidgetRegistryTest.kt`

**Interfaces (Produces):**

```kotlin
data class WidgetContext(val sizeDp: DpSize, val pageVisible: Boolean, val reduceMotion: Boolean, val editing: Boolean)
fun interface WidgetRenderer { @Composable fun Render(item: Item, ctx: WidgetContext, modifier: Modifier) }
data class Relevance(val relevant: Boolean, val reason: String?)          // reason non-null whenever relevant
fun interface RelevanceSource { fun relevance(item: Item): Flow<Relevance> }
class WidgetRegistry {                                                      // one instance in AppContainer (wired in Task 9)
    fun register(type: KClass<out Item>, renderer: WidgetRenderer)
    fun renderer(item: Item): WidgetRenderer                                // unknown type → UnsupportedWidgetRenderer ("Can't show this widget")
    var relevance: RelevanceSource                                          // default: always Relevance(true, null)
}
@Composable fun WidgetSlot(item: Item, ctx: WidgetContext, registry: WidgetRegistry, modifier: Modifier = Modifier)   // applies the WidgetFrame (GlassSurface plate, cornerDp, opacity) then the renderer
```

- [ ] **Step 1: Write the failing tests:** `unknownItemUsesUnsupportedRenderer`, `registeredRendererIsUsed` (Compose test: a fake renderer's text appears), `frameOpacityApplied` (alpha semantics or a `testTag` + `GraphicsLayer` check), `defaultRelevanceIsAlwaysRelevant`.
- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*WidgetRegistryTest*'` → FAIL.
- [ ] **Step 3: Implement** in the two files. `WidgetSlot` uses `GlassSurface` when `frame.plate`.
- [ ] **Step 4: Run** → PASS. **Step 5: Commit:** `feat(widgets): renderer registry and widget slot contract`

---

## Lanes (parallel; each in its own worktree from the Task 2 commit)

### Task 3 (Lane A): Spectral launcher icon and Spectral tiles

**Files:**
- Modify: `app/src/main/res/drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml`, `app/src/main/res/mipmap-anydpi/ic_launcher.xml`
- Create: `app/src/main/res/drawable/ic_launcher_monochrome.xml`, `app/src/main/kotlin/net/cybercoretech/ghostdeck/icons/SpectralIconProvider.kt`, `app/src/main/kotlin/net/cybercoretech/ghostdeck/icons/SpectralTint.kt`, `fastlane/metadata/android/en-US/images/icon.png` (512 px), `docs/brand/ghostdeck-icon.svg`
- Test: `app/src/test/kotlin/net/cybercoretech/ghostdeck/icons/SpectralTintTest.kt`, `SpectralIconProviderTest.kt`

**Interfaces:**
- Consumes: `Purpose`, `purposeFor` (Task 1); `IconProvider` (M1 `icons/IconProvider.kt`); `ThemePack` roles; `Contrast` (M1 `theme/Contrast.kt`)
- Produces: `object SpectralTint { fun colors(pack: ThemePack?): Map<Purpose, Int>; fun glyphPasses(tint: Int): Boolean }` and `class SpectralIconProvider(base: IconProvider, monochrome: (AppKey) -> Drawable?, purpose: (AppKey) -> Purpose, tints: () -> Map<Purpose, Int>, drawer: SpectralDrawer = DefaultSpectralDrawer) : IconProvider` (apps with no monochrome layer: their normal icon on the tile)

- [ ] **Step 1: Failing tests:** `defaultTintsMatchSpec` (TALK `0xFFA78BFA`, LOOK `0xFFF472B6`, GO `0xFF2DD4BF`, TOOLS `0xFFF0B35A`), `packRolesOverrideTints` (accent_hot/hot_pink/cyan/orange), `whiteGlyphPassesOnEveryBundledPack` (3:1 over white and black for the 4 bundled packs; adjust tile alpha per §2b.3 rule), `usesMonochromeLayerWhenPresent`, `fallsBackToNormalIconOnTile`, `purposeOverrideWins`.
- [ ] **Step 2–4:** run → FAIL, implement, run → PASS (`./gradlew :app:testDebugUnitTest --tests '*Spectral*'`).
- [ ] **Step 5: Launcher icon** per §2b.7 (gradient `#a78bfa → #5b4bc4 → #2dd4bf`, white ghost in the 66 dp safe zone, indigo `#4b3fa8` eyes, monochrome = ghost silhouette). Export the 512 px PNG with ImageMagick from the SVG. `./gradlew assembleDebug lint` passes.
- [ ] **Step 6: Commit:** `feat(icons): Spectral Glass launcher icon and Spectral tiles`

### Task 4 (Lane B): Ghostdeck tiles and the System tile

**Files:**
- Create under `app/src/main/kotlin/net/cybercoretech/ghostdeck/tiles/`: `TileSources.kt` (data flows), `TileFormat.kt` (pure formatting), `ClockTile.kt`, `AlarmTile.kt`, `BatteryTile.kt`, `NoteTile.kt`, `EventTile.kt`, `SystemTile.kt`, `TileRenderer.kt`
- Test: `app/src/test/kotlin/net/cybercoretech/ghostdeck/tiles/TileFormatTest.kt`, `TileSourcesTest.kt`, `TilesRenderTest.kt`

**Interfaces:**
- Consumes: `TileItem`, `TileKind` (Task 1); `WidgetRenderer`, `WidgetContext` (Task 2); `LocalTokens`, `GhostdeckFonts`
- Produces: `class TileRenderer(sources: TileSources) : WidgetRenderer`; `interface TileSources { val battery: Flow<BatteryInfo>; val nextAlarm: Flow<Long?>; val nextEvent: Flow<EventInfo?>; val system: Flow<SystemInfo>; fun clock(): Flow<Long> }` with `data class SystemInfo(val batteryPct: Int, val charging: Boolean, val storageUsed: Long, val storageFree: Long, val ramUsed: Long, val ramTotal: Long, val uptimeMs: Long, val network: NetworkKind)` and `enum class NetworkKind { WIFI, CELLULAR, NONE }`; `class AndroidTileSources(context: Context) : TileSources`; `fun quickNoteChanged(itemId: String, text: String)` callback param on `TileRenderer` (integration persists it via `Layout` transform)

- [ ] **Step 1: Failing tests:** `TileFormatTest`: `uptimeFormatsDaysHoursMinutes` (`93_780_000` → `"1d 2h 3m"`), `bytesFormatBinaryUnits` (`1_610_612_736` → `"1.5 GB"`), `alarmFormatsRelative` (in 7 h 5 m → `"in 7h 5m"`), `noTelemetryWordInAnyTileString` (scan all tile string resources and format outputs for "telemetry", case-insensitive). `TileSourcesTest` (Robolectric): `systemInfoHasNoAddresses` (SystemInfo has no IP/SSID fields: reflect on its properties), `networkKindFromCapabilities`, `eventFlowEmptyWithoutPermission` (no READ_CALENDAR → `null`, no exception). `TilesRenderTest`: one test per kind that it renders its key text, plus `eventTileWithoutPermissionShowsGrantButton`, `clockTerminalStyleUsesMono`.
- [ ] **Step 2–4:** run → FAIL, implement, run → PASS (`./gradlew :app:testDebugUnitTest --tests '*tiles*'`). Sources: battery from the sticky `ACTION_BATTERY_CHANGED`; storage `StatFs(filesDir)`; RAM `ActivityManager.MemoryInfo`; uptime `SystemClock.elapsedRealtime`; network `ConnectivityManager.registerDefaultNetworkCallback` (type only); alarm `AlarmManager.nextAlarmClock` + `ACTION_NEXT_ALARM_CLOCK_CHANGED`; events `CalendarContract.Instances` next 24 h only when granted. All flows stop collecting when not observed (no work with the screen off, §13).
- [ ] **Step 5: Commit:** `feat(tiles): built-in tiles and the System tile`

### Task 5 (Lane C): AppWidget hosting

**Files:**
- Create under `app/src/main/kotlin/net/cybercoretech/ghostdeck/widgets/host/`: `GhostdeckWidgetHost.kt`, `WidgetBindFlow.kt` (pure state machine), `AppWidgetRenderer.kt`, `WidgetProviders.kt`
- Test: `app/src/test/kotlin/net/cybercoretech/ghostdeck/widgets/host/WidgetBindFlowTest.kt`, `AppWidgetRendererTest.kt`

**Interfaces:**
- Consumes: `AppWidgetItem` (Task 1); `WidgetRenderer`, `WidgetContext` (Task 2)
- Produces:
```kotlin
class GhostdeckWidgetHost(context: Context) { val host: AppWidgetHost /* hostId 1024 */; fun allocate(): Int; fun delete(id: Int); fun start(); fun stop(); fun deleteOrphans(liveIds: Set<Int>) }
sealed interface BindStep { data class NeedBindPermission(val intent: Intent) : BindStep; data class NeedConfigure(val id: Int) : BindStep; data class Done(val item: AppWidgetItem) : BindStep; data class Failed(val id: Int?) : BindStep }
class WidgetBindFlow(host: WidgetHostOps, manager: WidgetManagerOps) { fun start(provider: ComponentName, user: UserHandle): BindStep; fun onBindResult(ok: Boolean): BindStep; fun onConfigureResult(ok: Boolean): BindStep }   // every Failed path deletes the allocated id
data class ProviderEntry(val provider: ComponentName, val user: UserHandle, val label: String, val minSpanCells: Pair<Int, Int>, val previewRes: Int)
class WidgetProviders(context: Context) { fun list(): List<ProviderEntry> }   // AppWidgetManager.getInstalledProvidersForProfile for each profile
class AppWidgetRenderer(host: GhostdeckWidgetHost) : WidgetRenderer     // AndroidView(AppWidgetHostView); visibility follows ctx.pageVisible; unknown/removed provider → "Widget unavailable" frame with a Remove affordance (callback)
```
- [ ] **Step 1: Failing tests:** `WidgetBindFlowTest` (fakes for host/manager): `boundWithoutConfigureIsDone`, `needsPermissionThenBound`, `cancelledBindDeletesIdAndPlacesNothing`, `cancelledConfigureDeletesId`, `configureOkIsDone`, `deleteOrphansRemovesIdsNotInLayout`. `AppWidgetRendererTest` (Robolectric): `providerRemovedShowsUnavailableFrame`, `hiddenWhenPageNotVisible`, `minSpanRoundsUpToWholeCells` (provider min 110×40 dp with cell 80×100 dp → 2×1).
- [ ] **Step 2–4:** run → FAIL, implement, run → PASS. `start()` in `onStart`, `stop()` in `onStop` (wired in Task 9). Declare nothing new in the manifest here (Task 9 adds `<queries>` if needed).
- [ ] **Step 5: Commit:** `feat(widgets): AppWidget hosting with a safe bind/configure flow`

### Task 6 (Lane D): Void relevance, Haunt, stacks and the left panel

**Files:**
- Create: `app/src/main/kotlin/net/cybercoretech/ghostdeck/void/LocalRelevance.kt`, `app/src/main/kotlin/net/cybercoretech/ghostdeck/void/Signals.kt`, `app/src/main/kotlin/net/cybercoretech/ghostdeck/ui/theme/Haunt.kt`, `app/src/main/kotlin/net/cybercoretech/ghostdeck/widgets/StackRenderer.kt`, `app/src/main/kotlin/net/cybercoretech/ghostdeck/home/LeftPanel.kt`
- Test: `app/src/test/kotlin/net/cybercoretech/ghostdeck/void/LocalRelevanceTest.kt`, `app/src/test/kotlin/net/cybercoretech/ghostdeck/widgets/StackRendererTest.kt`, `app/src/test/kotlin/net/cybercoretech/ghostdeck/home/LeftPanelTest.kt`, `app/src/test/kotlin/net/cybercoretech/ghostdeck/ui/theme/HauntTest.kt`

**Interfaces:**
- Consumes: Task 1 items; Task 2 `WidgetRegistry`, `WidgetSlot`, `Relevance`, `RelevanceSource`; Task 4's `TileSources` **interface only** (inject a fake in tests; don't import Lane B files beyond that interface, which Task 2's commit doesn't contain, so declare a minimal `SignalInputs` in `Signals.kt`: `val nextEventInMin: Flow<Int?>; val nextAlarmInMin: Flow<Int?>; val headphones: Flow<Boolean>; val batteryPct: Flow<Int>; val charging: Flow<Boolean>`)
- Produces: `class LocalRelevance(inputs: SignalInputs, purposeOf: (provider: String) -> Purpose, clock: () -> Long) : RelevanceSource` (Task 7 supplies `purposeOf` from the catalog + `purposeFor`); `fun Modifier.haunt(visible: Boolean, fromEdge: HauntEdge, reduceMotion: Boolean): Modifier`; `class StackRenderer(registry: WidgetRegistry, onShownIndex: (stackId: String, index: Int) -> Unit) : WidgetRenderer`; `@Composable fun LeftPanel(items: List<Item>, registry: WidgetRegistry, onClose: () -> Unit, modifier: Modifier = Modifier)`; `class AndroidSignalInputs(context: Context) : SignalInputs` (headphones via `AudioManager.registerAudioDeviceCallback`)
- Rules (M2 relevance table, all local):

| Item | Relevant when | Reason line |
|---|---|---|
| NEXT_EVENT tile | event starts within 15 min | "surfaced because a meeting is under 15 min away" |
| NEXT_ALARM tile | alarm within 60 min | "surfaced because an alarm is within the hour" |
| BATTERY / SYSTEM tile | battery ≤ 15 % and not charging, or charging | "surfaced because the battery is low" / "…because it's charging" |
| AppWidget whose provider is a media app (`Purpose.LOOK`), and stacks containing one | headphones connected | "surfaced because headphones are connected" |
| anything else with `VOID` | never (shown only when the user pins it ALWAYS) | — |

- [ ] **Step 1: Failing tests:** `LocalRelevanceTest`: one test per table row (relevant + exact reason; not relevant → `Relevance(false, null)`), `alwaysVisibilityIgnoresSignals`. `HauntTest`: `hauntInstantFadeWhenReduceMotion`, `hauntDurationAtMost600ms`. `StackRendererTest`: `swipeUpCyclesAndReportsIndex`, `dotsShowPosition`, `onlyShownAndNeighbourComposed` (§9.0.2: a counting fake renderer composes ≤ 2 members), `pinnedStackDoesNotCycle`. `LeftPanelTest`: `rendersGivenItems`, `voidWidgetHiddenKeepsCellReserved` (a not-relevant VOID tile leaves its slot empty but the layout doesn't shift), `backCloses`.
- [ ] **Step 2–4:** run → FAIL, implement, run → PASS (`./gradlew :app:testDebugUnitTest --tests '*void*' --tests '*Stack*' --tests '*LeftPanel*' --tests '*Haunt*'`).
- [ ] **Step 5: Commit:** `feat(void): local relevance, Haunt, stacks and the left panel`

---

## Integration (sequential, after all lanes are merged into `feat/m2-widgets`)

### Task 7: Widgets on the home grid and in edit mode

**Files:**
- Modify: `home/PageGrid.kt`, `home/HomeScreen.kt`, `home/HomeViewModel.kt`, `home/HomeUiState.kt`, `home/edit/EditOverlay.kt`, `home/edit/EditGestures.kt`, `home/edit/ItemMenu.kt`, `GhostdeckApp.kt`, `MainActivity.kt`, `AndroidManifest.xml`
- Test: extend `HomeScreenTest`, `EditModeTest`, `HomeViewModelTest`

**Interfaces:**
- Consumes: everything from Tasks 1–6.
- Produces: `AppContainer.widgets: WidgetRegistry` with all renderers registered (`AppWidgetItem` → `AppWidgetRenderer`, `TileItem` → `TileRenderer`, `StackItem` → `StackRenderer`), `relevance = LocalRelevance(AndroidSignalInputs)`, `icons` wrapping `SpectralIconProvider` per `prefs.iconStyle`; `Prefs` gains `iconStyle: String = "system"` (`system|pack|spectral`) and `leftPanel: Boolean = false` (model change: add with defaults + `m1ConfigDecodesUnchanged` still passes).
- [ ] **Step 1: Failing tests:** `widgetRendersAtItsSpan`, `voidWidgetSurfacesWithReason` (reason text visible), `widgetResizeHandlesInEditMode` (drag a handle → `setSpan` transform), `dropWidgetOntoWidgetCreatesStack`, `removingWidgetDeletesHostId` (fake host records `delete(id)`), `swipeRightFromFirstPageOpensLeftPanelWhenEnabled`, `leftPanelOffByDefault`, `itemMenuSetsAlwaysOrVoid`, `quickNoteEditPersists`, `orphanWidgetIdsDeletedOnStart`.
- [ ] **Step 2–4:** run → FAIL, implement, run → PASS. Host `start()/stop()` follow `MainActivity` `onStart/onStop`. Quick-note edits are a `Layout` transform on the current layout.
- [ ] **Step 5: Commit:** `feat(home): widgets, stacks, Void and the left panel on the home screen`

### Task 8: One picker for every widget, and Settings

**Files:**
- Create: `home/edit/WidgetPickerSheet.kt`
- Modify: `home/edit/EditOverlay.kt` ("+ Widgets" button), `settings/SettingsScreen.kt` (Icons: System / Icon pack / Spectral; Left panel toggle), `MainActivity.kt` (bind/configure activity results, `READ_CALENDAR` request when the Next event tile is added)
- Test: `WidgetPickerSheetTest.kt`, extend `SettingsScreenTest`

- [ ] **Step 1: Failing tests:** `pickerListsTilesAndProviders` (fake providers + 6 tile kinds), `pickerSearchFilters`, `pickerShowsSourceBadges` ("Ghostdeck" / app name), `pickerExplainsAppWidgetLimit` (copy: "Android widgets keep their own look; Ghostdeck can only frame them."), `addingEventTileRequestsCalendarOnlyThen`, `iconStyleRadioWritesPref`, `leftPanelToggleWritesPref`.
- [ ] **Step 2–4:** run → FAIL, implement, run → PASS.
- [ ] **Step 5: Commit:** `feat(edit): unified widget picker; icon style and left panel settings`

### Task 9: Emulator pass, docs, and the M2 PR

**Files:** `app/src/androidTest/.../WidgetSmokeTest.kt`; `README.md`; `docs/screenshots/`; Wiki `~/Wiki/src/tools/ghostdeck.md`; vault `~/Vaults/blackbox-notes/Projects/Ghostdeck.md`; local `AGENT_HANDOFF.md`

- [ ] **Step 1: `WidgetSmokeTest`** (animations off, `onboardingDone=true` seeded): add a Clock tile via the picker, see it on home, drop a Battery tile onto it to make a stack, swipe the stack.
- [ ] **Step 2: Run** on the headless emulator: `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest` → PASS. Re-add the HOME role afterwards (`cmd role add-role-holder android.app.role.HOME net.cybercoretech.ghostdeck.debug`).
- [ ] **Step 3: Manual checks on the emulator:** bind the stock Clock AppWidget (permission + any configure), resize it, remove it (`dumpsys appwidget` shows the id gone), Spectral icons on, left panel on, a Void Next-alarm tile surfacing after `adb shell cmd alarm` sets one within the hour. Screenshots: home with widgets, left panel, Spectral icons.
- [ ] **Step 4: Full check:** `bash tools/tests/test-privacy-gate.sh && bash tools/tests/test-charter-sync.sh && ./gradlew assembleDebug testDebugUnitTest lint assembleRelease && tools/privacy-gate.sh apk app/build/outputs/apk/release/app-release-unsigned.apk && tools/privacy-gate.sh mapping < app/build/outputs/mapping/release/mapping.txt && tools/privacy-gate.sh gradle` → all exit 0.
- [ ] **Step 5: Docs** (README status "M2 (widgets) working", Wiki, vault, AGENT_HANDOFF) and commit `test: widget smoke test; docs for M2`. The controller pushes and opens the PR after the final review.

---

## Spec coverage (M2)

| Spec item | Task |
|---|---|
| §9.0 one item model, shared frame, AppWidget limit stated | 1, 2, 5, 8 |
| §9.0 same resize handles and drag | 1, 7 |
| §9.0 one picker with search, previews, source badges | 8 |
| §9.0.1 stacks (manual), dots, pin | 1, 6, 7 |
| §9.0.2 off-screen widgets hidden, stack inflates ≤ 2 | 5, 6 |
| §9.1.1 built-in tiles + System tile (honest set, no "telemetry") | 4 |
| §2b.1 Void surfacing with reasons, ALWAYS pin | 6, 7 |
| §2b.4 Haunt + reduced-motion fallback | 6 |
| §6.4 left panel, off by default | 6, 7, 8 |
| §2b.7 launcher icon, Spectral tiles, purpose mapping, pack tints, contrast | 1, 3, 7, 8 |
| §13 no work with screen off, no up-front permissions | 4, 5, 8 |

**Deferred (see Global Constraints):** smart rotation and per-deck visibility → M4; widgets in folders and Command Bar pinning → M3; plugin tiles → M5; Deck tile → M4; UI glyph drawables arrive with their features (M3–M5).

## Execution notes for the controller

- Run Tasks 1–2 first. Then Lanes A–D (Tasks 3–6) in **parallel worktrees branched from the Task 2 commit** (check each worktree's base; the harness may create it from `main`). Usage-limit aware: two lanes at a time is the sensible default (A+B, then C+D).
- Review each lane on its own, then merge lanes into `feat/m2-widgets` one at a time (fast-forward the first, merge commits after), running the full check after each merge.
- Tasks 7–9 are sequential on `feat/m2-widgets`.
