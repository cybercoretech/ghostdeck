#!/usr/bin/env bash
# README screenshots stay referenced and small (the README shows them at width 240).
set -uo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
limit=$((300 * 1024))
fail=0
for shot in home.png edit-mode.png; do
    path="docs/screenshots/$shot"
    size=$(stat -c %s "$root/$path" 2>/dev/null || echo 0)
    if [[ "$size" -gt 0 && "$size" -lt "$limit" ]]; then echo "ok   $path is $((size / 1024)) KB"
    else echo "FAIL $path is $((size / 1024)) KB (want 1..$((limit / 1024)) KB)"; fail=1; fi
    if grep -qF "$path" "$root/README.md"; then echo "ok   README references $path"
    else echo "FAIL README does not reference $path"; fail=1; fi
done
exit $fail
