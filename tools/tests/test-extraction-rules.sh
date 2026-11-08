#!/usr/bin/env bash
# Every storage domain must be excluded from both cloud-backup and device-transfer
# (spec §11a.2), including the direct-boot device_* domains.
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
python3 - "${1:-$root/app/src/main/res/xml/data_extraction_rules.xml}" <<'PY'
import sys
import xml.etree.ElementTree as ET

need = {"root", "file", "database", "sharedpref", "external",
        "device_root", "device_file", "device_database", "device_sharedpref"}
tree = ET.parse(sys.argv[1]).getroot()
bad = False
for block in ("cloud-backup", "device-transfer"):
    node = tree.find(block)
    got = {e.get("domain") for e in node.findall("exclude")} if node is not None else set()
    missing = sorted(need - got)
    if missing:
        bad = True
        print(f"FAIL {block} does not exclude: {', '.join(missing)}")
    else:
        print(f"ok   {block} excludes all {len(need)} domains")
sys.exit(1 if bad else 0)
PY
