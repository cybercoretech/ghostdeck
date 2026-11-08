#!/usr/bin/env bash
# Dev-time only: vendor registry theme packs into app/src/main/assets/themes/.
# Never run by the build. Usage: tools/sync-themes.sh [registry-dir]
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
src="${1:-$HOME/Devspace/Cybercore/web/cybercore-tech.github.io/data/themes}"
dest="$here/app/src/main/assets/themes"
roles=(bg surface panel border line selection hover white muted text_primary accent_hot accent_alt acid_green hot_pink purple cyan orange red success warning error)
packs=(
  cybercore-vortex/cybercore-vortex-synthetic
  terminal-ghost/terminal-ghost-alpha
  cryo-terminal-blue/cryo-terminal-blue-synthetic
)
mkdir -p "$dest"
for p in "${packs[@]}"; do
  f="$src/$p.json"
  [[ -f "$f" ]] || { echo "missing: $f" >&2; exit 1; }
  for r in "${roles[@]}"; do
    jq -e --arg r "$r" 'has($r) and (.[$r] | type == "string" and test("^[0-9a-fA-F]{6}$"))' "$f" >/dev/null \
      || { echo "$f: missing or invalid role '$r'" >&2; exit 1; }
  done
  cp "$f" "$dest/$(basename "$p").json"
  echo "synced $(basename "$p").json"
done
