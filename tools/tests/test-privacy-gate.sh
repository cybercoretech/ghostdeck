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
expect 2 bogus
expect 2

echo "$pass passed, $fail failed"
[[ $fail -eq 0 ]]
