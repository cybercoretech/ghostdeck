# Ghostdeck MVP: Design Spec

> **Name:** **Ghostdeck** **(owner, 2026-11-07)**. Web check: no Android app or launcher uses the name. An unrelated Windows MSI fan-control tool on GitHub is called "GhostDeck"; it's a different platform and category, so the risk is low.
> **Date:** 2026-11-07 · **Revision:** 3 (rev 2: intelligent search, universal widgets, power-user layer, privacy charter · rev 3: three-layer architecture, radial menu, Smart Groups, adaptive interface, gesture combos, Activity Center scheduled for v0.2, owner decisions recorded · rev 4: design language (§2b), AI integration in four layers (§8.7), charter line 5 reworded, theme engine moved into M1, Handoff into M3) · **Status:** approved by the owner (rev 4, 2026-11-07)
>
> **Design mockups:** https://claude.ai/artifact/Sxh3LxZMM1Q376tq24dWBa (three directions, the interactive blend, the signature motion board)
> **Owner:** darkstardevx · **Org:** cybercoretech

The owner said "use your defaults", so every decision below is **Claude's call** unless it's marked **(owner)**. Anything here can be overridden.

---

## 1. Intent: why build this

Android launchers have gone stale. Nova was acquired by an ad/analytics company and its team was later cut. Most alternatives are either Pixel clones or minimal text lists. Nobody has shipped a launcher that **adapts to where you are and what you're doing**, can be **driven from the keyboard like a power tool**, and is **verifiably private**.

**The one-line pitch:** *Your home screen rearranges itself for where you are, you can drive it with a command bar, and it never phones home.*

**Who it's for:** power users and privacy-minded people who already install launchers on purpose, own more than one "mode" of phone use (work, car, home, gym, night), and like tweaking things. The first user is the owner, on their own phone.

---

## 2. The pillars of the MVP

| Pillar | What it is | Why it's different |
|---|---|---|
| **Context Decks** | Several complete home layouts ("decks") that switch automatically based on rules (time, Wi‑Fi, Bluetooth device, power, headphones), plus manual override | Other launchers suggest apps. This swaps the **whole environment**, and the rules are readable and explained |
| **Command Bar** | Replaces the app drawer. Type to find apps, shortcuts and contacts, run actions, switch decks, and call plugin verbs | Built in, polished and extensible. Not a gimmicky "terminal launcher" |
| **Tiles + Plugin API** | Home-screen tiles fed by local data or by **plugin apps** through a small public contract | The launcher stays offline. Network and scripting live in separate opt-in plugins (e.g. Nightwatch can supply a darkbox status tile) |
| **Intelligent Search** | Search that understands what you *mean*: typos, synonyms, intents ("pay" finds wallet apps), context-aware ranking, natural-language actions. All on-device | Other launchers' search is substring matching. This one is a local intent engine |
| **Universal Widgets** | One item system: Android widgets, Ghostdeck tiles and plugin tiles all sit on the same grid, can be **stacked**, resize the same way, and follow per-deck rules | Widgets become first-class, deck-aware and stackable, not stuck in a separate picker |
| **Power-User Layer** | Configurable gestures, aliases and macros, a hardware-keyboard mode, an intent/Tasker API, and a config file | Power through **text and files**, not 400 toggles |
| **Agents (rev 4)** | Claude, ChatGPT, Gemini, Copilot and others from the command bar (`@claude …`), in four opt-in layers: handoff, on-device, your own API keys, and agent bridges (§8.7) | AI that **shows what it sends** (consent chips), **proposes instead of acting** (tap to run), and can even design a deck. The core still never touches the network |

**Underneath everything: the Privacy Charter (§11a).** Privacy is enforced by the architecture, not just promised in marketing. The core APK has **no `INTERNET` permission, and CI fails the build if one ever appears**.

---

## 2a. Architecture: the launcher as an operating environment (owner's framing, rev 3)

Ghostdeck is built as **three layers with one-way dependencies**. Every feature belongs to exactly one layer, and the layers talk only through defined interfaces. This keeps the "operating environment" from turning into a tangle.

```
┌──────────────────────────────────────────────────────────────────┐
│ L3  INTERACTION   gestures · radial menu · command bar · keyboard │
│                   automation API · plugins · (v0.2: voice, activity)│
├──────────────────────────────────────────────────────────────────┤
│ L2  INTELLIGENCE  context signals · deck resolver · search/ranking │
│                   Smart Groups · stack rotation · adaptive profile │
│                   learned signals store · (v0.2: Mind scorer)      │
├──────────────────────────────────────────────────────────────────┤
│ L1  VISUAL        decks/layouts · grid · icons · widgets/tiles     │
│                   themes · animations · appearance profiles        │
└──────────────────────────────────────────────────────────────────┘
        all layers sit on :core:model (pure data) + :core:data
```

**Rules:**
- **L1 renders state; it never decides.** It takes a `HomeState` (active deck, layout, appearance profile, stack picks) and draws it
- **L2 decides; it never draws.** It is pure Kotlin behind interfaces (`ContextSource`, `DeckResolver`, `ResultScorer`, `Grouper`, `StackPicker`, `AppearanceAdapter`), and every decision carries a `Reason` so "Why this?" works everywhere
- **L3 turns input into `Action`s.** One `Action` sealed type (open app, run verb, switch/pin deck, open radial, open sheet, macro, …) is shared by gestures, the radial menu, the command bar, macros, the automation API and plugins. **Anything one input can do, every input can do**
- Module mapping: `:ui:*` = L1, `:engine:*` = L2, `:interaction:*` = L3 (this refines the module list in §4)

## 2b. Design language (owner-approved, rev 4)

**One sentence:** *glass you can see through, a terminal when you want to work, and nothing on screen that isn't needed right now.* The mockup canvas linked in the header is the visual reference.

### 2b.1 Three styles, each with its own job
| Style | Where it's used | What it looks like |
|---|---|---|
| **Phantom Glass** (the base) | Home, dock, folders, deck chip, sheets, settings | Near-black ground. Frosted translucent surfaces (`blur` 16–24 dp, white at 6–14% fill, a 1 dp hairline border at 12–16%, an inset top highlight). Two soft blurred colour "orbs" behind the wallpaper layer drift slowly. Icons sit on glass tiles with a 20 dp corner radius |
| **Terminal Ghost** | Command Bar, agent panels, macro editor, "Why this?" sheets, logs | Monospace everywhere. `❯` prompt, small uppercase mono labels (`ATTACHED · TAP TO REMOVE`), results styled like code, text that streams in, a blinking block caret. A near-opaque panel (`~92%`) with an accent hairline |
| **Void behaviour** | Widgets and tiles | Calm by default: a widget surfaces only when it's relevant (a meeting soon, a WARN from a plugin, headphones connected) and fades when it isn't. Every widget that surfaces shows a one-line reason ("surfaced because a plugin reported WARN"). Stacks (§9.0.1) and smart rotation drive it. The user can pin any widget to "always visible" |

### 2b.2 Type
- **Display:** Space Grotesk (clock, deck names, headings)
- **UI text:** Inter
- **Terminal surfaces:** JetBrains Mono
- All three are OFL-licensed and **bundled in the APK** (no downloadable fonts, no Google Fonts provider, consistent with §11a)
- Respects the system font scale up to 200%. The clock scales down before it wraps

### 2b.3 Colour and theme packs
- A theme pack is a palette using the **org theme registry's 20-role schema** (`bg`, `surface`, `panel`, `border`, `line`, `selection`, `hover`, `white`, `muted`, `text_primary`, `accent_hot`, `accent_alt`, `acid_green`, `hot_pink`, `purple`, `cyan`, `orange`, `red`, `success`, `warning`, `error`). Ghostdeck maps the roles: `bg` → ground, `accent_hot`/`accent_alt` → the two orbs and accents, `success`/`warning`/`error` → tile status
- **Bundled packs (MVP):** **Ghostdeck** (default: `bg #07060d`, accent `#a78bfa`, alt `#2dd4bf`), **Cybercore Vortex**, **Terminal Ghost** (`terminal-ghost-alpha`), and **Cryo Terminal Blue**, copied from `cybercoretech.github.io/data/themes/**` at build time by a Gradle task and kept in sync by CI. **No network at runtime**
- **More packs:** import any registry theme JSON through the Storage Access Framework. *(Claude's call: a "fetch the latest packs" option belongs in the Link plugin, never in the core.)*
- Per-deck accent overrides the pack's accent (§7.1). Material You dynamic colour is still offered as a pack called "System"
- **Contrast rule:** text on glass must meet **4.5:1** against the darkest point of the orb behind it. If a pack fails, the renderer raises surface opacity automatically. A unit test checks every bundled pack

### 2b.4 Signature motion
| Move | Where | Behaviour | "Remove animations" fallback |
|---|---|---|---|
| **Phase shift** | Deck switch, theme change | 450–550 ms chromatic split (red/cyan offset, slight skew) that settles on the new deck | 150 ms crossfade |
| **Materialize** | App/widget appearing; launch | Fade in from blur and 0.86 scale; dissolve on launch, matching the launch animation | Instant |
| **Ghost trail** | Radial menu flick | A fading 3-step trail follows the thumb to the slot; the slot flashes accent | Slot highlight only |
| **Terminal stream** | Agent replies, "Why this?" | Text appears line by line with a block caret | Text shown at once |
| **Consent glow** | Consent chips while data is being sent | Green pulse on each chip being sent | Static "sending…" label |
| **Haunt** | Widgets surfacing (Void) | Drifts in 40 dp from the edge with blur, fades out the same way | Plain fade |

**Motion rules:**
- No motion longer than 600 ms. Nothing loops on the home screen except the very slow orb drift (≥ 12 s period), which stops on battery saver and on reduced motion
- 60 fps budget on a Pixel 6a-class phone. Blur is the expensive part: use `RenderEffect` blur on API 31+, and **a pre-blurred static wallpaper layer** on API 30 (no live blur)

### 2b.5 Where it lands in the milestones
- **M1:** the theme engine (packs, roles → tokens, the contrast check), Phantom Glass surfaces, Materialize, fonts
- **M2:** Haunt + Void surfacing
- **M3:** Terminal Ghost Command Bar + Terminal stream
- **M4:** Phase shift
- **M5:** Ghost trail
- **v0.2 (Link):** Consent glow

## 3. Goals, non-goals, anti-goals

### Goals (MVP)
1. Good enough to daily-drive: fast, stable, and a full replacement for the stock launcher.
2. Context Decks switch correctly and explain *why* ("Car: connected to `Mazda BT`").
3. The Command Bar is the fastest way to reach anything on the phone.
4. A documented plugin contract (commands + tiles) that a second app (Nightwatch) actually implements.
5. Shipped on the cybercoretech F-Droid repo with reproducible builds.

### Non-goals (MVP; see the roadmap in §12)
- Scripted tiles (Vexlang/Lua), calendar-based or geofence triggers
- Notification inbox / triage, mindful-use friction, spatial canvas, desktop theme sync
- Google Play release, cloud sync, accounts
- Cloud AI in the MVP (Link, Mind and the agent bridge are v0.2/v0.3; the MVP ships only AI Handoff, which needs no network)
- Replacing the system recents screen or gesture animations (Android doesn't allow it, see §5)

### Anti-goals (shipping any of these counts as failure, even if everything works)
- **Any** network access, analytics, crash reporting SDK, or ads in the core APK
- Feeling slower than the stock launcher (jank on swipe home, slow cold start)
- Decks switching unexpectedly with no explanation ("magic" the user can't follow)
- Any AI feature that sends data without showing consent chips first, or that runs an action without a tap
- Asking for permissions up front. Each permission is requested at the moment the feature that needs it is turned on
- Settings sprawl in the style of Nova's 400 toggles. Every setting has to earn its place

---

## 4. Platform and stack (Claude's call; matches the existing Cybercore Android apps)

- **Language/UI:** Kotlin, Jetpack Compose, Material 3 (with the Cybercore theme, see §9)
- **SDK:** `compileSdk 36`, `targetSdk 36`, **`minSdk 30`** (Android 11; same as CyberVault) **(owner confirmed)**
- **JVM:** 17 · **Coroutines/Flow** for state · **Room** for layout/deck storage · **DataStore** for preferences
- **No Rust/uniffi in the MVP.** Everything is pure Kotlin. Rust only comes in later if Vexlang scripting lands
- **No Google Play Services**, no Firebase, no proprietary dependencies (an F-Droid requirement)
- **Build:** Gradle KTS with a version catalog, mirroring `~/Devspace/Android/CyberVault`
- **License:** GPL-3.0-or-later **(owner, 2026-11-07)**
- **Identity (owner):** repo `cybercoretech/ghostdeck` (public, created 2026-11-07), applicationId `net.cybercoretech.ghostdeck`, site `ghostdeck.pages.dev`

### Module layout
```
:app            Activities, Compose UI, DI wiring, launcher role
:core:model     Pure-Kotlin data: Deck, Layout, Item, Rule, Tile, Command
:core:data      Room DB, DataStore, layout import/export (JSON)
:engine:decks   Rule evaluation + context signal collection (pure-Kotlin core, Android adapters)
:engine:search  Indexing + fuzzy ranking + command parsing
:feature:tiles  Built-in tiles + plugin tile host
:plugin-api     Published contract (constants, schema, tiny helper lib) for third-party plugins
```
`:engine:decks` and `:engine:search` keep their logic free of Android imports so it can be unit-tested on the JVM.

---

## 5. Platform constraints (known up front)

- **Recents and gesture animations:** A third-party default launcher doesn't get Pixel QuickStep integration. The swipe-home animation is the system default and recents stays the system's. **Accept this.** Make our own transitions (deck switch, opening the command bar) great instead.
- **Wi‑Fi SSID** needs `ACCESS_FINE_LOCATION` plus location turned on (Android 10+). Request it only when the user adds a Wi‑Fi rule, and say why in plain words.
- **Bluetooth device names** need `BLUETOOTH_CONNECT` (Android 12+).
- **Toggling system settings directly** (Wi‑Fi, Bluetooth) isn't allowed for apps. Commands open **Settings panels** (`Settings.Panel.*`) or the right settings screen.
- **AccessibilityService: never use it.** It isn't needed and draws policy scrutiny.
- **Private Space (Android 15+)** and **work profile** must be handled properly through `LauncherApps` / `UserManager`, with apps from those profiles badged and separated.
- **The launcher must be the default HOME** for most features. Ask for it through `RoleManager.ROLE_HOME` during onboarding.

---

## 6. Feature spec: Home core

Table stakes. Without these nobody switches.

- **Workspace:** a grid of pages per deck (default 5×6, adjustable 3–8 × 4–10), and a dock with 0–6 slots
- **Items:** app icons, deep shortcuts (`LauncherApps.getShortcuts`), folders, widgets (`AppWidgetHost`, with resize handles), and tiles (§9)
- **Edit mode:** long-press on empty space opens edit; drag to move between pages and the dock; drop on a target to remove or uninstall; app info
- **Icon packs:** support the ADW/Nova `appfilter.xml` format (the most common one). Missing icons fall back to a themed adaptive icon
- **Gestures (defaults; all can be remapped, see §10a):** swipe up opens the Command Bar · swipe down opens the notification shade · **long-press on empty space opens the Radial menu (§10b)**, whose centre item is "Edit home" · pinch out opens the Deck switcher. *Double-tap to lock is left out on purpose: it needs device admin or an AccessibilityService, and §5 rules out both.*
- **Hidden apps:** per deck (see §7) and global
- **Badges:** notification dots through `NotificationListenerService`, opt-in. If it isn't granted, there are no dots and no nagging
- **Wallpaper:** per deck, picked through the system picker or from a file. Scrolling between pages uses the wallpaper offset

---

## 7. Feature spec: Context Decks (the core)

### 7.1 What a deck is
A **deck** is a complete home environment:

| Field | Notes |
|---|---|
| `name`, `icon`, `accent` | e.g. "Car" 🚗, accent `#ff4fd8` |
| `layout` | Its own pages, dock and widgets. A deck can **clone** another deck when it's created |
| `wallpaper` | Optional; leave it blank to inherit |
| `hiddenApps` / `dimmedApps` | Dimmed apps show at 40% opacity with a grayscale filter (useful for "Work: personal apps dimmed") |
| `searchBoost` | Apps/commands ranked higher in the Command Bar while this deck is active |
| `rules` | The triggers that activate the deck (7.2) |

Decks are the owner's **"multiple independent workspaces"**: each one has its own layout, dock, widgets, organisation and appearance (§7.6). They work **fully manually** too (switch from the chip, the radial menu, or `deck work`), so automatic rules are optional, not required.

The MVP ships with **one deck ("Home")** and offers templates: *Personal, Work, Car, Night, Gym, Gaming, Travel*. Templates include rules with blanks to fill in (e.g. "pick your car's Bluetooth device").

### 7.2 Triggers (MVP set)
| Trigger | Signal | Permission |
|---|---|---|
| Time window + weekdays | `AlarmManager` exact-ish alarms at window edges + re-evaluated on resume | none (`SCHEDULE_EXACT_ALARM` not needed; inexact ±1 min is fine) |
| Wi‑Fi SSID connected | `ConnectivityManager.NetworkCallback` | `ACCESS_FINE_LOCATION` (only when this rule is used) |
| Bluetooth device connected | `ACTION_ACL_CONNECTED/DISCONNECTED` | `BLUETOOTH_CONNECT` |
| Charging / power connected | `ACTION_POWER_CONNECTED` / battery sticky intent | none |
| Wired/BT headphones | `AudioManager` device callback | none |
| Manual | User picks a deck | none |

A rule is **an AND of conditions** (e.g. *weekday AND 09:00–17:00 AND Wi‑Fi = `CorpNet`*). A deck can have several rules, which are ORed together.

### 7.3 Resolution
- Decks are kept in a **user-ordered priority list**. The **first deck with a matching rule wins**. "Home" is the fallback and is always last
- **Manual pin:** "Pin *Gym* for 1 h / until 6 pm / until next change / indefinitely". A pin outranks rules until it expires
- **Hysteresis:** Bluetooth and Wi‑Fi *disconnect* signals wait **30 s** before switching (stops flapping in a car or a lift). *Connect* signals switch immediately
- **No switch mid-drag or in edit mode.** Queue it and apply it when the user leaves edit mode
- **No switch while the launcher is in the foreground** unless the user turns that on. Otherwise, apply on the next `onResume` with a short crossfade (default: apply on resume)

### 7.4 Explaining a switch (required, see the anti-goals)
- The **deck chip** at the top of page 1 (can be hidden) shows the deck icon and name. Tapping it opens a sheet: *"Car is active because: connected to `Mazda BT` (since 08:12). Next: Home when it disconnects."* plus buttons to Pin / Switch / Edit rules
- A **deck history** log (last 50 switches, stored locally) is reachable from that sheet

### 7.5 Evaluation model
- A launcher process lives a long time. Signals are collected through registered callbacks while the process is alive, plus manifest-declared receivers for power and boot. **No foreground service, no wakelocks, no polling**
- Evaluation is a pure function `resolve(decks, signals, pin, now) -> DeckId + Reason`, unit-tested with a table of scenarios

### 7.6 Adaptive interface (more than the wallpaper; rev 3)
Each deck carries an **appearance profile**, and L2's `AppearanceAdapter` combines it with system and context signals into the final look:

| Input | What adapts |
|---|---|
| **Deck profile** (user-set) | Grid density, icon size, labels on/off, dock size, corner style, motion level, accent, typeface (default / mono) |
| **System accessibility** (`fontScale`, *remove animations*, high-contrast text, bold text, TalkBack running) | Bigger targets and spacing, labels forced on, motion reduced to fades, a high-contrast palette, a simpler single-page layout suggested when TalkBack is on |
| **Time of day** | Optional "dusk" shift: dimmer surfaces, warmer accent, lower wallpaper brightness after sunset (times computed on-device from the time zone, no location) |
| **Context** | Car deck template: extra-large targets, no widgets except nav/music, swipe-only gestures. Power-saver on: animations off, live tiles slowed down |
| **Ambient light** *(optional, Claude's call: off by default)* | Contrast boost in bright sunlight, via the light sensor |

Every adaptation is **visible and reversible**. The deck sheet shows "Appearance: Work profile + Large text (system) + Dusk", and each part can be switched off. No hidden UI changes.

---

## 8. Feature spec: Command Bar

### 8.1 Opening and layout
- Swipe up from home, tap the search pill (optional on the dock), or use a hardware keyboard shortcut (any letter key on home starts a search)
- A full-screen sheet: input at the **bottom** (thumb reach), results above it, sorted nearest the input first. With an empty query it shows **frecent items + "suggested for this deck"**

### 8.2 Result sources (MVP)
| Source | Examples | Notes |
|---|---|---|
| Apps (all profiles) | `sig` → Signal | fuzzy, initials (`gm` → Google Maps), handles diacritics |
| App shortcuts | `new tab`, `compose` | via `LauncherApps` |
| Contacts | `call mom`, `text alex` | **Opt-in** `READ_CONTACTS`; the `call` verb opens the dialer pre-filled (`ACTION_DIAL`, no `CALL_PHONE`) |
| Calculator | `14*23`, `20% of 85`, `sqrt(2)` | local expression parser |
| Unit conversion | `5 mi in km`, `72f in c` | fixed table, no currency (that would need network) |
| Timers/alarms | `timer 10m`, `alarm 6:30` | `AlarmClock.ACTION_SET_TIMER/ALARM` intents to the clock app |
| Settings | `wifi`, `bluetooth`, `battery`, `dnd` | opens panels / settings screens |
| Deck commands | `deck car`, `pin gym 1h`, `unpin`, `why` | built in |
| Web search | `?best ramen` or fallback row "Search web for …" | `ACTION_WEB_SEARCH` / browser intent. **The launcher itself never touches the network** |
| Plugin verbs | `nw status`, `vault lock` | §10 |

### 8.3 Ranking
- Score = fuzzy match quality × **frecency** (half-life 7 days) × deck `searchBoost`
- Pressing Enter runs the top result. `Tab`/swipe on a row shows its secondary actions (app info, uninstall, pin to deck)
- Target **< 16 ms** per keystroke for apps/shortcuts/commands on a mid-range phone. Contacts are allowed to arrive one frame later

### 8.4 Grammar (kept small)
`<verb> <args>` where a verb is built in or from a plugin. Anything that doesn't parse becomes a fuzzy search. Verbs and their arguments get autocomplete hints.

### 8.5 Intelligent search (on-device; no model needed in the MVP)
The aim is search that feels smart, built from deterministic, explainable parts that run in microseconds. "AI" is never used as a label for something that isn't.

| Layer | What it does | Example |
|---|---|---|
| **Typo tolerance** | Damerau-Levenshtein ≤ 2 on tokens, plus keyboard-adjacency weighting | `spotfy`, `whtsapp` |
| **Aliases from metadata** | Index the app label **and** package name segments, shortcut labels, and `ApplicationInfo.category` | `gcam` / `camera` → Pixel Camera |
| **Intent lexicon** | A bundled, editable synonym → capability map (≈ 300 entries in English at launch). Each capability resolves to installed apps by intent-filter probing (`ACTION_IMAGE_CAPTURE`, `ACTION_SEND` mime types, `ACTION_VIEW geo:` etc.) | `pay` → wallet apps · `map` / `directions` → apps handling `geo:` · `scan` → camera/scanner · `music` → apps with a media-browser service |
| **Natural-language actions** | A small rule-based parser for common phrases → the verbs from §8.2 | `text mom I'm late` → SMS intent to the matching contact with the body pre-filled · `wake me at 6:30` → alarm · `remind me in 20 min` → timer |
| **Context ranking** | Frecency × deck boost × **time-of-day pattern** (a local per-app histogram in 24 hourly buckets) × "just connected" boost (Bluetooth car → nav/music go up) | An empty query in the morning shows your morning apps |
| **Learned corrections** | When you type `x` and pick result #3, `x → that app` gets a boost (local, capped, decays) | Typing `mail` once and picking Proton Mail makes it the top result |
| **Explainable** | Long-press a result → *"Why this?"* shows the score breakdown | Fits the "no hidden magic" anti-goal |

Every learned signal is stored locally, can be seen in **What Ghostdeck knows** (§11a.3), and can be reset or switched off.

### 8.5a Smart Groups (intelligent app organisation; rev 3)
Apps get grouped automatically, and **the user can override everything**.

- **Group kinds:**
  - **Purpose:** from `ApplicationInfo.category` plus the intent lexicon (Social, Media, Finance, Navigation, Photos, Tools, Games…)
  - **Usage:** Daily / Weekly / Rarely / Never (60 d), from local usage stats
  - **Deck:** apps that matter in this deck (from `searchBoost` + deck usage)
  - **Rules (user-defined):** a tiny filter language, e.g.
    `group "Money": purpose=finance OR label~/bank|pay|wallet/i`
    `group "Cybercore": package^=net.cybercoretech`
- **Where they appear:**
  - The `apps` view in the Command Bar (grouped, collapsible)
  - Optionally as **Smart Folders** on the home grid, which update live as apps are installed or removed
- **Overrides are sticky:** pin an app into a group, exclude it, rename or reorder the group. An override always beats inference. A small "auto" badge marks anything inferred, so the user can tell it apart from their own choices
- **New installs:** "Signal installed → added to *Social*. [Undo] [Put on home]"
- Grouping runs in L2 (`Grouper`), costs nothing at render time, and re-runs only on package or usage changes

### 8.6 Optional offline AI (after the MVP: "Ghostdeck Mind" plugin APK, v0.2)
- A **separate opt-in APK** that bundles a small on-device model (a sentence-embedding model of about 25–90 MB running on ONNX Runtime/LiteRT). It adds **semantic search** ("something to edit photos") and fuzzy natural-language commands
- It talks to Ghostdeck through the plugin API (§10), so **the core never loads ML code or touches the network**. The Mind APK also has **no INTERNET permission**, because the model is bundled rather than downloaded
- **No Gemini Nano/AICore dependency:** it's Google-only, limited to certain devices, and not acceptable on F-Droid. It could be offered later as an optional backend inside Mind for users who want it
- Search in the MVP must be designed so Mind can add a scorer without refactoring (`interface ResultScorer`)

### 8.7 AI integration: four layers (rev 4)

**Principle:** AI is **opt-in, layered, and visible**. The core APK still has **no INTERNET permission**. Cloud access lives only in a separate plugin you choose to install, using your own keys, and nothing is sent without you seeing exactly what.

| Layer | Name | What it does | Providers | Network | When |
|---|---|---|---|---|---|
| **0** | **Handoff** | `@claude <text>` sends the text to that installed app. Uses `ACTION_SEND` (text/plain), or `ACTION_PROCESS_TEXT` where the app supports it; the app is targeted by package | Claude, ChatGPT, Gemini, Microsoft Copilot, Perplexity, Le Chat, and any installed app that accepts shared text (discovered by intent query) | **None** in Ghostdeck | **MVP, M3** |
| **1** | **Mind** | On-device model: semantic search, routing, short summaries, offline voice (§8.6) | Local | None (Mind APK has no INTERNET) | v0.2 |
| **2** | **Link** | Separate opt-in **plugin APK with INTERNET**. Chat with your own API keys, streaming replies in the Terminal Ghost panel | Anthropic (Claude), OpenAI, Google Gemini API, Mistral, OpenRouter, and any **OpenAI-compatible endpoint**, including **your own Ollama on darkbox over Tailscale** | Only in Link | v0.2 |
| **3** | **Agent bridge** | Link connects to **MCP servers** you configure, and to **desktop agents** through Nightwatch: start a task on blackbox and follow its progress as a tile | MCP servers; Claude Code, Codex, **Antigravity** and other desktop agents (bridge only: Antigravity has no phone API we know of) | Only in Link (+ Nightwatch) | v0.3 |

*Copilot is Layer 0 only. As far as we know there's no general consumer chat API for direct calls.*

#### 8.7.1 How agents behave inside Ghostdeck (all layers)
1. **Consent chips.** Before anything is sent (Layers 2–3; Layer 0 shows the text being handed off), the panel lists exactly what's attached as chips: `clipboard`, `next 3 events`, `deck: Work`, `nightwatch alerts`. Tap a chip to remove it. **Nothing is attached by default except the typed text**; context is added with `+ context` or by a per-agent default the user sets. Chips glow while sending (§2b.4). Every send is written to a **local send log** (what, to whom, when; never the reply body unless the user keeps it), shown in the Privacy Ledger
2. **Propose, don't act.** Agents get Ghostdeck's `Action` type (§2a) as their tools. Replies can contain **proposed actions** (`pin Focus 1h`, `set timer 10m`, `open Maps to …`, `nw restart warnflare`), shown as buttons under the reply. **Nothing runs without a tap.** Destructive actions (uninstall, deleting a deck) are never offered as agent tools
3. **Agent-designed decks.** "Build me a travel deck" → the agent returns a config fragment in the §11 JSON schema → Ghostdeck validates it and shows a **visual diff preview** (added/removed apps, widgets, rules) → Apply / Discard. Applying takes a snapshot first (§11), so it can be undone
4. **Prompt tiles.** Any prompt can be pinned as a tile with a schedule ("Morning brief" at 07:00), a provider and attached context chips (fixed when the tile is created). The result is cached and shown with "updated 07:00". Runs only through Link, and only when it's installed and the key is present
5. **Side-by-side.** `@claude @gpt <text>` sends the same prompt (and the same chips) to two providers and shows the answers in two columns
6. **Routing with "Why this?".** With Mind installed, plain `@ <text>` lets the local model pick a destination (stay local / the user's default cloud provider), and shows why ("routed → cloud · needs calendar reasoning"). Without Mind, `@` goes to the user's default provider
7. **Ghostdeck as an agent surface (v0.3).** Ghostdeck's safe actions are exposed (a) through Android's app-function APIs for assistants where the platform offers them, and (b) as an **MCP server hosted by Link**, so the user's own agents (e.g. Claude Code on blackbox) can propose launcher actions. They still go through tap-to-confirm on the phone

#### 8.7.2 Link plugin security
- **Keys** live in the Android Keystore (AES-GCM wrapped), per provider. They're never exported in the §11 config file, never logged, never shown again after entry (only the last 4 characters)
- **Exact hosts only:** Link talks only to the endpoints the user has enabled (`api.anthropic.com`, `api.openai.com`, …, or their own URL). A network security config with cleartext disabled, except an explicit opt-in for Tailscale `100.64.0.0/10` addresses
- **No telemetry, no analytics,** same tracker gate as the core (§11a.2). Link is reproducibly built and on F-Droid. F-Droid will tag it with the "NonFreeNet" anti-feature when cloud providers are enabled, and we say so plainly
- The Plugins screen shows Link's INTERNET permission clearly (§11a.2), so it's obvious where network access lives

---

## 9. Feature spec: Universal Widget System

### 9.0 One item model for every widget
Everything that shows live content on the grid is a **`WidgetItem`** with one shared frame, whatever its source:

| Source | Rendered by | Theming |
|---|---|---|
| Android **AppWidget** | `AppWidgetHost` (`RemoteViews`) | Frame only: corner radius, padding, optional background plate, and per-deck opacity. *The contents can't be restyled. That's an Android limit and we say so in the UI* |
| **Ghostdeck tile** (built in) | Ghostdeck (Compose) | Fully themed |
| **Plugin tile** (§10) | Ghostdeck, from `TileState` | Fully themed |
| *Script tile (v0.2)* | Ghostdeck, from the script plugin | Fully themed |

**Shared behaviour across every source:**
- **Same resize handles**, snapping and drag.
- **Can live in folders, and be pinned into Command Bar results**: typing `weather` can show the weather widget inline as the top result.
- **Visibility per deck**: one widget instance can appear on several decks. *Claude's call: an instance is shared, not cloned, so it keeps its configuration.*
- **One picker** for all sources, with search, previews and source badges.

### 9.0.1 Widget Stacks
- Any widget can be dropped onto another to form a **stack** (swipe vertically to cycle; dots show the position)
- **Smart rotation (on by default, can be switched off):** the stack surfaces the member that best fits the context. Next event shows within 1 h of a meeting, music shows when headphones connect, the darkbox tile shows when it reports WARN/ERROR. Uses the same signals as decks, all local, and is explainable via long-press "Why this?"
- A stack can be **pinned** to one member

### 9.0.2 Performance guardrails
- AppWidgets on pages that aren't visible are told they're hidden (`AppWidgetHostView` visibility) and get no updates until shown. Stacks only inflate the visible member plus one neighbour

### 9.1 Tiles (Ghostdeck's own widgets)

A **tile** is a home-screen item (1×1, 2×1, 2×2, 4×1, 4×2) drawn by the launcher in **one consistent style**. It isn't an `AppWidget`, so tiles always match the theme and are cheap to render.

### 9.1.1 Built-in tiles (MVP)
- **Clock/Date** (several styles, including a monospace "terminal" style)
- **Next alarm**
- **Battery** (level, charging, time-to-full when the system provides it)
- **Deck** (current deck + reason; tap opens the deck sheet)
- **Quick note** (single scratch note stored locally; tap to edit)
- **Next event** (opt-in `READ_CALENDAR`)

### 9.1.2 Plugin tiles
Fed by plugin apps through the contract in §10. The data model is deliberately small:
```
TileState {
  title: String          // "darkbox"
  value: String          // "12/12 up"
  subtitle: String?      // "load 0.42"
  status: OK|WARN|ERROR|NEUTRAL   // drives the accent colour
  icon: Uri?             // content:// from the plugin
  tapIntent: PendingIntent?
  updatedAt: Long
}
```
The launcher draws it. A plugin cannot inject UI.

### 9.2 Theme
See **§2b** (design language, theme packs, motion). Tiles use Phantom Glass surfaces. Tile *values* can switch to JetBrains Mono as a per-tile option (the "power tool" look).

---

## 10. Plugin API v0 (`:plugin-api`)

**Goal:** other apps (Nightwatch first, CyberVault next) add commands and tiles **without the launcher gaining any permissions**.

### 10.1 Discovery
A plugin declares an exported component with this intent filter, plus meta-data pointing at an XML descriptor:
```xml
<service android:name=".LauncherPlugin" android:exported="true"
         android:permission="<applicationId>.permission.BIND_PLUGIN">
  <intent-filter><action android:name="<applicationId>.PLUGIN_V0"/></intent-filter>
  <meta-data android:name="<applicationId>.plugin" android:resource="@xml/launcher_plugin"/>
</service>
```
The descriptor lists **verbs** (name, description, argument hint) and **tile types** (id, label, sizes, min refresh interval ≥ 15 min).

### 10.2 Transport
- **Bound service with a small AIDL interface:** `runCommand(verb, args): CommandResult`, `getTile(tileTypeId, instanceId): TileState`, plus a callback the plugin can use to **push** tile updates
- `CommandResult` = toast text | list of result rows (each with a `PendingIntent`) | open intent
- Timeouts: command 2 s, tile fetch 5 s. On timeout the launcher shows a stale tile with an "updated X ago" label

### 10.3 Trust and safety
- The user must **enable each plugin explicitly** in settings. The screen shows the package name and signing-cert SHA-256
- Plugin verbs are **namespaced** (`nw status`) to avoid clashes. Built-in verbs always win
- The launcher never sends the plugin anything except the verb, its args and the tile instance ID

### 10.4 Deliverable
- The contract lives in `:plugin-api` and is published as a small AAR (via JitPack or GitHub Packages; **decide at release**) plus `docs/plugin-api.md`
- **Reference plugin:** a Nightwatch PR that adds `nw status` / `nw restart <svc>` verbs and a "Host status" tile. That proves the network-lives-in-the-plugin model

---

## 10a. Power-User Layer

**Principle:** power comes through **text, files and APIs**, not more toggles. The settings screen stays short. The depth lives in the Command Bar, the config file and the automation API.

### 10a.1 Gestures (remappable)
- Slots: swipe up / down / left-edge / right-edge, two-finger swipe up/down, double-tap, pinch in/out, long-press dock, and tapping the deck chip
- Any slot can run **any action**: open the Command Bar, switch to or pin a deck, a widget stack, a specific app or shortcut, a macro, a plugin verb, or open the notification shade
- Gestures can be set **per deck** (e.g. in Car, swipe up opens Maps)
- *Double-tap to lock stays unavailable (§5). The slot exists for other actions*
- **Combos and contextual bindings (rev 3; the owner's "gesture automation"):**
  - **Combos:** two-finger double-tap, swipe-then-hold, edge-swipe-and-return, and **drawn glyphs**: draw a letter on home (`m` → Maps, `w` → pin Work). Glyphs are matched on-device by a simple stroke recogniser (the $1/$P family; no ML)
  - **Conditional bindings:** a binding can carry a condition from the deck trigger vocabulary (§7.2): *"swipe up = Spotify **when headphones are connected**, otherwise Command Bar"*
  - **Bound to a trigger, not a gesture:** "when Bluetooth `Mazda BT` connects → run macro `drive`". Uses the same rule engine as decks, but runs an action instead of switching deck. *(Claude's call: this is capped to launcher-side actions, so it doesn't turn into a Tasker clone. Tasker integration (§10a.4) covers the rest)*

### 10a.2 Aliases and macros
- **Alias:** `alias gh = open github` · `alias dark = nw status darkbox`
- **Macro:** a named sequence of actions, run as a verb:
  ```
  macro focus:
    pin work 2h
    panel dnd
    open com.todoist
  ```
- Steps are limited to existing verbs and actions: no loops, no conditions (that's scripting, which comes in v0.2). They're edited in a monospace editor sheet with syntax colouring and validation, and stored in the config file

### 10a.3 Hardware keyboard and DeX/desktop mode
- Typing on home opens the Command Bar · `Ctrl+1..9` switches deck · `Ctrl+E` edit mode · `Ctrl+,` settings · arrow keys and Enter move through results
- It has to work properly on tablets, foldables and Samsung DeX-style desktop modes (large layouts with resizable grid density)

### 10a.4 Automation API (Tasker, Automate, `adb`)
- **Inbound broadcasts / intents** (guarded by a signature-or-user-granted permission `<applicationId>.permission.CONTROL`):
  `SWITCH_DECK(id)`, `PIN_DECK(id, duration)`, `RUN_COMMAND(text)`, `RUN_MACRO(name)`, `REFRESH_TILE(id)`
- **Outbound broadcast** when the deck changes (`DECK_CHANGED` with id + reason) so Tasker can react (e.g. Car deck → turn on media volume)
- **Tasker/Locale plugin actions** (the standard `com.twofortyfouram.locale` contract) for "Switch deck" and "Run command" *(Claude's call: the format is open and needs no Tasker SDK)*
- `adb shell am broadcast …` works for scripting from the desktop. Documented in `docs/automation.md`
- **Off by default.** Enabled in Settings → Automation, which lists the apps that have been granted control

### 10a.5 Config file
- The JSON from §11 doubles as the **power-user config**: decks, rules, gestures, aliases, macros, tiles. Edit it on the desktop, import it, done
- *Claude's call:* the export also gets a **TOML** form (`ghostdeck.toml`) for hand editing. JSON stays the canonical schema, and TOML is converted to and from it

### 10a.6 Small power features in the MVP
- Per-app: rename the label, swap the icon (from a pack or an image), hide it, change badge behaviour
- Bulk edit in the drawer-free "All apps" list inside the Command Bar (`apps` verb): multi-select → hide / move to deck / add to folder
- `apps --unused 60d` lists apps not opened in 60 days (local usage stats) → one-tap uninstall (an early slice of the "graveyard")

---

## 10b. Radial Command Menu (rev 3)

**Long-press anywhere on empty home space** (or a remappable gesture) opens a radial menu **centred on the touch point**, clamped so it stays on screen.

- **Structure:**
  - Inner ring of **8 slots**, an optional outer ring of 8 more
  - Any slot can be a **submenu** (one level deep in the MVP)
  - The centre slot is **Edit home** by default
- **Slot contents:** any `Action` (§2a): an app, shortcut, contact (call/text), deck switch or pin, macro, plugin verb, settings panel, widget-stack peek
- **Marking-menu behaviour (the important part):**
  - Novices press, wait for the menu to appear, and lift on a slot
  - Experts **press-and-flick** in a direction without waiting. The action fires on release and the menu never draws
  - Over time this builds muscle memory: *flick up-right = call Mom*
  - Draw delay of 250 ms by default, adjustable
- **Per deck:** each deck can have its own radial layout (the Car radial = nav, music, call, message)
- **Smart slots (optional):** up to 2 slots can be set to "suggested". They're filled by L2 ranking for the context and marked so the user knows they change
- **Feel:** haptic tick on each slot crossing, scale-in animation (a plain fade when *remove animations* is on), labels under icons
- **Accessibility:** with TalkBack the radial becomes a linear list. Slots can be reached with the keyboard (arrow keys)
- **Left-handed mode** mirrors the default arrangement

## 11. Layout files, backups and undo

- **Export/import** the whole config (decks, layouts, rules, tiles, preferences) as **versioned JSON** (`"schema": 1`) through the Storage Access Framework. The format is documented. Human-readable on purpose ("dotfiles for your phone")
- **Automatic local snapshots:** before every destructive edit and once a day; the last **20** are kept. *Settings → History* lets the user preview and restore one (a light "undo timeline")
- Imports validate the schema; missing apps become greyed placeholders that remap themselves if the app is installed later

---

## 11a. Privacy Charter (enforced by the architecture, not only promised)

### 11a.1 The Charter (published in the app, the README and the site, word for word)
1. **No advertisements.** Ever, in any build.
2. **No behavioural tracking.** Ghostdeck learns from your use **only on your device**, for you, and shows you everything it has learned (11a.3).
3. **No selling or sharing of data.** There is no server to send it to.
4. **Local-first.** All settings, layouts and learned signals live on the device. Export them yourself if you want them anywhere else.
5. **Optional AI, on-device by default.** Cloud AI only through the separate Link plugin, with your own keys, and nothing is sent without you seeing exactly what.
6. **Transparent permissions.** Every permission is requested at the moment it's needed, with a plain-language reason, and listed in the Privacy Ledger.
7. **No account.** Nothing to sign up for, ever.

> **Honesty note (Claude's addition):** promise 2 is phrased carefully on purpose. Smart ranking *is* a model of behaviour. What makes it acceptable is that it stays on the device, can be inspected, reset and switched off. The charter says that plainly instead of claiming "we learn nothing".

### 11a.2 How it's enforced
| Mechanism | Detail |
|---|---|
| **No INTERNET permission** | Core and Mind APKs. A **CI gate** runs `aapt2 dump permissions` on every build and **fails** if `android.permission.INTERNET` appears. This also catches dependencies that sneak it in through manifest merging. *(`ACCESS_NETWORK_STATE` is allowed: it only reads connection state for Wi‑Fi deck rules and can't send data)* |
| **Dependency allow-list** | No analytics, crash-reporting, ads or Google Play Services libraries. A CI check fails on known tracker packages (Exodus Privacy's tracker signatures) |
| **Reproducible builds** | F-Droid verifies that the published APK matches the source |
| **`allowBackup` policy** | Android auto-backup to Google Drive is **off** (`android:allowBackup="false"`, plus data-extraction rules that exclude everything). Backups happen only through the user's own export |
| **Data at rest** | App-private storage. The learned-signals DB can be encrypted (SQLCipher) *(Claude's call: plaintext in the MVP because app sandboxing already protects it; encryption as a setting in v0.2)* |
| **Plugins** | The plugin list shows each plugin's own permissions (including whether it has INTERNET), so users can see where network access lives |
| **AI (rev 4)** | Handoff (Layer 0) needs no network. Link is the only Ghostdeck component with INTERNET, and it's a separate opt-in APK. Consent chips before every send, a local send log in the Privacy Ledger, keys in the Keystore and never in exports (§8.7) |

### 11a.3 In-app transparency screens
- **Privacy Ledger:** every permission Ghostdeck holds, why, which feature uses it, when it was granted, and a toggle or deep link to revoke it. Includes plugins and automation grants
- **What Ghostdeck knows:** the learned signals, readable: top apps per time of day, learned search corrections, frecency list, deck history. Each section has **Reset**; there's a global **Forget everything** and a **Learning off** switch (falls back to plain alphabetical/fuzzy ranking)
- **First run:** a single screen with the 7 charter lines and *no* permission prompts. Permissions come later, when the feature that needs them is turned on

---

## 12. Roadmap after the MVP (only so we don't paint ourselves into a corner)

1. **v0.2: Activity Center + Mind + Link**
   - **Ghostdeck Link** (§8.7 Layer 2): bring-your-own-key chat with Claude, OpenAI, Gemini, Mistral, OpenRouter and OpenAI-compatible endpoints (Ollama@darkbox). Consent chips, proposed actions, agent-designed decks, prompt tiles, side-by-side
   - **v0.3** adds the Agent bridge (MCP + desktop agents via Nightwatch) and Ghostdeck-as-agent-surface (§8.7 Layer 3, §8.7.1 item 7)
   - **Unified Activity Center** (owner's idea, moved up from v0.3): one customisable sheet or page for notifications, missed calls, messages, reminders and upcoming events
   - Activity sources:
     - `NotificationListenerService`. **Missed calls and messages come from their notifications, not from `READ_CALL_LOG`/`READ_SMS`.** Those permissions are restricted on Play and too invasive for the charter
     - `CalendarContract` for events
     - Plugin-provided items, such as Nightwatch alerts
   - Features: priority people (starred contacts), per-app filtering, People / Waiting on me / Noise lanes, snooze, digests ("social at 18:00"), all-local rules. It's an L3 surface over an L2 "activity ranker"
   - **Ghostdeck Mind** (offline AI plugin, §8.6), plus **offline voice commands** inside Mind (whisper.cpp/Vosk small model; mic used only while held, never always listening). Voice output goes through the same `Action` pipeline as the Command Bar
   - Scripted tiles as an **optional plugin APK** (Vexlang embedded via Rust/uniffi, Lua as the fallback)
   - Calendar-event trigger. Encrypted signals DB. More languages in the intent lexicon
2. **v0.3** Location triggers (on-device, no GMS). An Activity Center tile/stack member
3. **v0.4** Mindful mode (friction on chosen apps, weekly local report). App graveyard
4. **Later** Desktop theme sync with Omarchy over Tailscale (as a plugin). Spatial canvas mode. Clipboard shelf

The data model must leave room for these: tiles are an abstract item type, triggers are a sealed class open to extension, and plugin API v0 is versioned.

---

## 13. Quality bars

| Area | Target |
|---|---|
| Cold start (process dead → home drawn) | < 400 ms on a Pixel 6a-class device |
| Return to home | No dropped frames (no recomposition storms: stable keys, `remember`, baseline profile) |
| Command Bar keystroke → results | < 16 ms (apps/commands) |
| Memory (resident) | < 120 MB |
| Battery | Zero work while the screen is off except receivers handling OS broadcasts. No wakelocks, no services |
| Crash-free | No crash in two weeks of the owner daily-driving it |
| Accessibility | TalkBack labels on every item, minimum 48 dp targets, honours font scale up to 200%, deck switches announced |
| Tests | JVM unit tests for `resolve()` (scenario table), search ranking, the calculator, JSON import/export round-trip. Compose UI tests for edit mode and the command bar. An instrumented smoke test on the API 36 AVD |
| Release | Reproducible build, signed APK, published on `cybercoretech/fdroid`, with fastlane metadata |

**Testing hygiene (from earlier Cybercore Android work):** always use `adb -s emulator-5554`, because the owner's phone is often plugged in. Never `pkill -f`.

---

## 14. Decisions only the owner can make

All decided by the owner on 2026-11-07:
1. **Name:** Ghostdeck. Repo `cybercoretech/ghostdeck`, applicationId `net.cybercoretech.ghostdeck`, site `ghostdeck.pages.dev` ✅
2. **License:** GPL-3.0-or-later ✅
3. **minSdk:** 30 ✅
4. **Google Play:** later (after F-Droid). The MVP must stay Play-policy-clean: no AccessibilityService, no call-log/SMS permissions, and a declared rationale for every permission ✅
5. **Reference plugin:** Nightwatch ✅

Still open: none that blocks planning.

## 15. Left to the builder

- Exact Compose architecture (MVI/UDF style is fine), DI choice (manual or Hilt; Hilt is fine on F-Droid)
- Visual design of the chip, sheets and the command bar, within the theme rules in §9.2
- Fuzzy-match algorithm (fzf-style scoring recommended)
- Icon-pack parser details, widget resize UX details
- Exact snapshot retention mechanics

## 16. Milestones

| # | Milestone | Done when |
|---|---|---|
| M0 | Privacy rails | Repo, CI with the INTERNET/tracker gate, `allowBackup=false`, charter text. **Before any feature work** |
| M1 | Home core + design base | Can be set as default HOME; grid, dock, folders, edit mode, icon packs, Private Space; **theme engine with the 4 bundled packs + contrast test, Phantom Glass surfaces, bundled fonts, Materialize (§2b)** |
| M2 | Universal widgets | `WidgetItem` model, AppWidget hosting, built-in tiles, stacks (manual), unified picker, **Void surfacing + Haunt** |
| M3 | Intelligent Command Bar | §8.2 sources, typo tolerance, intent lexicon, NL actions, context ranking, "Why this?", **Smart Groups + Smart Folders**, **Terminal Ghost styling + Terminal stream, AI Handoff (`@claude`, `@gpt`, `@gemini`, `@copilot` … to installed apps; §8.7 Layer 0)** |
| M4 | Context Decks + Adaptive | Decks, triggers, priority, pins, hysteresis, explainer sheet, history, templates, smart stack rotation, **appearance profiles + accessibility/dusk adaptation**, **Phase shift** |
| M5 | Interaction + Plugins | **Radial menu (marking-menu flicks) with Ghost trail**, remappable gestures, **combos, glyphs, conditional bindings, trigger→action**, aliases/macros, keyboard mode, automation API, AIDL plugin contract, Nightwatch reference plugin PR |
| M6 | Ship | Export/import (JSON+TOML), snapshots, Privacy Ledger + What Ghostdeck knows, a11y pass, perf pass, F-Droid repo, Pages site (Cybercore kit + contact block), Wiki + vault note, README, `AGENT_HANDOFF.md` |

## 17. Success criteria
- The owner replaces their current launcher with this one and keeps it for **2 weeks**
- Over that time, deck switches are judged "right" ≥ 95% of the time, and the explainer always answers "why?"
- At least one plugin (Nightwatch) works end to end with zero network permissions in the launcher
- `aapt dump permissions` on the release APK shows **no `android.permission.INTERNET`**
- Exodus Privacy scan: **0 trackers**
- Intelligent search: ≥ 90% of the owner's first-try queries land on the intended result as #1 (logged locally only during the dogfood period, through an opt-in debug toggle)
