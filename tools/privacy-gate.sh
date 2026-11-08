#!/usr/bin/env bash
# Ghostdeck privacy gate (spec §11a.2). Fails the build when the core APK
# could reach the network or carries tracker code or tracker dependencies.
#
# Usage:
#   privacy-gate.sh perms        < `aapt2 dump permissions` output
#   privacy-gate.sh dex          < `strings` output of the APK's dex files
#   privacy-gate.sh deps         < `gradle dependencies` output
#   privacy-gate.sh mapping      < R8 mapping.txt (original class names of a minified APK)
#   privacy-gate.sh apk <path>   runs perms + dex against an APK
#   privacy-gate.sh gradle [cfg] runs deps against :app <cfg> (default releaseRuntimeClasspath)
#
# Exit codes: 0 clean, 1 violation (offending lines on stderr), 2 usage error.
#
# Denied Maven groups (analytics, crash reporting, ads, Google Play Services):
DENIED_GROUPS=(
    com.google.firebase
    com.google.android.gms
    com.google.android.play
    com.crashlytics
    io.sentry
    com.facebook
    com.appsflyer
    com.adjust
    com.amplitude
    com.mixpanel
    com.segment
    io.branch
    com.onesignal
    com.bugsnag
    com.newrelic
    com.datadoghq
)
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/.." && pwd)"
trackers="${PRIVACY_GATE_TRACKERS:-$here/trackers.txt}"

usage() { sed -n '5,11p' "${BASH_SOURCE[0]}" >&2; exit 2; }

# grep that tells "no match" (1) apart from "grep broke" (2+, e.g. a bad
# pattern). A broken check must never read as clean.
safe_grep() {
    local status=0
    grep "$@" || status=$?
    if (( status >= 2 )); then echo "privacy-gate: grep failed (status $status); check the patterns" >&2; exit 2; fi
    return 0
}

# Read stdin into a temp file (path in $input_file) and exit 2 unless it looks
# like real tool output: an empty or garbled upstream must never read as clean.
# $1 = what we expect, $2 = ERE a line must match, or "" for any non-empty line.
input_file=""
require_input() {
    local what="$1" re="$2"
    input_file="$(mktemp)"
    cat > "$input_file"
    if [[ -z "$re" ]]; then re='.'; fi
    if ! grep -qE -- "$re" "$input_file"; then
        rm -f "$input_file"
        echo "privacy-gate: empty or unrecognised input (expected $what); the upstream command probably failed" >&2
        exit 2
    fi
}

violation() { echo "privacy-gate: $1" >&2; shift; printf '  %s\n' "$@" >&2; exit 1; }

check_perms() {
    local hits
    require_input "aapt2 output with a 'package:' line" '^package:'
    hits="$(safe_grep -E "android\.permission\.INTERNET'?\b" < "$input_file")"
    rm -f "$input_file"
    [[ -z "$hits" ]] || violation "INTERNET permission found (the core APK must never have it)" "$hits"
}

check_dex() {
    local patterns hits
    require_input "dex strings or class names" ''
    [[ -r "$trackers" ]] || { echo "privacy-gate: tracker list not found: $trackers" >&2; exit 2; }
    patterns="$(mktemp)"
    # Each signature is a package or class prefix (com.google.firebase.analytics,
    # com.google.firebase.analytics.FirebaseAnalytics). It becomes a dex
    # descriptor prefix that must end at a name boundary (/ ; or $), so
    # Lcom/adjust does not match Lcom/adjustable.
    grep -vE '^\s*(#|$)' "$trackers" | sed -E 's/\.$//; s/\./\//g; s/^/L/; s/$/(\/|;|\\$)/' > "$patterns"
    hits="$(safe_grep -E -f "$patterns" < "$input_file" | sort -u)" || { rm -f "$patterns" "$input_file"; exit 2; }
    rm -f "$patterns" "$input_file"
    [[ -z "$hits" ]] || violation "tracker code found in dex ($(wc -l <<<"$hits") classes, first 20 shown)" "$(head -n 20 <<<"$hits")"
}

check_deps() {
    local re hits
    require_input "gradle dependencies output with a '---' line" '---'
    re="$(printf '%s|' "${DENIED_GROUPS[@]}" | sed 's/\./\\./g; s/|$//')"
    hits="$(safe_grep -E "(^|[^A-Za-z0-9_.])($re)(\.[A-Za-z0-9_.-]+)?:" < "$input_file" | sort -u)"
    rm -f "$input_file"
    [[ -z "$hits" ]] || violation "tracker or Play Services dependency found" "$hits"
}

# R8 renames classes in release builds, so a tracker without keep rules is
# invisible in the release dex. Its original name survives in mapping.txt:
# "com.example.Tracker -> a.b:" becomes the descriptor "Lcom/example/Tracker;".
check_mapping() {
    safe_grep -E '^[^ #].* -> .*:$' | sed -E 's/ -> .*//; s/\./\//g; s/^/L/; s/$/;/' | check_dex
}

scan=""
check_apk() {
    local apk="${1:-}"
    [[ -f "$apk" ]] || { echo "privacy-gate: no such APK: $apk" >&2; exit 2; }
    local aapt2="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/android}}/build-tools/36.0.0/aapt2"
    [[ -x "$aapt2" ]] || { echo "privacy-gate: aapt2 not found at $aapt2" >&2; exit 2; }
    local out entry
    out="$("$aapt2" dump permissions "$apk")" || { echo "privacy-gate: aapt2 failed on $apk" >&2; exit 2; }
    check_perms <<<"$out"
    # Every dex and jar at any path (assets/, lib/ ...), not just root classes*.dex.
    local entries
    entries="$(unzip -Z1 "$apk")" || { echo "privacy-gate: cannot list $apk" >&2; exit 2; }
    scan="$(mktemp)"; trap 'rm -f "${scan:-}"' EXIT
    while IFS= read -r entry; do
        case "$entry" in
            *.dex|*.jar) unzip -p "$apk" "$entry" | strings -n 6 >> "$scan" || { rm -f "$scan"; echo "privacy-gate: cannot read $entry in $apk" >&2; exit 2; } ;;
        esac
    done <<<"$entries"
    check_dex < "$scan"
    echo "privacy-gate: $apk is clean (no INTERNET, no tracker code)"
}

check_gradle() {
    local cfg="${1:-releaseRuntimeClasspath}"
    (cd "$root" && ./gradlew -q :app:dependencies --configuration "$cfg") | check_deps
    echo "privacy-gate: $cfg is clean"
}

case "${1:-}" in
    perms)  check_perms ;;
    dex)    check_dex ;;
    deps)   check_deps ;;
    mapping) check_mapping ;;
    apk)    check_apk "${2:-}" ;;
    gradle) check_gradle "${2:-}" ;;
    *)      usage ;;
esac
