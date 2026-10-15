#
# VENDORED FILE — do not edit here.
#
# Source of truth: camunda/c8-cross-component-e2e-tests,
#                  scripts/alwaysgreen-core/alwaysgreen_core/fingerprints.py
# Vendored at:     1f4ba545a9164a2f435ef733db11ad81b887dc7b
#
# Edit it there and re-vendor; `vendored-copy-drift.yml` in that repository
# reports a copy that was changed in place or left behind this pin.
#
"""Stable identities for a failing spec, a failing job, and a known product bug.

Part of alwaysgreen_core, the code every AlwaysGreen instance shares verbatim.
Do not edit a vendored copy of this file: edit it in
camunda/c8-cross-component-e2e-tests and re-vendor. `vendored-copy-drift.yml`
reports a copy that stops matching the revision its vendor.json pins.
"""


from __future__ import annotations

import hashlib
import re
from .gh import gh_json


def fingerprint(*parts: str) -> str:
    """Stable 8-character id used for dedupe and for the PR coverage block.

    Failing *step* names are deliberately not an input: they are frequently
    absent (a cancelled job, or a job whose steps never ran), which would make
    the same failure hash differently between runs.
    """
    joined = "::".join(p or "" for p in parts)
    return hashlib.sha256(joined.encode("utf-8")).hexdigest()[:8]


def spec_fingerprint(base_ref: str, surface: str, file: str, test_name: str) -> str:
    return fingerprint(base_ref, surface, file, test_name)


def job_leaf_name(job_name: str) -> str:
    """Return the last segment of a nested reusable-workflow job name."""
    return job_name.rsplit("/", 1)[-1].strip()


def job_fingerprint(base_ref: str, surface: str, job_name: str) -> str:
    return fingerprint(base_ref, surface, job_leaf_name(job_name))


def source_spec_path(spec_file: str, *, suite: str | None) -> str:
    """Map a report `file` value to its path in the e2e test repository.

    The npm package ships compiled `.js`; the source is `.ts`.
    """
    name = (spec_file or "").strip()
    if not name:
        return ""
    if name.endswith(".spec.js"):
        name = name[: -len(".spec.js")] + ".spec.ts"
    if "/" in name:
        return name
    return f"tests/{suite}/{name}" if suite else name


def product_bug_fingerprints() -> tuple[set[str], dict[str, str]]:
    """(fingerprints, fingerprint -> issue URL).

    The URL is carried so a suppressed failure can name the bug it is tracked by. A
    medic told only "tracked as a known product bug" still has to find the issue by
    hand, which is the whole cost the message was supposed to save.
    """
    issues = gh_json(
        [
            "search", "issues", "nightly-product-bug is:issue",
            "--owner", "camunda", "--state", "open",
            "--limit", "200", "--json", "body,url",
        ],
        [],
    )
    out: set[str] = set()
    urls: dict[str, str] = {}
    for issue in issues if isinstance(issues, list) else []:
        for line in (issue.get("body") or "").splitlines():
            if "nightly-product-bug fp=" in line:
                fp = line.split("fp=", 1)[1].strip()[:8]
                out.add(fp)
                if issue.get("url"):
                    urls.setdefault(fp, str(issue["url"]))
    return out, urls
