#!/usr/bin/env bash
# Refresh tools/trackers.txt from Exodus Privacy's tracker signatures.
# Run by hand at dev time and commit the result. CI never fetches it.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
out="$here/trackers.txt"
url="https://reports.exodus-privacy.eu.org/api/trackers"
json="$(curl -fsSL -A "ghostdeck-update-trackers" "$url")"
{
    echo "# Tracker code signatures (Java package prefixes), one per line."
    echo "# Source: Exodus Privacy tracker signatures, $url"
    echo "# Snapshot: $(date -u +%Y-%m-%d). Regenerate with tools/update-trackers.sh."
    printf '%s' "$json" | python3 -c '
import json, sys
data = json.load(sys.stdin)["trackers"]
sigs = set()
for t in data.values():
    for sig in (t.get("code_signature") or "").split("|"):
        sig = sig.replace("\\", "").strip().strip(".")
        if sig and "." in sig:
            sigs.add(sig)
print("\n".join(sorted(sigs)))
'
} > "$out.tmp"
mv "$out.tmp" "$out"
echo "wrote $(grep -vc '^#' "$out") signatures to $out"
