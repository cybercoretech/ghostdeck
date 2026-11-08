# Ghostdeck M0 + M1 (Privacy Rails + Home Core + Design Base) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship an installable Ghostdeck that can be set as the default home app. It has a paged grid with fine (half-cell) positioning, a dock, folders, deep shortcuts, icon swipe actions, edit mode, icon packs, opt-in badges, work-profile and Private Space support, the theme engine with four bundled packs, Phantom Glass surfaces, bundled fonts, the Materialize motion, and a hidden Labs screen. Every build is guarded by a CI privacy gate that fails on `INTERNET` or tracker code.

**Architecture:** There are two pure-Kotlin JVM modules. `:core:model` holds the data model, layout operations and catalog reducer. `:core:data` holds the JSON config store. A pure-Kotlin `:interaction:home` module holds the edit-mode state machine. `:app` holds the Android adapters (LauncherApps, RoleManager, notifications, icon packs) and the Compose UI. The layering follows spec §2a: the UI only renders `HomeUiState`, decisions live in pure code with JVM tests, and Android-only code stays thin.

**Tech Stack:** Kotlin 2.2.21, AGP 9.4.1, Gradle 9.6.0, Jetpack Compose (BOM 2025.10.00) + Material 3, kotlinx-serialization-json 1.9.0, coroutines 1.10.2, JUnit 4.13.2, Robolectric 4.16.1, bash for the privacy gate.

**Spec:** `docs/superpowers/specs/2026-11-07-ghostdeck-mvp-design.md` (rev 5). This plan covers milestones **M0** and **M1** from §16. M2–M6 get their own plans.

## Global Constraints

- applicationId and namespace `net.cybercoretech.ghostdeck`; debug builds use `applicationIdSuffix = ".debug"`.
- `compileSdk 36`, `targetSdk 36`, `minSdk 30`, `buildToolsVersion "36.0.0"`, JVM target 17.
- **The release APK must not contain `android.permission.INTERNET`.** CI fails if it does.
- No Google Play Services, Firebase, analytics, crash reporting or ads libraries, and no AccessibilityService. Never request `READ_CALL_LOG`, `READ_SMS` or device-admin APIs.
- `android:allowBackup="false"`, plus `dataExtractionRules` that exclude everything.
- Permissions are requested only when the feature that needs them is turned on. The first run shows the charter and asks for no permissions.
- Grid: default 5×6, cols 3–8, rows 4–10. Dock: 0–6 slots. *(Default of 5 slots is Claude's call.)*
- Config file format: JSON, `"schema": 1`, written atomically.
- **Plan-level change from spec §4 (Claude's call):** layouts and preferences live in the single JSON config (`GhostdeckConfig`) instead of Room/DataStore. It's the same document as the §11 export, so there's no schema duplication and no KSP. Room can come back in M3/M4 for the learned-signals store.
- Licence header not required per file. The project is GPL-3.0-or-later (root `LICENSE`).
- Every commit body explains what changed, why, and how it was verified. The body ends with the footer `👻 Code & Design by Darkstardevx 🤖 Generated with Claude Code`, then a blank line, then `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Check `git config user.email` is the `darkstardevx` noreply address before the first commit.
- Branches: M0 work on `feat/m0-privacy-rails` → PR → merge. Then M1 on `feat/m1-home-core` from the updated `main`. **Never stack PRs.** Keep GitHub activity light: one PR per milestone.
- Emulator testing: always `adb -s emulator-5554`, because the owner's phone is often plugged in. Never `pkill -f`. AVD home: `ANDROID_AVD_HOME=~/.config/.android/avd`.
- `AGENT_HANDOFF.md` is local only (already in `.git/info/exclude`). Never commit it.
- **Positions are in half-cell units (spec §6.1):** `SUB = 2` sub-units per cell. An icon is 2×2 sub-units. Grid sizes stay in whole cells. Dock and folders are whole-slot.
- **Theme packs are vendored** in `app/src/main/assets/themes/` by `tools/sync-themes.sh` (dev time only). Bundled: `ghostdeck` (`bg 07060d`, `accent_hot a78bfa`, `accent_alt 2dd4bf`), `cybercore-vortex-synthetic`, `terminal-ghost-alpha`, `cryo-terminal-blue-synthetic`, plus the dynamic-colour pack `system` (API 31+). Default `ghostdeck`.
- **Fonts are bundled** (OFL, licences in `app/src/main/assets/licenses/`): Space Grotesk (display), Inter (UI), JetBrains Mono (mono). No downloadable fonts.
- **Motion:** nothing longer than 600 ms; every animation checks `Settings.Global.ANIMATOR_DURATION_SCALE == 0` (Remove animations) and uses its fallback.

## Review Focus

1. **Private Space locked / work profile paused.** That profile's apps leave the visible catalog, but their home and dock items must **stay in the layout** and come back on unlock. Only `PackageRemoved` deletes items. *(Test: Task 6 `profileUnavailableKeepsLayoutItems`.)*
2. **The same app in the personal and work profiles** gives two distinct `AppKey`s (different `userSerial`). Both can be placed, and removing one never removes the other. *(Test: Task 4 `removeAppOnlyMatchesSameUser`.)*
3. **A config file that's corrupt or half-written** (process killed mid-save) must not lose the layout silently. Saves are atomic. A corrupt file is moved aside to `config.corrupt-<ts>.json` and the issue is reported. *(Test: Task 5 `corruptFileIsMovedAsideAndReported`.)*
4. **Shrinking the grid** (e.g. 5×6 → 4×5) must **move** items that no longer fit to free cells or new pages, never drop them. *(Test: Task 4 `resizeReflowsOverflowWithoutLoss`.)*
5. **An app update renames its launcher activity.** The home icon must remap to the package's new launcher activity instead of becoming a dead icon. *(Test: Task 6 `staleComponentRemapsToSoleLauncherActivity`.)*
6. **Half-offset icons** (fine positioning) must never overlap a neighbour, and a grid resize must keep them on the grid. *(Tests: Task 4 `halfOffsetOverlapIsOccupied`, `resizeKeepsHalfOffsetsInBounds`.)*

---

## File map

```
settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/wrapper/*, gradlew*   (Task 1)
core/model/   build.gradle.kts
  src/main/kotlin/net/cybercoretech/ghostdeck/model/{Keys,Items,Layout,Config}.kt      (Task 4)
  src/main/kotlin/net/cybercoretech/ghostdeck/model/LayoutOps.kt                       (Task 4)
  src/main/kotlin/net/cybercoretech/ghostdeck/theme/{ThemePack,Contrast}.kt             (Task 8)
  src/main/kotlin/net/cybercoretech/ghostdeck/catalog/{AppEntry,CatalogState}.kt       (Task 6)
  src/main/kotlin/net/cybercoretech/ghostdeck/catalog/Reconcile.kt                     (Task 6)
core/data/    src/main/kotlin/net/cybercoretech/ghostdeck/data/ConfigStore.kt          (Task 5)
interaction/home/ src/main/kotlin/net/cybercoretech/ghostdeck/edit/EditController.kt   (Task 10)
app/src/main/
  AndroidManifest.xml, res/xml/data_extraction_rules.xml, res/values/{strings,charter,themes}.xml
  kotlin/net/cybercoretech/ghostdeck/
    MainActivity.kt                       (Task 1, Task 7)
    GhostdeckApp.kt                       (Task 9: manual DI container)
    platform/AndroidAppCatalog.kt         (Task 6)
    platform/AppLauncher.kt               (Task 9)
    platform/HomeRole.kt                  (Task 7)
    platform/DefaultApps.kt               (Task 9)
    icons/{IconProvider,IconPackRepository,AppFilterParser}.kt   (Task 11)
    badges/{BadgeListenerService,BadgeCounts}.kt                 (Task 12)
    home/{HomeViewModel,HomeUiState,HomeScreen,PageGrid,Dock,FolderSheet,AppIcon}.kt  (Task 9)
    home/edit/{EditOverlay,AppPickerSheet,ItemMenu}.kt           (Task 10)
    onboarding/OnboardingScreen.kt, settings/{SettingsScreen,LabsScreen,LabsUnlock}.kt   (Task 13)
    ui/theme/{GhostdeckTheme,Tokens,Fonts,GlassSurface,Orbs,Materialize,Motion}.kt   (Task 8)
  assets/themes/*.json, assets/licenses/OFL-*.txt, res/font/*.ttf                    (Task 8)
tools/sync-themes.sh                                                                  (Task 8)
tools/privacy-gate.sh, tools/trackers.txt, tools/update-trackers.sh, tools/tests/test-privacy-gate.sh (Task 2)
.github/workflows/android.yml, PRIVACY.md   (Task 3)
```

---

## M0: Privacy rails (branch `feat/m0-privacy-rails`)

### Task 1: Gradle scaffold and privacy-safe app shell

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/wrapper/gradle-wrapper.{jar,properties}`, `gradlew`, `gradlew.bat`
- Create: `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/data_extraction_rules.xml`, `app/src/main/res/values/{strings,themes}.xml`, `app/src/main/kotlin/net/cybercoretech/ghostdeck/MainActivity.kt`
- Create: `core/model/build.gradle.kts`, `core/data/build.gradle.kts`, `interaction/home/build.gradle.kts` (empty source sets for now)
- Test: `app/src/test/kotlin/net/cybercoretech/ghostdeck/ManifestPrivacyTest.kt`

**Interfaces:**
- Produces: modules `:app`, `:core:model`, `:core:data`, `:interaction:home`. The pure modules use plugin `org.jetbrains.kotlin.jvm` 2.2.21 and `org.jetbrains.kotlin.plugin.serialization` 2.2.21 (`:core:model`, `:core:data`).

- [ ] **Step 1: Copy the wrapper and the `gradle.properties` flags from `~/Devspace/Android/CyberVault`** (Gradle 9.6.0 with the same `distributionSha256Sum`). Settings use `rootProject.name = "Ghostdeck"` and include the four modules. Root plugins: AGP `com.android.application` 9.4.1, `org.jetbrains.kotlin.android`, `.plugin.compose`, `.jvm`, `.plugin.serialization`, all 2.2.21, `apply false`.

- [ ] **Step 2: `app/build.gradle.kts`** mirrors CyberVault without the Rust/NDK parts:
  - Signing from env vars `GHOSTDECK_RELEASE_{STORE_FILE,STORE_PASSWORD,KEY_ALIAS,KEY_PASSWORD}`, with the same all-or-nothing guard
  - `ghostdeckVersionCode` / `ghostdeckVersionName` Gradle properties, defaulting to `1` / `"0.0.1"`
  - Release: minify + shrinkResources. Lint: `warningsAsErrors = true`, with the same informational set
  - Dependencies: compose BOM 2025.10.00, `material3`, `activity-compose:1.11.0`, `lifecycle-runtime-compose:2.9.4`, `lifecycle-viewmodel-compose:2.9.4`, `kotlinx-coroutines-android:1.10.2`, project deps on the three modules
  - Test dependencies: `junit:4.13.2`, `robolectric:4.16.1`, `kotlinx-coroutines-test:1.10.2`, `androidx.compose.ui:ui-test-junit4`, debug `ui-test-manifest`
  - `testOptions.unitTests.isIncludeAndroidResources = true`

- [ ] **Step 3: Write the failing test** `ManifestPrivacyTest` (Robolectric, `@Config(sdk = [35])`):

```kotlin
@Test fun noInternetPermission() {
    val info = pm().getPackageInfo(ctx.packageName, PackageManager.GET_PERMISSIONS)
    assertFalse(info.requestedPermissions.orEmpty().contains(Manifest.permission.INTERNET))
}
@Test fun backupDisabled() {
    assertEquals(0, ctx.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
}
```

- [ ] **Step 4: Run it and confirm it fails.** `./gradlew :app:testDebugUnitTest --tests '*ManifestPrivacyTest*'` should FAIL (no manifest/app yet, or backup still allowed).

- [ ] **Step 5: Manifest + shell.**
  - `<application android:allowBackup="false" android:dataExtractionRules="@xml/data_extraction_rules" android:fullBackupContent="false" android:theme="@style/Theme.Ghostdeck">`
  - `data_extraction_rules.xml` excludes `root`, `file`, `database`, `sharedpref` and `external` for both `<cloud-backup>` and `<device-transfer>`
  - `MainActivity` (a `ComponentActivity`) shows a centred `Text("Ghostdeck")`
  - Theme `Theme.Ghostdeck`: parent `android:Theme.Material.NoActionBar`, `android:windowShowWallpaper=true`, transparent `windowBackground`, `windowLightStatusBar` false

- [ ] **Step 6: Run the tests and the build.** `./gradlew :app:testDebugUnitTest assembleDebug lint` should PASS.

- [ ] **Step 7: Commit:** `build: scaffold Ghostdeck Gradle project and privacy-safe app shell`

### Task 2: Privacy gate script (permissions, tracker code, dependencies)

**Files:**
- Create: `tools/privacy-gate.sh`, `tools/trackers.txt`, `tools/update-trackers.sh`
- Test: `tools/tests/test-privacy-gate.sh`, `tools/tests/fixtures/{perms-clean,perms-internet,dex-clean,dex-firebase,deps-clean,deps-gms}.txt`

**Interfaces:**
- Produces a CLI with every subcommand exiting 0 = clean, 1 = violation (offending lines printed to stderr), 2 = usage error:
  - `privacy-gate.sh perms` reads `aapt2 dump permissions` output on stdin
  - `privacy-gate.sh dex` reads `strings` output of the dex files on stdin
  - `privacy-gate.sh deps` reads `gradle dependencies` output on stdin
  - `privacy-gate.sh apk <path>` runs `perms` + `dex` against the APK (uses `$ANDROID_HOME/build-tools/36.0.0/aapt2`, `unzip -p <apk> 'classes*.dex' | strings -n 6`)
  - `privacy-gate.sh gradle` runs `./gradlew -q :app:dependencies --configuration releaseRuntimeClasspath | privacy-gate.sh deps`

- [ ] **Step 1: Write fixtures and the failing test script.** Each case pipes a fixture into a subcommand and asserts its exit code.

```bash
expect 0 perms < fixtures/perms-clean.txt        # has ACCESS_NETWORK_STATE only
expect 1 perms < fixtures/perms-internet.txt     # has 'uses-permission: name='android.permission.INTERNET''
expect 0 dex   < fixtures/dex-clean.txt
expect 1 dex   < fixtures/dex-firebase.txt       # contains 'Lcom/google/firebase/analytics/FirebaseAnalytics;'
expect 0 deps  < fixtures/deps-clean.txt
expect 1 deps  < fixtures/deps-gms.txt           # contains 'com.google.android.gms:play-services-base:18.5.0'
expect 2 bogus
```

- [ ] **Step 2: Run it and confirm it fails.** `bash tools/tests/test-privacy-gate.sh` should FAIL (script missing).

- [ ] **Step 3: Implement `tools/privacy-gate.sh`** (`set -euo pipefail`):
  - `perms` fails on a line matching `android.permission.INTERNET`
  - `dex` builds one ERE from `tools/trackers.txt`: each non-comment line is a Java package prefix, e.g. `com.google.firebase.analytics`. It's converted to descriptor form `L com/google/firebase/analytics/` (no space), and any match fails
  - `deps` fails on a line whose Maven group starts with any denied group: `com.google.firebase`, `com.google.android.gms`, `com.google.android.play`, `com.crashlytics`, `io.sentry`, `com.facebook`, `com.appsflyer`, `com.adjust`, `com.amplitude`, `com.mixpanel`, `com.segment`, `io.branch`, `com.onesignal`, `com.bugsnag`, `com.newrelic`, `com.datadoghq`
  - Keep this group list in the script header

- [ ] **Step 4: Seed `tools/trackers.txt`** with a header comment naming the source (Exodus Privacy tracker signatures, snapshot date). Add `update-trackers.sh`, which fetches `https://reports.exodus-privacy.eu.org/api/trackers`, splits each `code_signature` on `|`, strips regex escapes, then sorts and dedupes into the file. **Run it once at dev time, and commit the result.** CI never fetches it.

- [ ] **Step 5: Run the tests and confirm they pass.** `bash tools/tests/test-privacy-gate.sh` prints all cases passing, exit 0. Also: `./gradlew assembleRelease && tools/privacy-gate.sh apk app/build/outputs/apk/release/app-release-unsigned.apk` exits 0.

- [ ] **Step 6: Manual negative check.** Temporarily add `<uses-permission android:name="android.permission.INTERNET"/>` to the manifest and rebuild the release APK. `privacy-gate.sh apk …` should exit 1. Revert the change.

- [ ] **Step 7: Commit:** `ci: add privacy gate for INTERNET permission, tracker code and tracker dependencies`

### Task 3: CI workflow, charter text, and README charter fix

**Files:**
- Create: `.github/workflows/android.yml`, `PRIVACY.md`, `app/src/main/res/values/charter.xml`
- Modify: `README.md` (Privacy Charter section)

**Interfaces:**
- Produces: `R.array.charter_lines` (7 strings, exact text below without markdown) and `R.string.charter_title` = "Ghostdeck Privacy Charter". Task 13 uses these.

- [ ] **Step 1: Write `PRIVACY.md`** with the seven charter lines **word for word from spec §11a.1** (including "Ever, in any build." and the reference to the Privacy Ledger), followed by the §11a.2 "How it's enforced" table. **Replace README's charter list with the same seven lines verbatim.** The current README wording differs, and the spec requires the text to match word for word.

- [ ] **Step 2: `charter.xml`**: a `string-array name="charter_lines"` with the seven lines (bold markers removed, apostrophes escaped).

- [ ] **Step 3: Workflow** (based on CyberVault's `android.yml`, minus Rust/NDK):
  - Triggers: push, pull_request, workflow_dispatch
  - Setup: temurin 21, `setup-android@v4`, `sdkmanager 'platforms;android-36' 'build-tools;36.0.0'`
  - Steps:
    1. `bash tools/tests/test-privacy-gate.sh`
    2. `./gradlew assembleDebug testDebugUnitTest lint assembleRelease`
    3. `tools/privacy-gate.sh apk app/build/outputs/apk/release/app-release-unsigned.apk`
    4. `tools/privacy-gate.sh gradle`
    5. upload the debug APK

- [ ] **Step 4: Verify locally.** Run every workflow `run:` command in order. All should exit 0.

- [ ] **Step 5: Commit:** `ci: add Android workflow with privacy gate; publish the charter verbatim`

- [ ] **Step 6: Push the branch and open the PR** "M0: privacy rails", with the PR body footer `👻 Code & Design by Darkstardevx 🤖 Generated with [Claude Code](https://claude.com/claude-code)`. Confirm CI is green. If push/PR runs don't trigger (a known issue on some org repos), run `gh workflow run Android --ref feat/m0-privacy-rails`. **Stop and wait for the owner to merge before starting M1.**

---

## M1: Home core (branch `feat/m1-home-core`, cut from `main` after M0 merges)

### Task 4: Data model and layout operations (`:core:model`)

**Files:**
- Create: `core/model/src/main/kotlin/net/cybercoretech/ghostdeck/model/{Keys,Items,Layout,Config,LayoutOps}.kt`
- Test: `core/model/src/test/kotlin/net/cybercoretech/ghostdeck/model/LayoutOpsTest.kt`, `ConfigSerializationTest.kt`

**Interfaces (Produces; all `@Serializable`, package `net.cybercoretech.ghostdeck.model`):**

```kotlin
data class AppKey(val packageName: String, val className: String, val userSerial: Long)
const val SUB = 2                                                       // sub-units per cell (spec §6.1)
data class Cell(val x: Int, val y: Int, val spanX: Int = SUB, val spanY: Int = SUB)   // all in sub-units
fun cell(col: Int, row: Int, spanCols: Int = 1, spanRows: Int = 1): Cell  // whole-cell helper: Cell(col*SUB, row*SUB, spanCols*SUB, spanRows*SUB)
val Cell.isWholeAligned: Boolean                                         // x, y, spanX, spanY all multiples of SUB
sealed interface ItemAction                                             // M5 widens this to the full Action type
@SerialName("launchApp")      data class LaunchApp(val app: AppKey) : ItemAction
@SerialName("launchShortcut") data class LaunchShortcut(val packageName: String, val shortcutId: String, val userSerial: Long) : ItemAction
@SerialName("shortcutList")   data object ShowShortcuts : ItemAction    // the app's own long-press shortcuts
sealed interface Item { val id: String }
@SerialName("app")      data class AppItem(override val id: String, val app: AppKey, val customLabel: String? = null, val swipeUp: ItemAction? = null) : Item
@SerialName("shortcut") data class ShortcutItem(override val id: String, val packageName: String, val shortcutId: String, val userSerial: Long, val label: String, val swipeUp: ItemAction? = null) : Item
@SerialName("folder")   data class FolderItem(override val id: String, val name: String, val children: List<Item>) : Item   // children never contain FolderItem
data class Placed(val item: Item, val cell: Cell)
data class Page(val placed: List<Placed> = emptyList())
data class GridSize(val cols: Int = 5, val rows: Int = 6)          // init: require(cols in 3..8 && rows in 4..10)
data class Dock(val items: List<Item?> = List(5) { null })          // init: require(items.size in 0..6)
data class Layout(val grid: GridSize = GridSize(), val pages: List<Page> = listOf(Page()), val dock: Dock = Dock(), val hidden: Set<AppKey> = emptySet())
data class Deck(val id: String, val name: String, val icon: String, val accentArgb: Long? = null, val layout: Layout)
data class Prefs(val iconPack: String? = null, val badges: Boolean = false, val onboardingDone: Boolean = false,
                 val themePack: String = "ghostdeck", val fineGrid: Boolean = false, val swipeMarks: Boolean = true, val labsUnlocked: Boolean = false)
data class GhostdeckConfig(val schema: Int = 1, val decks: List<Deck>, val activeDeckId: String, val prefs: Prefs = Prefs())

enum class LayoutError { OutOfBounds, Occupied, NestedFolder, DockFull, UnknownItem, InvalidTarget }
sealed interface LayoutResult { data class Ok(val layout: Layout) : LayoutResult; data class Err(val error: LayoutError) : LayoutResult }
sealed interface Target { data class Grid(val page: Int, val cell: Cell) : Target; data class DockSlot(val index: Int) : Target; data class IntoFolder(val folderId: String) : Target }

object ItemIds { fun new(): String }   // UUID v4 string

fun Layout.place(item: Item, page: Int, cell: Cell): LayoutResult   // page == pages.size appends a page
fun Layout.move(itemId: String, to: Target): LayoutResult
fun Layout.remove(itemId: String): Layout
fun Layout.removeApp(key: AppKey): Layout            // grid, dock and folders; folder left with 1 child collapses into it at the same cell, 0 → removed
fun Layout.createFolder(draggedId: String, ontoId: String, name: String = "Folder"): LayoutResult
fun Layout.firstFreeCell(spanX: Int = SUB, spanY: Int = SUB): Pair<Int, Cell>   // row-major across pages, whole-aligned cells only; returns (pages.size, Cell(0,0)) if all full
fun Layout.resize(newGrid: GridSize): Layout          // items still in bounds keep their (possibly half-offset) cell; overflow re-placed via firstFreeCell, original row-major order kept
fun Layout.setSwipeUp(itemId: String, action: ItemAction?): LayoutResult   // AppItem/ShortcutItem only, else Err(InvalidTarget); searches grid, dock and folder children
fun Layout.remapApp(stale: AppKey, replacement: AppKey): Layout
fun Layout.dropEmptyTrailingPages(): Layout           // always keeps ≥ 1 page
fun Layout.findItem(itemId: String): Item?
fun Layout.allAppKeys(): Set<AppKey>                  // includes folder children and dock
fun GhostdeckConfig.activeDeck(): Deck
fun GhostdeckConfig.updateActiveLayout(f: (Layout) -> Layout): GhostdeckConfig
```

- [ ] **Step 1: Write the failing tests** (`LayoutOpsTest`), at least these:

```kotlin
@Test fun placeRejectsOutOfBounds()   { assertEquals(Err(OutOfBounds), Layout().place(app("a"), 0, cell(5, 0))) }       // 5 cols → col 5 invalid
@Test fun halfStepAtRightEdgeOutOfBounds() { assertEquals(Err(OutOfBounds), Layout().place(app("a"), 0, Cell(9, 0))) } // sub x 9 + span 2 > 10
@Test fun placeRejectsOccupied()      { val l = ok(Layout().place(app("a"), 0, cell(0,0))); assertEquals(Err(Occupied), l.place(app("b"), 0, cell(0,0))) }
@Test fun halfOffsetOverlapIsOccupied() { val l = ok(Layout().place(app("a"), 0, cell(0,0))); assertEquals(Err(Occupied), l.place(app("b"), 0, Cell(1,0))) }
@Test fun halfOffsetBesideIsFree()    { val l = ok(Layout().place(app("a"), 0, cell(0,0))); assertTrue(l.place(app("b"), 0, Cell(2,1)) is Ok) }
@Test fun resizeKeepsHalfOffsetsInBounds() { /* item at Cell(3,1) on 5×6, resize to 4×5 → still Cell(3,1); item at Cell(9,0)-ish beyond new width reflows to a whole-aligned free cell */ }
@Test fun swipeUpOnlyForAppsAndShortcuts() { /* setSwipeUp on AppItem → Ok and serialises; on FolderItem → Err(InvalidTarget); on a folder child → Ok */ }
@Test fun placeOnNextPageAppends()    { assertEquals(2, ok(Layout().place(app("a"), 1, Cell(0,0))).pages.size) }
@Test fun dockFull()                  { /* Dock with 5 filled slots; move grid item to DockSlot(2) occupied → Err(Occupied); Dock(List(7){null}) throws */ }
@Test fun folderCannotNest()          { /* move a FolderItem IntoFolder(other) → Err(NestedFolder) */ }
@Test fun removeAppCollapsesFolder()  { /* folder [a, b] at Cell(1,1); removeApp(a) → AppItem b at Cell(1,1) */ }
@Test fun removeAppOnlyMatchesSameUser() { /* AppKey("p","C",0) and AppKey("p","C",10) both placed; removeApp(user 10) leaves user 0 item */ }
@Test fun resizeReflowsOverflowWithoutLoss() { /* 5×6 page full of 30 apps → resize(GridSize(4,5)): allAppKeys() unchanged (30), every cell x<4,y<5, pages ≥ 2 */ }
@Test fun gridBoundsValidated()       { assertThrows<IllegalArgumentException> { GridSize(9, 6) }; assertThrows<IllegalArgumentException> { GridSize(5, 3) } }
@Test fun createFolderFromTwoApps()   { /* result contains FolderItem(children=[onto, dragged]) at onto's cell; dragged's old cell free */ }
```

  `ConfigSerializationTest`: round-trip a config containing every `Item` subtype and every `ItemAction` subtype (as `swipeUp`). The JSON contains `"schema":1`, `"type":"folder"` and `"type":"launchApp"`.

- [ ] **Step 2: Run them and confirm they fail.** `./gradlew :core:model:test` should FAIL (unresolved references).

- [ ] **Step 3: Implement the model and ops.** All ops are pure and return new instances. Occupancy checks every **sub-unit** covered by `spanX × spanY` (bounds: `x + spanX <= cols*SUB`, `y + spanY <= rows*SUB`). Shared Json instance for tests and Task 5: `GhostdeckJson = Json { classDiscriminator = "type"; ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }` (put it in `Config.kt`).

- [ ] **Step 4: Run the tests and confirm they pass.** `./gradlew :core:model:test` should PASS.

- [ ] **Step 5: Commit:** `feat(model): layout data model and pure layout operations`

### Task 5: Atomic JSON config store (`:core:data`)

**Files:**
- Create: `core/data/src/main/kotlin/net/cybercoretech/ghostdeck/data/ConfigStore.kt`
- Test: `core/data/src/test/kotlin/net/cybercoretech/ghostdeck/data/ConfigStoreTest.kt`

**Interfaces:**
- Consumes: `GhostdeckConfig`, `GhostdeckJson` (Task 4)
- Produces:

```kotlin
sealed interface LoadResult {
    data class Loaded(val config: GhostdeckConfig) : LoadResult
    data object Missing : LoadResult
    data class Corrupt(val movedTo: File, val reason: String) : LoadResult   // also used for schema != 1
}
class ConfigStore(private val dir: File, private val clock: () -> Long = System::currentTimeMillis) {
    val file: File   // dir/config.json
    fun load(): LoadResult
    fun save(config: GhostdeckConfig)   // write config.json.tmp, FileOutputStream.fd.sync(), Files.move(ATOMIC_MOVE, REPLACE_EXISTING)
}
```
  The caller serialises access (the app uses one single-threaded dispatcher). It's documented in KDoc.

- [ ] **Step 1: Write the failing tests:** `missingReturnsMissing`, `saveThenLoadRoundTrips`, `corruptFileIsMovedAsideAndReported` (write `"{not json"` → `Corrupt`, `movedTo.name == "config.corrupt-<clock>.json"`, `config.json` no longer exists), `newerSchemaTreatedAsCorrupt` (`"schema": 2`), `leftoverTmpIsIgnored` (an existing `config.json.tmp` with garbage + a valid `config.json` → `Loaded`).

- [ ] **Step 2: Run them and confirm they fail.** `./gradlew :core:data:test` should FAIL.

- [ ] **Step 3: Implement `ConfigStore`** as specified above.

- [ ] **Step 4: Run them and confirm they pass.** `./gradlew :core:data:test` should PASS.

- [ ] **Step 5: Commit:** `feat(data): atomic JSON config store with corrupt-file recovery`

### Task 6: App catalog: pure reducer plus the Android adapter (work profile + Private Space)

**Files:**
- Create: `core/model/src/main/kotlin/net/cybercoretech/ghostdeck/catalog/{AppEntry,CatalogState,Reconcile}.kt`
- Create: `app/src/main/kotlin/net/cybercoretech/ghostdeck/platform/AndroidAppCatalog.kt`
- Modify: `app/src/main/AndroidManifest.xml` (add `<uses-permission android:name="android.permission.ACCESS_HIDDEN_PROFILES"/>`)
- Test: `core/model/src/test/kotlin/net/cybercoretech/ghostdeck/catalog/CatalogStateTest.kt`

**Interfaces:**
- Consumes: `AppKey`, `Layout.removeApp`, `Layout.remapApp`, `Layout.allAppKeys` (Task 4)
- Produces:

```kotlin
enum class ProfileKind { PERSONAL, WORK, PRIVATE, OTHER }
data class AppEntry(val key: AppKey, val label: String, val profile: ProfileKind)
sealed interface CatalogEvent {
    data class Snapshot(val userSerial: Long, val profile: ProfileKind, val entries: List<AppEntry>) : CatalogEvent
    data class PackageUpserted(val userSerial: Long, val packageName: String, val entries: List<AppEntry>) : CatalogEvent
    data class PackageRemoved(val userSerial: Long, val packageName: String) : CatalogEvent
    data class ProfileAvailability(val userSerial: Long, val available: Boolean) : CatalogEvent
}
data class CatalogState(val entries: Map<AppKey, AppEntry> = emptyMap(), val unavailableUsers: Set<Long> = emptySet(), val profiles: Map<Long, ProfileKind> = emptyMap()) {
    fun reduce(event: CatalogEvent): CatalogState
    fun visibleApps(): List<AppEntry>   // excludes unavailable users; sorted by label (case-insensitive, locale-aware Collator)
}
fun Layout.reconcile(event: CatalogEvent, state: CatalogState): Layout   // PackageRemoved → removeApp for each key of that pkg+user; ProfileAvailability → unchanged
fun Layout.resolveStale(state: CatalogState): Layout   // AppItem whose key is missing, user available, and package has exactly 1 entry → remapApp to it
interface AppCatalog { val events: Flow<CatalogEvent>; fun setProfileLocked(userSerial: Long, locked: Boolean) }
class AndroidAppCatalog(context: Context) : AppCatalog
```

- [ ] **Step 1: Write the failing tests:** `snapshotPopulates`, `upsertReplacesPackageEntries`, `removeDropsEntries`, `profileUnavailableHidesButKeepsEntries` (`visibleApps()` excludes them, `entries` still has them), `profileUnavailableKeepsLayoutItems` (`reconcile(ProfileAvailability(10,false))` returns an equal layout), `packageRemovedRemovesLayoutItems`, `staleComponentRemapsToSoleLauncherActivity` (layout has `AppKey("p","Old",0)`; the catalog has only `AppKey("p","New",0)` → layout now has `New`), `staleWithMultipleCandidatesLeftAlone`.

- [ ] **Step 2: Run them and confirm they fail.** `./gradlew :core:model:test --tests '*CatalogStateTest*'` should FAIL.

- [ ] **Step 3: Implement the reducer and reconcile** in `:core:model`.

- [ ] **Step 4: Run them and confirm they pass.** The same command should PASS.

- [ ] **Step 5: Implement `AndroidAppCatalog`** as a `callbackFlow`:
  - On start, emit a `Snapshot` for each `UserHandle` in `LauncherApps.profiles`, using `getActivityList(null, user)` and `userSerial = UserManager.getSerialNumberForUser(user)`
  - Register a `LauncherApps.Callback`: `onPackageAdded/Changed` → `PackageUpserted`, `onPackageRemoved` → `PackageRemoved`, `onPackagesAvailable/Unavailable` → `PackageUpserted/PackageRemoved` for each package
  - Register a receiver for `ACTION_MANAGED_PROFILE_AVAILABLE/UNAVAILABLE`, plus `Intent.ACTION_PROFILE_AVAILABLE/UNAVAILABLE` on API 35+, → `ProfileAvailability`
  - Initial availability comes from `UserManager.isQuietModeEnabled(user)`
  - `ProfileKind`: own user → PERSONAL. On API 35+, `launcherApps.getLauncherUserInfo(user)?.userType` maps `UserManager.USER_TYPE_PROFILE_MANAGED` → WORK and `USER_TYPE_PROFILE_PRIVATE` → PRIVATE, else OTHER. Below API 35, any other user → WORK
  - `setProfileLocked` → `UserManager.requestQuietModeEnabled(locked, user)`

- [ ] **Step 6: Verify on the emulator.** Use an API 35+ AVD with a Private Space created in Settings, with Ghostdeck set as home (after Task 7). Log `CatalogEvent`s with `adb -s emulator-5554 logcat -s Ghostdeck`. Expected: a PRIVATE snapshot appears. Locking emits `ProfileAvailability(false)`. **If PRIVATE never appears, `ACCESS_HIDDEN_PROFILES` isn't being granted. Record it in `AGENT_HANDOFF.md` and tell the owner. Don't work around it.**

- [ ] **Step 7: Commit:** `feat(catalog): app catalog reducer and LauncherApps adapter with work and private profiles`

### Task 7: Become the home app

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`, `MainActivity.kt`
- Create: `app/src/main/kotlin/net/cybercoretech/ghostdeck/platform/HomeRole.kt`
- Test: `app/src/test/kotlin/net/cybercoretech/ghostdeck/HomeIntentTest.kt`

**Interfaces:**
- Produces: `class HomeRole(activity: ComponentActivity) { fun isHeld(): Boolean; fun request(launcher: ActivityResultLauncher<Intent>) }` (wraps `RoleManager.ROLE_HOME` / `createRequestRoleIntent`). `MainActivity` exposes `val homePresses: SharedFlow<Unit>`, which emits from `onNewIntent` when the HOME intent arrives while the activity is resumed.

- [ ] **Step 1: Write the failing test** (Robolectric):

```kotlin
@Test fun mainActivityHandlesHome() {
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(ctx.packageName)
    assertEquals(MainActivity::class.java.name, pm().resolveActivity(i, 0)!!.activityInfo.name)
}
```

- [ ] **Step 2: Run it and confirm it fails.** It should FAIL, because the activity isn't resolved.

- [ ] **Step 3: Manifest.** `MainActivity` gets these attributes and filters:
  - `launchMode="singleTask"`, `stateNotNeeded="true"`, `excludeFromRecents="true"`, `clearTaskOnLaunch="true"`, `resizeableActivity="true"`, `screenOrientation="unspecified"`, `windowSoftInputMode="adjustPan"`
  - Intent filters: MAIN+HOME+DEFAULT, and MAIN+LAUNCHER
  - Back press on home is consumed (`OnBackPressedCallback` that does nothing at root)

- [ ] **Step 4: Run it and confirm it passes.** The same test should PASS.

- [ ] **Step 5: Commit:** `feat(app): register as a home activity and wrap the HOME role request`

### Task 8: Theme engine, fonts, Phantom Glass and Materialize

**Files:**
- Create: `core/model/src/main/kotlin/net/cybercoretech/ghostdeck/theme/{ThemePack,Contrast}.kt`
- Create: `app/.../ui/theme/{GhostdeckTheme,Tokens,Fonts,GlassSurface,Orbs,Materialize,Motion}.kt`
- Create: `app/src/main/assets/themes/{ghostdeck,cybercore-vortex-synthetic,terminal-ghost-alpha,cryo-terminal-blue-synthetic}.json`, `app/src/main/res/font/{space_grotesk,inter,jetbrains_mono}.ttf` (variable fonts), `app/src/main/assets/licenses/OFL-{SpaceGrotesk,Inter,JetBrainsMono}.txt`, `tools/sync-themes.sh`
- Test: `core/model/src/test/.../theme/{ThemePackTest,ContrastTest}.kt`, `app/src/test/.../ui/theme/{ThemeAssetsTest,MotionTest}.kt`

**Interfaces:**
- Consumes: `Prefs.themePack` (Task 4)
- Produces:

```kotlin
// :core:model, package net.cybercoretech.ghostdeck.theme
@Serializable data class ThemePack(val id: String, val name: String, val roles: Map<String, String>)   // 20 registry roles, hex without '#'
object ThemeRoles { val REQUIRED: List<String> }   // bg surface panel border line selection hover white muted text_primary accent_hot accent_alt acid_green hot_pink purple cyan orange red success warning error
fun parseThemePack(id: String, name: String, json: String): Result<ThemePack>   // fails on a missing role or a non-6-hex value
object Contrast {
    fun luminance(rgb: Int): Double                       // WCAG relative luminance
    fun ratio(a: Int, b: Int): Double
    fun composite(top: Int, alpha: Float, under: Int): Int
    fun glassAlpha(text: Int, surface: Int): Float?       // lowest alpha in 0.55..0.95 (0.01 steps) where ratio(text, composite(surface, a, WHITE)) >= 4.5 AND same over BLACK; null = pack fails
}
// :app
data class GhostdeckTokens(val ground: Color, val glass: Color, val glassAlpha: Float, val border: Color, val text: Color, val muted: Color,
                           val accent: Color, val accentAlt: Color, val ok: Color, val warn: Color, val error: Color)
fun tokensFor(pack: ThemePack, deckAccent: Color? = null): GhostdeckTokens   // bg→ground, surface→glass, accent_hot/accent_alt→accent/accentAlt, success/warning/error→ok/warn/error; deckAccent overrides accent
class ThemeRepository(context: Context) { fun bundled(): List<ThemePack>; fun load(id: String): ThemePack /* unknown id → ghostdeck */ }
@Composable fun GhostdeckTheme(pack: ThemePack, content: @Composable () -> Unit)   // provides LocalTokens + the three FontFamilies
val LocalTokens: ProvidableCompositionLocal<GhostdeckTokens>
object GhostdeckFonts { val display: FontFamily; val ui: FontFamily; val mono: FontFamily }
@Composable fun GlassSurface(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(24.dp), content: @Composable BoxScope.() -> Unit)
@Composable fun AmbientOrbs(modifier: Modifier = Modifier)   // two radial gradients (accent, accentAlt), drift period ≥ 12 s; static when animations off or power save
fun Modifier.materialize(visible: Boolean, reduceMotion: Boolean): Modifier   // alpha 0→1, scale 0.86→1, blur 10→0 dp (API 31+) over 260 ms; reduceMotion → instant
interface MotionPolicy { val reduceMotion: Boolean; val powerSave: Boolean }   // reads ANIMATOR_DURATION_SCALE and PowerManager.isPowerSaveMode
fun applySheetBlur(window: Window, radiusPx: Int)   // API 31+ and windowManager.isCrossWindowBlurEnabled → setBackgroundBlurRadius; else no-op
```

- [ ] **Step 1: Vendor the packs.** Write `tools/sync-themes.sh <registry-dir>` (default `~/Devspace/Cybercore/web/cybercore-tech.github.io/data/themes`): copies `cybercore-vortex/cybercore-vortex-synthetic.json`, `terminal-ghost/terminal-ghost-alpha.json`, `cryo-terminal-blue/cryo-terminal-blue-synthetic.json` into `app/src/main/assets/themes/`, and fails if any role is missing. Hand-write `ghostdeck.json` with all 20 roles (`bg 07060d`, `surface 0f0d1a`, `panel 15122a`, `accent_hot a78bfa`, `accent_alt 2dd4bf`, `text_primary e7e3f4`, `muted 9a95ad`, `success 6af0ba`, `warning f0b35a`, `error ff6b81`; the rest from the mockup palette). Run it once and commit the output.

- [ ] **Step 2: Bundle the fonts.** Download the variable TTFs and OFL licences from the official upstream repos (`google/fonts` `ofl/spacegrotesk`, `ofl/inter`, `JetBrains/JetBrainsMono` release) at dev time; record the source commit/tag in `assets/licenses/FONTS.md`. Never fetched by the build.

- [ ] **Step 3: Write the failing tests.**
  - `ContrastTest`: `blackOnWhiteIs21`, `compositeFullAlphaIsTop`, `glassAlphaFindsLowestPassing` (text `e7e3f4`, surface `0f0d1a` → a value in 0.55..0.95), `glassAlphaNullWhenImpossible` (text `777777`, surface `808080` → null).
  - `ThemePackTest`: `parsesRegistryJson`, `missingRoleFails`, `badHexFails`.
  - `ThemeAssetsTest` (Robolectric): `allBundledPacksParse` (4 packs), **`allBundledPacksPassContrast`** (`glassAlpha(text_primary, surface) != null` for each), `unknownIdFallsBackToGhostdeck`, `deckAccentOverridesPackAccent`.
  - `MotionTest`: `materializeInstantWhenReduceMotion` (Compose test with `reduceMotion = true`: alpha is 1 on the first frame), `orbsStaticWhenPowerSave`.

- [ ] **Step 4: Run them and confirm they fail.** `./gradlew :core:model:test :app:testDebugUnitTest` should FAIL.

- [ ] **Step 5: Implement.** `GlassSurface` draws `glass` at `glassAlpha` with a 1 dp top-highlight border (`white` at 12%) and the shape; it does not blur its backdrop (the wallpaper can't be read; spec §2b.4). `AmbientOrbs` is drawn once behind the home content. The `system` pack (API 31+) builds roles from `dynamicDarkColorScheme(context)` and is listed only on 31+.

- [ ] **Step 6: Run them and confirm they pass.** Same command should PASS.

- [ ] **Step 7: Commit:** `feat(theme): theme packs, contrast check, bundled fonts, Phantom Glass and Materialize`

### Task 9: Home screen UI (pager, grid, dock, folders, launching, wallpaper)

**Files:**
- Create: `GhostdeckApp.kt`, `platform/{AppLauncher,DefaultApps}.kt`, `home/{HomeUiState,HomeViewModel,HomeScreen,PageGrid,Dock,FolderSheet,AppIcon}.kt`, `icons/IconProvider.kt`
- Modify: `AndroidManifest.xml` (`android:name=".GhostdeckApp"`, `<queries>` for `ACTION_DIAL`, `ACTION_VIEW http`, `ACTION_IMAGE_CAPTURE`, `ACTION_SENDTO smsto`)
- Test: `app/src/test/kotlin/net/cybercoretech/ghostdeck/home/HomeViewModelTest.kt`, `HomeScreenTest.kt`, `core/model/src/test/.../DefaultLayoutTest.kt`

**Interfaces:**
- Consumes: Tasks 4–8
- Produces:

```kotlin
data class HomeUiState(val layout: Layout, val apps: Map<AppKey, AppEntry>, val unavailableUsers: Set<Long>, val currentPage: Int = 0,
                       val editing: Boolean = false, val badges: Map<String, Int> = emptyMap(), val loadIssue: String? = null)
class HomeViewModel(store: ConfigStore, catalog: AppCatalog, defaults: DefaultAppsResolver, io: CoroutineDispatcher) : ViewModel() {
    val state: StateFlow<HomeUiState>
    fun updateLayout(f: (Layout) -> Layout)   // applies to the active deck, persists via store.save on io
    fun setEditing(on: Boolean); fun goToPage(i: Int)
}
interface AppLauncher { fun launch(key: AppKey, bounds: Rect?); fun launchShortcut(item: ShortcutItem, bounds: Rect?); fun appInfo(key: AppKey); fun uninstall(key: AppKey) }
interface IconProvider { fun icon(key: AppKey, sizePx: Int): ImageBitmap }   // LRU cache of 300 entries; default impl uses LauncherActivityInfo.getBadgedIcon(density)
fun interface DefaultAppsResolver { fun resolve(): List<AppKey> }   // dialer, SMS, browser, camera; missing ones skipped
fun defaultLayout(dockApps: List<AppKey>): Layout   // in :core:model; dock = first 5 of dockApps, page 0 empty
```

- [ ] **Step 1: Write the failing tests.**
  - `DefaultLayoutTest`: 4 apps → a dock of 5 slots with the first 4 filled.
  - `HomeViewModelTest` (with a fake `AppCatalog` and a temp-dir `ConfigStore`):
    - `missingConfigCreatesDefaultAndSaves`
    - `corruptConfigSurfacesLoadIssue` (`loadIssue != null`, layout is the default)
    - `packageRemovedUpdatesAndPersists`
    - `privateProfileLockKeepsItems`
  - `HomeScreenTest` (Compose on Robolectric):
    - `rendersAppAtCell` (the label for `Cell(2,3)` is shown)
    - `tapLaunches` (the fake `AppLauncher` records the key)
    - `unavailableProfileItemsRenderDimmedAndDontLaunch`
    - `folderTapOpensSheetWithChildren`
    - `halfOffsetItemRendersBetweenCells` (an item at `Cell(1,0)` is drawn half a cell right of column 0)
    - `iconSwipeUpRunsSwipeAction` (swipe up on an icon with `swipeUp = LaunchApp(b)` launches `b`; without one the swipe goes to the page)
    - `swipeMarkShownOnlyWhenEnabled`

- [ ] **Step 2: Run them and confirm they fail.** `./gradlew :core:model:test :app:testDebugUnitTest` should FAIL.

- [ ] **Step 3: Implement.**
  - `GhostdeckApp` holds a small manual DI container: store at `filesDir/config`, a single-thread `io` dispatcher, catalog, launcher, icon provider.
  - `HomeViewModel` loads on start:
    - On `Missing` or `Corrupt` it builds a single deck: `Deck(id="home", name="Home", icon="home", layout=defaultLayout(...))`.
    - It folds catalog events through `CatalogState.reduce` + `Layout.reconcile` + `resolveStale`.
  - `HomeScreen`:
    - `HorizontalPager` over `layout.pages`, with a `PageGrid` that places `Placed` items in a `cols×rows` grid sized to the window, a `Dock` row, and edge-to-edge insets.
    - On pager scroll it calls `WallpaperManager.setWallpaperOffsets(windowToken, fraction, 0f)` with steps `1/(pages-1)`.
    - A home press (`homePresses`) animates to page 0 and closes any sheet.
    - Swipe down opens the notification shade via **reflection** on the hidden `StatusBarManager.expandNotificationsPanel()` (`getSystemService("statusbar")`). Lawnchair and other launchers do the same. It needs `EXPAND_STATUS_BAR`, a normal permission, so declare it. Wrap the call in `runCatching`: if a future Android blocks it, the gesture silently does nothing, and the failure is logged once under tag `Ghostdeck`.
    - Labels are 1 line, ellipsised, respecting font scale. Labels use `GhostdeckFonts.ui`.
    - The whole screen is wrapped in `GhostdeckTheme(pack)`; `AmbientOrbs` sits behind the pager; the dock and folder sheet are `GlassSurface`s; the folder sheet calls `applySheetBlur`.
    - `PageGrid` lays out in sub-units: one sub-unit = cell size / `SUB`.
    - Icons `materialize` on first appearance (staggered 20 ms, capped at 200 ms total).
    - An icon with `swipeUp` consumes a vertical upward drag of ≥ 24 dp that starts on it and runs the action (`LaunchApp` → `launch`, `LaunchShortcut` → `launchShortcut`, `ShowShortcuts` → opens the item menu). A 4 dp dot under the label marks it when `prefs.swipeMarks`.
  - `AppLauncher`: `LauncherApps.startMainActivity(component, user, bounds, null)`, `startShortcut(...)`, `startAppDetailsActivity(...)`. `uninstall` uses `Intent.ACTION_DELETE` with `package:` URI (declare `REQUEST_DELETE_PACKAGES`).

- [ ] **Step 4: Run them and confirm they pass.** The same command should PASS.

- [ ] **Step 5: Check on the emulator.** `./gradlew installDebug`, then `adb -s emulator-5554 shell cmd role add-role-holder android.app.role.HOME net.cybercoretech.ghostdeck.debug`, then press home. The dock should show 4 defaults, paging should move the wallpaper, and taps should launch apps.

- [ ] **Step 6: Commit:** `feat(home): paged grid, dock, folders and app launching with wallpaper parallax`

### Task 10: Edit mode, the app picker, shortcuts and the item menu

**Files:**
- Create: `interaction/home/src/main/kotlin/net/cybercoretech/ghostdeck/edit/EditController.kt`
- Create: `app/.../home/edit/{EditOverlay,AppPickerSheet,ItemMenu}.kt`
- Test: `interaction/home/src/test/kotlin/net/cybercoretech/ghostdeck/edit/EditControllerTest.kt`, `app/src/test/.../home/edit/AppPickerSheetTest.kt`

**Interfaces:**
- Consumes: `Layout` ops (Task 4), `HomeViewModel.updateLayout`, `AppLauncher` (Task 9)
- Produces:

```kotlin
sealed interface DragSource { data class Existing(val itemId: String) : DragSource; data class New(val item: Item) : DragSource }
sealed interface DropZone { data class OnTarget(val target: Target) : DropZone; data class OnItem(val itemId: String) : DropZone; data object Remove : DropZone; data object Uninstall : DropZone; data object None : DropZone }
sealed interface DropOutcome { data class Updated(val layout: Layout) : DropOutcome; data class Rejected(val error: LayoutError) : DropOutcome; data class UninstallRequested(val key: AppKey) : DropOutcome; data object Cancelled : DropOutcome }
class EditController(initial: Layout) {
    val layout: Layout
    fun startDrag(source: DragSource); fun hover(zone: DropZone): Boolean /* drop would be valid */; fun drop(): DropOutcome; fun cancel()
    var fine: Boolean   // false: snap OnTarget grid cells to whole cells; true: half cells (spec §6.1)
    fun snap(rawSubX: Int, rawSubY: Int): Pair<Int, Int>
}
```
  Rules:
  - Drop `OnItem` on an app → `createFolder`. On a folder → `move(IntoFolder)`.
  - `Remove` on an `Existing` → `remove`.
  - `Uninstall` is only valid for an `AppItem` → `UninstallRequested` (the layout isn't changed until the package is really removed).
  - After every successful drop, call `dropEmptyTrailingPages()`.

- [ ] **Step 1: Write the failing tests** (`EditControllerTest`): `dragToEmptyCellMoves`, `dragOntoAppCreatesFolder`, `dragFolderOntoFolderRejectedNested`, `dropOnOccupiedRejected` (`hover` returns false, `drop` → `Rejected(Occupied)`), `newItemFromPickerPlaces`, `uninstallOnlyForApps` (shortcut → `hover(Uninstall)` false), `cancelRestoresLayout`, `emptyTrailingPageDroppedAfterMove`, `wholeModeSnapsToCells` (`snap(3,1)` → `(2,0)`), `fineModeAllowsHalfCellDrop` (`fine = true`, drop at `Cell(1,0)` → placed there), `dockAndFolderIgnoreFine`.
  `AppPickerSheetTest`: `swipeActionPickerSetsItemAction`, `filtersByQueryCaseInsensitive`, `hiddenAppsExcludedUnlessToggled`, `showsProfileBadgeForWorkAndPrivate`.

- [ ] **Step 2: Run them and confirm they fail.** `./gradlew :interaction:home:test :app:testDebugUnitTest` should FAIL.

- [ ] **Step 3: Implement `EditController`** (pure), then the UI:
  - Long-press empty space → `setEditing(true)`. *(M5 replaces this with the radial menu, whose centre is "Edit home". Keep the trigger in one place: `HomeScreen.onEmptyLongPress`.)*
  - Holding a second finger anywhere while dragging sets `fine = true` for that drag; `prefs.fineGrid` makes it the default.
  - Edit overlay: drop targets "Remove" and "Uninstall" at the top, a "+ Apps" button, a "Done" button, and an extra empty page at the end while dragging.
  - Long-press on an icon opens the `ItemMenu` popup: app shortcuts from `LauncherApps.getShortcuts(ShortcutQuery().setPackage(pkg).setQueryFlags(FLAG_MATCH_DYNAMIC or FLAG_MATCH_MANIFEST or FLAG_MATCH_PINNED), user)`, only when `hasShortcutHostPermission()`, plus App info, Remove, Hide and **Swipe-up action…** (picker: None, another app, one of this app's shortcuts, "Show shortcuts") → `Layout.setSwipeUp`. Dragging from the long-press starts `DragSource.Existing`. Dragging a shortcut row starts `DragSource.New(ShortcutItem)`.
  - The picker sheet lists `visibleApps()` with a search field and a "Show hidden" toggle. Hiding toggles `layout.hidden`.
  - New installs are **not** auto-added to home *(Claude's call; M3 Smart Groups adds the "added to group" prompt)*.

- [ ] **Step 4: Run them and confirm they pass.** The same command should PASS.

- [ ] **Step 5: Check on the emulator:**
  - Move an app between pages and into the dock
  - Create a folder and drag an app out of it
  - Drag a Chrome "New tab" shortcut to home
  - Uninstall a test app via the drop target, after installing one first (e.g. a debug sample APK)

- [ ] **Step 6: Commit:** `feat(edit): edit mode with drag and drop, folders, shortcuts and the app picker`

### Task 11: Icon packs (ADW/Nova `appfilter.xml`) with the themed fallback

**Files:**
- Create: `app/.../icons/{AppFilterParser,IconPackRepository,IconPackIconProvider}.kt`
- Modify: `AndroidManifest.xml` `<queries>`: `<intent><action android:name="org.adw.launcher.THEMES"/></intent>` and `<intent><action android:name="com.novalauncher.THEME"/></intent>`
- Test: `app/src/test/.../icons/AppFilterParserTest.kt`, fixture `app/src/test/resources/appfilter.xml`

**Interfaces:**
- Consumes: `IconProvider` (Task 9), `Prefs.iconPack` (Task 4)
- Produces:

```kotlin
object AppFilterParser { fun parse(parser: XmlPullParser): Map<ComponentName, String> }   // drawable name per component
data class IconPack(val packageName: String, val label: String)
class IconPackRepository(context: Context) { fun installed(): List<IconPack>; fun load(pack: String): Map<ComponentName, String> /* res/xml/appfilter, else assets/appfilter.xml */ }
class IconPackIconProvider(base: IconProvider, repo: IconPackRepository, packProvider: () -> String?) : IconProvider
```

- [ ] **Step 1: Write the failing tests:** `parsesComponentInfoEntries` (`<item component="ComponentInfo{com.a/com.a.Main}" drawable="a_icon"/>` → that mapping), `skipsMalformedEntries` (missing `/`, missing drawable, `:LAUNCHER_ACTION_APP_DRAWER` style entries), `lastDuplicateWins`.

- [ ] **Step 2: Run them and confirm they fail.** `./gradlew :app:testDebugUnitTest --tests '*AppFilterParserTest*'` should FAIL.

- [ ] **Step 3: Implement.** The provider resolves the drawable through `packageManager.getResourcesForApplication(pack).getIdentifier(name, "drawable", pack)`. If it's missing, it falls back to the base icon. On API 33+, if the base `AdaptiveIconDrawable` has a `monochrome` layer and a pack is active, it draws the monochrome layer tinted with the theme accent so unmatched icons still fit (spec §6 "themed adaptive icon"). Changing the pack clears the cache.

- [ ] **Step 4: Run them and confirm they pass.** The same command should PASS.

- [ ] **Step 5: Check on the emulator.** Install any F-Droid icon pack (e.g. Arcticons), select it in Settings (Task 13), and confirm the icons change.

- [ ] **Step 6: Commit:** `feat(icons): ADW/Nova icon pack support with themed monochrome fallback`

### Task 12: Opt-in notification badges

**Files:**
- Create: `app/.../badges/{BadgeCounts,BadgeListenerService}.kt`
- Modify: `AndroidManifest.xml`: a service with `BIND_NOTIFICATION_LISTENER_SERVICE` and the intent filter `android.service.notification.NotificationListenerService`, `android:enabled="true"`
- Test: `app/src/test/.../badges/BadgeCountsTest.kt`

**Interfaces:**
- Produces: `object BadgeCounts { val counts: StateFlow<Map<String, Int>>; fun recompute(active: List<BadgeNotification>) }` with `data class BadgeNotification(val packageName: String, val userSerial: Long, val isOngoing: Boolean, val isGroupSummary: Boolean, val canShowBadge: Boolean)`. The key is `"$packageName/$userSerial"`. `HomeViewModel` combines `counts` into `HomeUiState.badges` only when `prefs.badges == true`.

- [ ] **Step 1: Write the failing tests:** `countsPerPackageAndUser`, `ignoresOngoingAndGroupSummaries`, `respectsChannelCanShowBadge`, `emptyWhenNoneActive`.

- [ ] **Step 2: Run them and confirm they fail.** `./gradlew :app:testDebugUnitTest --tests '*BadgeCountsTest*'` should FAIL.

- [ ] **Step 3: Implement.** The service calls `recompute(activeNotifications.map { … })` on `onListenerConnected`, `onNotificationPosted` and `onNotificationRemoved`. `canShowBadge` comes from `getNotificationChannels(pkg, user)`, cached per channel. The UI draws a dot (no number) on icons when the count is > 0. When the listener isn't granted, nothing shows and nothing nags.

- [ ] **Step 4: Run them and confirm they pass.** The same command should PASS.

- [ ] **Step 5: Commit:** `feat(badges): opt-in notification dots via NotificationListenerService`

### Task 13: First run (charter) and minimal Settings

**Files:**
- Create: `app/.../onboarding/OnboardingScreen.kt`, `app/.../settings/{SettingsScreen,LabsScreen,LabsUnlock}.kt`
- Modify: `MainActivity.kt` (routing: onboarding if `!prefs.onboardingDone`, otherwise home; Settings is reached from the edit overlay's gear button)
- Test: `app/src/test/.../onboarding/OnboardingScreenTest.kt`, `app/src/test/.../settings/{SettingsScreenTest,LabsUnlockTest}.kt`

**Interfaces:**
- Consumes: `R.array.charter_lines` (Task 3), `HomeRole` (Task 7), `IconPackRepository` (Task 11), `HomeViewModel` (add `fun updatePrefs(f: (Prefs) -> Prefs)` and `fun resizeGrid(g: GridSize)`, which calls `Layout.resize`)

- [ ] **Step 1: Write the failing tests:**
  - `OnboardingScreenTest`:
    - `showsAllSevenCharterLines`
    - `noPermissionRequestedOnFirstScreen`: the fake permission requester and role requester record no calls until the "Set as home" button is tapped
    - `continueSetsOnboardingDone`
  - `SettingsScreenTest`:
    - `gridSlidersClampToSpecRanges` (cols 3–8, rows 4–10)
    - `dockSlotsClampTo0to6`
    - `badgesToggleOpensListenerSettingsWhenNotGranted`
    - `iconPackListIncludesSystemDefault`
    - `themePickerListsBundledPacks` (4, plus "System" on API 31+) and selecting one updates `prefs.themePack`
    - `fineGridAndSwipeMarksToggles`
  - `LabsUnlockTest` (pure, in `settings/LabsUnlock.kt`): `sevenTapsWithin3sUnlocks`, `slowTapsReset`, `volumeDownHeld2sUnlocks`, `volumeDownReleasedEarlyDoesNot`, `lockAgainClears`

- [ ] **Step 2: Run them and confirm they fail.** `./gradlew :app:testDebugUnitTest` should FAIL.

- [ ] **Step 3: Implement.**
  - **Onboarding** has two steps:
    1. The charter title and seven lines, with a "Continue" button.
    2. "Make Ghostdeck your home screen", which runs `HomeRole.request` and offers a "Not now" option.
  - **Settings** contains: theme pack picker (with a live glass preview), fine positioning toggle, swipe marks toggle, grid cols/rows, dock slots, icon pack picker, badges toggle, hidden apps list (unhide), and "About & Privacy". About & Privacy shows the charter again, the version, and the licence.
    - The badges toggle deep-links to `Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS`, with a fallback to `ACTION_NOTIFICATION_LISTENER_SETTINGS`, and explains why in one sentence.
    - *(The Privacy Ledger and "What Ghostdeck knows" are M6.)*
  - **Labs (spec §10a.7):** `class LabsUnlock(clock: () -> Long) { fun onVersionTap(): Boolean; fun onVolumeDown(down: Boolean): Boolean }` (true = just unlocked). The Settings screen forwards `KEYCODE_VOLUME_DOWN` (consumed only while Settings is shown). Unlocking sets `prefs.labsUnlocked` and shows a toast; Labs appears as the last Settings row with "Lock Labs" inside. M1 Labs contents: grid-bounds debug overlay toggle and "Export diagnostics" (writes app version, Android version, grid and pack ids to a file through `ACTION_CREATE_DOCUMENT`; nothing else).
  - Changing the dock slot count keeps existing items in their slots where they fit. Items in slots beyond the new count move to `firstFreeCell` on the grid. Add `fun Layout.resizeDock(slots: Int): Layout` to `LayoutOps.kt`, plus the test `resizeDockMovesOverflowToGrid` in `LayoutOpsTest`.

- [ ] **Step 4: Run them and confirm they pass.** `./gradlew :core:model:test :app:testDebugUnitTest` should PASS.

- [ ] **Step 5: Commit:** `feat(app): charter-first onboarding and minimal settings`

### Task 14: Emulator smoke test, docs, and the M1 PR

**Files:**
- Create: `app/src/androidTest/kotlin/net/cybercoretech/ghostdeck/HomeSmokeTest.kt`
- Modify: `app/build.gradle.kts` (`androidx.test.ext:junit`, `androidx.test:runner`, `androidx.compose.ui:ui-test-junit4` as `androidTestImplementation`, `testInstrumentationRunner`), `README.md` (status, build instructions, screenshots)
- Create (outside the repo): `~/Wiki/src/<android category>/ghostdeck.md` + `SUMMARY.md` entry; `~/Vaults/blackbox-notes/Projects/Ghostdeck.md`; local `AGENT_HANDOFF.md`

- [ ] **Step 1: Write `HomeSmokeTest`:** launch `MainActivity` with `onboardingDone=true` pre-seeded in the config, assert the dock node exists, swipe the pager left, and assert page index 1.

- [ ] **Step 2: Run it on the AVD.** `ANDROID_AVD_HOME=~/.config/.android/avd emulator -avd CyberVault_Test_API_36 -no-snapshot-save &`, then `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest`. It should PASS.

- [ ] **Step 3: Run all checks.** `./gradlew assembleDebug testDebugUnitTest lint assembleRelease && tools/privacy-gate.sh apk app/build/outputs/apk/release/app-release-unsigned.apk && tools/privacy-gate.sh gradle` should all exit 0.

- [ ] **Step 4: Docs.**
  - README: update the status line to "M1 (home core) working", and add build/install steps and two emulator screenshots (home, edit mode) under `docs/screenshots/`.
  - Wiki page in the contributing.md format, then `mdbook build`.
  - Vault note: paths, AVD, decisions.
  - `AGENT_HANDOFF.md`: state, resume checklist, decisions (including JSON-instead-of-Room), gotchas (the ACCESS_HIDDEN_PROFILES result from Task 6).
  - Commit the Wiki and the vault in their own repos.

- [ ] **Step 5: Commit, push, and open the PR** "M1: home core". Commit subject: `test: emulator smoke test; docs for M1`. Check CI is green, then wait for the owner to merge.

---

## Spec coverage (M0 + M1)

| Spec item | Task |
|---|---|
| §11a.2 INTERNET CI gate, tracker gate, `allowBackup`, reproducibility groundwork | 1, 2, 3 |
| §11a.1 charter word for word (README, PRIVACY.md, app) | 3, 12 |
| §5 HOME role, Private Space and work profile, no AccessibilityService | 6, 7 |
| §6 grid 5×6 (3–8 × 4–10), dock 0–6, pages, folders, shortcuts | 4, 8, 9 |
| §6 edit mode: move, dock, remove, uninstall, app info | 9 |
| §6 icon packs + themed fallback | 10 |
| §6 global hidden apps | 9, 12 |
| §6 opt-in badges, no nagging | 11, 12 |
| §6 wallpaper offset on scroll | 8 |
| §3 anti-goal: no up-front permission prompts | 12 |
| §6.1 fine positioning (half-cell units) | 4, 9, 10, 13 |
| §6.2 icon swipe actions | 4, 9, 10 |
| §10a.7 Labs | 13 |
| §2b.3 theme packs (vendored), contrast check, System pack | 8, 13 |
| §2b.1/§2b.4 Phantom Glass, orbs, sheet blur, Materialize, reduced motion | 8, 9 |
| §2b.2 bundled fonts | 8 |
| §13 a11y basics (labels, font scale) | 8 (labels), 13 (smoke) |

**Deferred on purpose:**
- Per-deck hidden apps and wallpaper → M4
- Widgets → M2
- Swipe-up Command Bar → M3 (swipe up does nothing in M1)
- Radial menu → M5
- Snapshots, export/import UI, scheduled export, Privacy Ledger, theme generator site → M6
- Classic drawer, textures → M3; left panel, System tile → M2
