#!/usr/bin/env bash
# Ghostdeck privacy gate (spec §11a.2). Fails the build when the core APK
# could reach the network or carries tracker code or tracker dependencies.
#
# Usage:
#   privacy-gate.sh perms        < `aapt2 dump permissions` output
#   privacy-gate.sh dex          < `strings` output of the APK's dex files
#   privacy-gate.sh deps         < `gradle dependencies` output
#   privacy-gate.sh apk <path>   runs perms + dex against an APK
#   privacy-gate.sh gradle       runs deps against :app releaseRuntimeClasspath
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
trackers="$here/trackers.txt"

usage() { sed -n '5,10p' "${BASH_SOURCE[0]}" >&2; exit 2; }

violation() { echo "privacy-gate: $1" >&2; shift; printf '  %s\n' "$@" >&2; exit 1; }

check_perms() {
    local hits
    hits="$(grep -E "android\.permission\.INTERNET'?\b" || true)"
    [[ -z "$hits" ]] || violation "INTERNET permission found (the core APK must never have it)" "$hits"
}

check_dex() {
    local patterns hits
    patterns="$(mktemp)"; trap 'rm -f "$patterns"' RETURN
    # Each signature is a package or class prefix (com.google.firebase.analytics,
    # com.google.firebase.analytics.FirebaseAnalytics). It becomes a dex
    # descriptor prefix that must end at a name boundary (/ ; or $), so
    # Lcom/adjust does not match Lcom/adjustable.
    grep -vE '^\s*(#|$)' "$trackers" | sed -E 's/\.$//; s/\./\//g; s/^/L/; s/$/(\/|;|\\$)/' > "$patterns"
    hits="$(grep -E -f "$patterns" | sort -u | head -n 20 || true)"
    [[ -z "$hits" ]] || violation "tracker code found in dex" "$hits"
}

check_deps() {
    local re hits
    re="$(printf '%s|' "${DENIED_GROUPS[@]}" | sed 's/\./\\./g; s/|$//')"
    hits="$(grep -E "(^|[^A-Za-z0-9_.])($re)(\.[A-Za-z0-9_.-]+)?:" | sort -u || true)"
    [[ -z "$hits" ]] || violation "tracker or Play Services dependency found" "$hits"
}

check_apk() {
    local apk="${1:-}"
    [[ -f "$apk" ]] || { echo "privacy-gate: no such APK: $apk" >&2; exit 2; }
    local aapt2="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/android}}/build-tools/36.0.0/aapt2"
    [[ -x "$aapt2" ]] || { echo "privacy-gate: aapt2 not found at $aapt2" >&2; exit 2; }
    "$aapt2" dump permissions "$apk" | check_perms
    unzip -p "$apk" 'classes*.dex' | strings -n 6 | check_dex
    echo "privacy-gate: $apk is clean (no INTERNET, no tracker code)"
}

check_gradle() {
    (cd "$root" && ./gradlew -q :app:dependencies --configuration releaseRuntimeClasspath) | check_deps
    echo "privacy-gate: releaseRuntimeClasspath is clean"
}

case "${1:-}" in
    perms)  check_perms ;;
    dex)    check_dex ;;
    deps)   check_deps ;;
    apk)    check_apk "${2:-}" ;;
    gradle) check_gradle ;;
    *)      usage ;;
esac
