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
    bash "$gate" "$@" < "$input" >/dev/null 2>&1
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
expect 2 bogus
expect 2

echo "$pass passed, $fail failed"
[[ $fail -eq 0 ]]
