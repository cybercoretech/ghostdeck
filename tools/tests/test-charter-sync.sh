#!/usr/bin/env bash
# The Privacy Charter must match word for word in the spec (§11a.1), README,
# PRIVACY.md and the app's charter.xml (spec §11a.1).
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
spec="$root/docs/superpowers/specs/2026-11-07-ghostdeck-mvp-design.md"

# Markdown list lines 1.–7. → plain text without bold markers.
md_lines() { grep -E '^[1-7]\. \*\*' | sed -E 's/^[1-7]\. //; s/\*\*//g'; }

want="$(awk '/^### 11a.1/{f=1;next} /^### 11a.2/{f=0} f' "$spec" | md_lines)"
[[ $(wc -l <<<"$want") -eq 7 ]] || { echo "spec charter does not have 7 lines"; exit 1; }

fail=0
check() {
    local name="$1" got="$2"
    if [[ "$got" == "$want" ]]; then echo "ok   $name"; else echo "FAIL $name differs from spec §11a.1"; diff <(echo "$want") <(echo "$got") || true; fail=1; fi
}
check README.md "$(awk '/^## 🛡️ Privacy Charter/{f=1;next} /^## /{f=0} f' "$root/README.md" | md_lines)"
check PRIVACY.md "$(md_lines < "$root/PRIVACY.md")"
xml="$root/app/src/main/res/values/charter.xml"
check charter.xml "$(grep -oE '<item>[^<]*</item>' "$xml" 2>/dev/null | sed -E "s#</?item>##g; s#\\\\'#'#g" || true)"
exit $fail
