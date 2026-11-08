# 🛡️ Ghostdeck Privacy

Ghostdeck is a home screen, not a data business. This file is the public form of the Privacy Charter in the [design spec](docs/superpowers/specs/2026-11-07-ghostdeck-mvp-design.md) (§11a). The app shows the same seven lines on first run.

## The Charter

1. **No advertisements.** Ever, in any build.
2. **No behavioural tracking.** Ghostdeck learns from your use **only on your device**, for you, and shows you everything it has learned (11a.3).
3. **No selling or sharing of data.** There is no server to send it to.
4. **Local-first.** All settings, layouts and learned signals live on the device. Export them yourself if you want them anywhere else.
5. **Optional AI, on-device by default.** Cloud AI only through the separate Link plugin, with your own keys, and nothing is sent without you seeing exactly what.
6. **Transparent permissions.** Every permission is requested at the moment it's needed, with a plain-language reason, and listed in the Privacy Ledger.
7. **No account.** Nothing to sign up for, ever.

## How it's enforced

| Mechanism | Detail |
|---|---|
| **No INTERNET permission** | Core and Mind APKs. A **CI gate** runs `aapt2 dump permissions` on every build and **fails** if `android.permission.INTERNET` appears. This also catches dependencies that sneak it in through manifest merging. *(`ACCESS_NETWORK_STATE` is allowed: it only reads connection state for Wi‑Fi deck rules and can't send data)* |
| **Dependency allow-list** | No analytics, crash-reporting, ads or Google Play Services libraries. A CI check fails on known tracker packages (Exodus Privacy's tracker signatures) |
| **Reproducible builds** | F-Droid verifies that the published APK matches the source |
| **`allowBackup` policy** | Android auto-backup to Google Drive is **off** (`android:allowBackup="false"`, plus data-extraction rules that exclude everything). Backups happen only through the user's own export |
| **Data at rest** | App-private storage. The learned-signals DB can be encrypted (SQLCipher) *(Claude's call: plaintext in the MVP because app sandboxing already protects it; encryption as a setting in v0.2)* |
| **Plugins** | The plugin list shows each plugin's own permissions (including whether it has INTERNET), so users can see where network access lives |
| **AI (rev 4)** | Handoff (Layer 0) needs no network. Link is the only Ghostdeck component with INTERNET, and it's a separate opt-in APK. Consent chips before every send, a local send log in the Privacy Ledger, keys in the Keystore and never in exports (§8.7) |

## Checking it yourself

```bash
tools/privacy-gate.sh apk path/to/ghostdeck.apk   # no INTERNET permission, no tracker code
tools/privacy-gate.sh gradle                      # no tracker or Play Services dependencies
```

Found something that breaks the charter? That's a security bug: email security@cybercoretech.net.
