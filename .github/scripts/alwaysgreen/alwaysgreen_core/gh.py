#
# VENDORED FILE — do not edit here.
#
# Source of truth: camunda/c8-cross-component-e2e-tests,
#                  scripts/alwaysgreen-core/alwaysgreen_core/gh.py
# Vendored at:     1f4ba545a9164a2f435ef733db11ad81b887dc7b
#
# Edit it there and re-vendor; `vendored-copy-drift.yml` in that repository
# reports a copy that was changed in place or left behind this pin.
#
"""Running `gh` and reporting what it said.

Part of alwaysgreen_core, the code every AlwaysGreen instance shares verbatim.
Do not edit a vendored copy of this file: edit it in
camunda/c8-cross-component-e2e-tests and re-vendor. `vendored-copy-drift.yml`
reports a copy that stops matching the revision its vendor.json pins.
"""


from __future__ import annotations

import json
import subprocess
import sys


def log(message: str) -> None:
    print(message, file=sys.stderr)


def gh_json_ex(args: list[str], default) -> tuple[object, str]:
    """Run a gh command expecting JSON. Returns (value, error_text)."""
    try:
        out = subprocess.run(
            ["gh", *args], capture_output=True, text=True, timeout=120, check=True
        ).stdout.strip()
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired, OSError) as exc:
        stderr = (getattr(exc, "stderr", "") or str(exc)).strip()
        log(f"::warning::gh {' '.join(args[:3])} failed: {stderr[:200]}")
        return default, stderr
    if not out:
        return default, ""
    try:
        return json.loads(out), ""
    except json.JSONDecodeError:
        log(f"::warning::gh {' '.join(args[:3])} returned non-JSON output")
        return default, "invalid json"


def gh_json(args: list[str], default):
    """Run a gh command expecting JSON on stdout. Returns `default` on any failure."""
    value, _ = gh_json_ex(args, default)
    return value
