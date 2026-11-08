#!/usr/bin/env bash
# Tests for tools/privacy-gate.sh. Each case pipes a fixture into a subcommand and checks the exit code.
set -uo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
gate="$here/../privacy-gate.sh"
fx="$here/fixtures"
pass=0; fail=0

expect() {
    local want="$1"; shift
    local input="${INPUT:-/dev/null}"
    PRIVACY_GATE_TRACKERS="${PRIVACY_GATE_TRACKERS:-}" bash "$gate" "$@" < "$input" >/dev/null 2>&1
    local got=$?
    if [[ "$got" == "$want" ]]; then
        pass=$((pass + 1)); echo "ok   $* < ${input##*/} → $got"
    else
        fail=$((fail + 1)); echo "FAIL $* < ${input##*/}: want $want, got $got"
    fi
}

INPUT="$fx/perms-clean.txt"    expect 0 perms
INPUT="$fx/perms-internet.txt" expect 1 perms
INPUT="$fx/dex-clean.txt"      expect 0 dex
INPUT="$fx/dex-firebase.txt"   expect 1 dex
INPUT="$fx/deps-clean.txt"     expect 0 deps
INPUT="$fx/deps-gms.txt"       expect 1 deps
INPUT="$fx/dex-many-trackers.txt" expect 1 dex
INPUT="$fx/mapping-clean.txt"   expect 0 mapping
INPUT="$fx/mapping-tracker.txt" expect 1 mapping
# A broken pattern file must be a tool error (2), never "clean".
INPUT="$fx/dex-firebase.txt" PRIVACY_GATE_TRACKERS="$fx/trackers-broken.txt" expect 2 dex
# Empty input means the upstream command broke; that is a tool error (2), never "clean".
INPUT=/dev/null expect 2 perms
INPUT=/dev/null expect 2 deps
INPUT=/dev/null expect 2 dex
INPUT=/dev/null expect 2 mapping

# apk mode: a failing aapt2 is a tool error (2), not a violation (1); dex/jar
# entries are scanned at any path inside the APK.
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/sdk/build-tools/36.0.0" "$tmp/sdk-ok/build-tools/36.0.0"
printf '#!/bin/sh\nexit 1\n' > "$tmp/sdk/build-tools/36.0.0/aapt2"
printf '#!/bin/sh\ncat "%s"\n' "$fx/perms-clean.txt" > "$tmp/sdk-ok/build-tools/36.0.0/aapt2"
chmod +x "$tmp/sdk/build-tools/36.0.0/aapt2" "$tmp/sdk-ok/build-tools/36.0.0/aapt2"
mkdir -p "$tmp/z/assets"
cp "$fx/dex-clean.txt" "$tmp/z/classes.dex"
(cd "$tmp/z" && zip -qr ../clean.apk classes.dex)
cp "$fx/dex-firebase.txt" "$tmp/z/assets/x.dex"
(cd "$tmp/z" && zip -qr ../nested.apk classes.dex assets/x.dex)

expect_apk() {
    local want="$1" sdk="$2" apk="$3"
    ANDROID_HOME="$sdk" bash "$gate" apk "$apk" >/dev/null 2>&1
    local got=$?
    if [[ "$got" == "$want" ]]; then pass=$((pass + 1)); echo "ok   apk ${apk##*/} (${sdk##*/}) → $got"
    else fail=$((fail + 1)); echo "FAIL apk ${apk##*/} (${sdk##*/}): want $want, got $got"; fi
}
expect_apk 2 "$tmp/sdk"    "$tmp/clean.apk"
expect_apk 0 "$tmp/sdk-ok" "$tmp/clean.apk"
expect_apk 1 "$tmp/sdk-ok" "$tmp/nested.apk"

expect 2 bogus
expect 2

echo "$pass passed, $fail failed"
[[ $fail -eq 0 ]]
