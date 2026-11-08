#!/usr/bin/env bash
# CI must run once per PR update: push only on main, PRs separately, and a
# concurrency group that cancels superseded runs.
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
python3 - "${1:-$root/.github/workflows/android.yml}" <<'PY'
import sys
import yaml

wf = yaml.safe_load(open(sys.argv[1]))
on = wf.get(True, wf.get("on"))  # PyYAML reads the bare key `on` as True
problems = []
if (on.get("push") or {}).get("branches") != ["main"]:
    problems.append("on.push.branches must be ['main']")
if "pull_request" not in on:
    problems.append("on.pull_request is missing")
conc = wf.get("concurrency") or {}
if conc.get("cancel-in-progress") is not True:
    problems.append("concurrency.cancel-in-progress must be true")
if not conc.get("group"):
    problems.append("concurrency.group is missing")
for p in problems:
    print("FAIL", p)
if not problems:
    print("ok   workflow triggers and concurrency")
sys.exit(1 if problems else 0)
PY
