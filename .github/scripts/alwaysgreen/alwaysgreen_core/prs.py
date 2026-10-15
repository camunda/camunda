#
# VENDORED FILE — do not edit here.
#
# Source of truth: camunda/c8-cross-component-e2e-tests,
#                  scripts/alwaysgreen-core/alwaysgreen_core/prs.py
# Vendored at:     1f4ba545a9164a2f435ef733db11ad81b887dc7b
#
# Edit it there and re-vendor; `vendored-copy-drift.yml` in that repository
# reports a copy that was changed in place or left behind this pin.
#
"""Reading provenance off a pull request: who opened it, and what it came from.

Part of alwaysgreen_core, the code every AlwaysGreen instance shares verbatim.
Do not edit a vendored copy of this file: edit it in
camunda/c8-cross-component-e2e-tests and re-vendor. `vendored-copy-drift.yml`
reports a copy that stops matching the revision its vendor.json pins.
"""


from __future__ import annotations

import re


_BACKPORT_TITLE_RE = re.compile(r"^\[Backport [^\]]+\]\s.*\(#(?P<number>\d+)\)\s*$")


def is_bot(login: str | None) -> bool:
    return bool(login) and login.endswith("[bot]")


def originating_pr(prs: list[dict], head_sha: str) -> dict | None:
    """Pick the PR that actually produced `head_sha`.

    `/commits/{sha}/pulls` also returns open PRs that merely contain the commit
    as an ancestor, so match on `merge_commit_sha` and fall back to the first
    entry only when nothing matches.
    """
    for pr in prs:
        if pr.get("merge_commit_sha") == head_sha:
            return pr
    return prs[0] if prs else None


def backported_pr_number(title: str | None) -> int | None:
    """Return the original PR number referenced by a backport PR title."""
    match = _BACKPORT_TITLE_RE.match((title or "").strip())
    return int(match.group("number")) if match else None
