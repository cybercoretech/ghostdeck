#!/usr/bin/env bash
# sync-themes.sh must be atomic: one bad pack leaves the destination untouched.
set -uo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/../.." && pwd)"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
pass=0; fail=0
CASE=""
check() { if "$@"; then pass=$((pass + 1)); echo "ok   $CASE"; else fail=$((fail + 1)); echo "FAIL $CASE"; fi; }

roles=(bg surface panel border line selection hover white muted text_primary accent_hot accent_alt acid_green hot_pink purple cyan orange red success warning error)
mkpack() { # $1 file, $2 role to omit (optional)
    mkdir -p "$(dirname "$1")"
    local json='{}' r
    for r in "${roles[@]}"; do
        [[ "$r" == "${2:-}" ]] || json="$(jq --arg r "$r" '.[$r]="a1b2c3"' <<<"$json")"
    done
    echo "$json" > "$1"
}
# Run against a copy of the script so the real app/ assets are never touched.
mkdir -p "$tmp/repo/tools" "$tmp/repo/app/src/main/assets/themes"
cp "$repo/tools/sync-themes.sh" "$tmp/repo/tools/"
dest="$tmp/repo/app/src/main/assets/themes"
packs=(cybercore-vortex/cybercore-vortex-synthetic terminal-ghost/terminal-ghost-alpha cryo-terminal-blue/cryo-terminal-blue-synthetic)

# Bad case: third pack lacks a role.
mkpack "$tmp/bad/${packs[0]}.json"; mkpack "$tmp/bad/${packs[1]}.json"; mkpack "$tmp/bad/${packs[2]}.json" accent_hot
for p in "${packs[@]}"; do echo "sentinel-$(basename "$p")" > "$dest/$(basename "$p").json"; done
before="$(cat "$dest"/*.json)"
bash "$tmp/repo/tools/sync-themes.sh" "$tmp/bad" >/dev/null 2>&1; rc=$?
CASE="invalid third pack exits 1"; check test "$rc" -eq 1
CASE="invalid third pack leaves dest untouched"; check test "$(cat "$dest"/*.json)" == "$before"

# Good case: all three valid.
for p in "${packs[@]}"; do mkpack "$tmp/good/$p.json"; done
rm -f "$dest"/*.json
bash "$tmp/repo/tools/sync-themes.sh" "$tmp/good" >/dev/null 2>&1; rc=$?
CASE="valid packs exit 0"; check test "$rc" -eq 0
CASE="valid packs write three files"; check test "$(ls "$dest" | wc -l)" -eq 3

echo "$pass passed, $fail failed"
[[ $fail -eq 0 ]]
