#
# VENDORED FILE — do not edit here.
#
# Source of truth: camunda/c8-cross-component-e2e-tests,
#                  scripts/alwaysgreen-core/alwaysgreen_core/artifacts.py
# Vendored at:     1f4ba545a9164a2f435ef733db11ad81b887dc7b
#
# Edit it there and re-vendor; `vendored-copy-drift.yml` in that repository
# reports a copy that was changed in place or left behind this pin.
#
"""Downloading a run's artifacts and reading the JSON inside them.

Part of alwaysgreen_core, the code every AlwaysGreen instance shares verbatim.
Do not edit a vendored copy of this file: edit it in
camunda/c8-cross-component-e2e-tests and re-vendor. `vendored-copy-drift.yml`
reports a copy that stops matching the revision its vendor.json pins.
"""


from __future__ import annotations

import json
import subprocess
from pathlib import Path

from .gh import log


def download_artifacts(run_id: str, repo: str, pattern: str, dest: Path) -> bool:
    dest.mkdir(parents=True, exist_ok=True)
    try:
        subprocess.run(
            [
                "gh", "run", "download", str(run_id),
                "--repo", repo, "--pattern", pattern, "--dir", str(dest),
            ],
            capture_output=True, text=True, timeout=300, check=True,
        )
        return True
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired, OSError):
        return False


def read_json_files(root: Path, filename: str) -> list[dict]:
    out = []
    for path in sorted(root.rglob(filename)):
        try:
            out.append(json.loads(path.read_text()))
        except (json.JSONDecodeError, OSError):
            log(f"::warning::could not parse {path}")
    return out
