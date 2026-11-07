# 👻 Ghostdeck

**A privacy-first Android launcher that rearranges itself for where you are, works as a power tool, and never phones home.**

> 🚧 **Status: design stage.** The MVP spec is done and approved. Implementation starts with milestone M0 (privacy checks in CI). There's no APK yet.

---

## 🃏 What makes it different

| | Feature | In short |
|---|---|---|
| 🃏 | **Context Decks** | Separate workspaces (Work, Car, Night, Gaming…), each with its own layout, dock, widgets and look. They switch automatically by time, Wi‑Fi, Bluetooth, charging or headphones, and always tell you *why* |
| 🔮 | **Intelligent search** | On-device intent search: typos, synonyms (`pay` finds wallet apps), natural-language actions (`text mom I'm late`), context-aware ranking, and "Why this?" on every result |
| 🧩 | **Universal widgets** | Android widgets, Ghostdeck tiles and plugin tiles share one grid, with **smart stacks** that bring the right widget to the top |
| 🎯 | **Radial command menu** | Long-press anywhere and flick toward an action, without waiting for the menu to appear |
| 🗂️ | **Smart Groups** | Apps grouped automatically by purpose, usage and your own rules. Your overrides always win |
| ⚡ | **Power-user layer** | Remappable gestures, drawn glyphs, aliases and macros, hardware keyboard, Tasker/`adb` API, and a config file |
| 🌗 | **Adaptive interface** | Per-deck appearance profiles that follow accessibility settings, dusk and context |
| 🔌 | **Plugins** | Commands and tiles from other apps (Nightwatch first). Any network access lives in the plugin, never in Ghostdeck |

## 🛡️ Privacy Charter

1. **No advertisements.** Ever.
2. **No behavioural tracking.** Ghostdeck learns only on your device, for you, and shows you everything it has learned.
3. **No selling or sharing of data.** There's no server to send it to.
4. **Local-first.** Settings, layouts and learned signals stay on the device.
5. **Optional offline AI.** A separate opt-in plugin that runs on-device and has no network access either.
6. **Transparent permissions.** Asked for only when needed, explained, and listed in the Privacy Ledger.
7. **No account.** Nothing to sign up for, ever.

**Enforced, not just promised:** the core APK has **no `INTERNET` permission**, and CI fails the build if one ever appears. Builds are reproducible and published on F-Droid.

## 🧱 Architecture

```
L3  Interaction   gestures · radial · command bar · keyboard · automation · plugins
L2  Intelligence  context · deck resolver · search ranking · Smart Groups · adaptation
L1  Visual        decks · grid · icons · widgets/tiles · themes · animation
```
L1 draws, L2 decides (and records why), L3 turns input into one shared set of actions.

## 📐 Design docs

- 📄 [MVP design spec](docs/superpowers/specs/2026-11-07-ghostdeck-mvp-design.md)

## 🛠️ Stack

Kotlin · Jetpack Compose · Material 3 · Room · minSdk 30 / targetSdk 36 · no Google Play Services

## 📜 License

[GPL-3.0-or-later](LICENSE). Ghostdeck stays free and open, and so do its forks.

---

👻 Code & Design by Darkstardevx 🤖 Generated with Claude Code
