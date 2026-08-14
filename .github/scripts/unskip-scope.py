#!/usr/bin/env python3
"""Derive the verification scope of an auto-unskip PR.

The auto-unskip workflow (camunda/qa-metrics-exporter) opens one PR per
branch + category that flips `test.skip(` back to `test(` for tests whose
referenced bug is closed. This script reads that PR and emits everything the
unskip-verify agent needs:

* `scope.json` — the changed spec files, the test titles each one un-skipped
  (with the closed bug they reference), and the "Needs manual review" markers
  the unskip workflow deliberately left untouched.
* `$GITHUB_OUTPUT` — `pr_number`, `base_ref`, `version`, `safe_version`,
  `has_e2e`, `has_api`, `spec_count` and `matrix`: one cell per environment
  dimension the target version actually runs (mirrors the dimensions of
  c8-orchestration-cluster-e2e-tests-on-demand.yml). Cells whose specs do not
  execute in that dimension prune themselves later, via `playwright --list`.

Usage:
    unskip-scope.py --pr 60156 [--repo camunda/camunda] [--out scope.json]
    unskip-scope.py --branch auto/unskip-closed-bugs-camunda-stable-8.9-e2e
"""

import argparse
import json
import os
import re
import subprocess
import sys

SUITE = "qa/c8-orchestration-cluster-e2e-test-suite"

# Tasklist generations the E2E matrix runs per version, and secondary-storage
# backends the API matrix runs. Mirrors the on-demand workflow's own matrix
# expressions — keep the two in sync when a version is added or retired.
E2E_TASKLIST_MODES = {
    "main": ["v2"],
    "8.9": ["v1", "v2"],
    "8.8": ["v1", "v2"],
    "8.7": ["v1"],
    "8.6": ["v1"],
}
API_DATABASES = {
    "main": ["es", "h2"],
    "8.9": ["es", "h2"],
    "8.8": ["es"],
    "8.7": ["es"],
    "8.6": ["es"],
}

DIFF_FILE_RE = re.compile(r"^\+\+\+ b/(.+)$")
# `test('title'`, `test.describe('title'`, `test.step('title'` — the three call
# shapes the unskip transform flips. Quote style varies across the suite.
TEST_CALL_RE = re.compile(
    r"^\+\s*(?:test|test\.describe|test\.step)\(\s*['\"`](.+?)['\"`]"
)
ISSUE_URL_RE = re.compile(r"https://github\.com/[\w.-]+/[\w.-]+/issues/\d+")
SKIP_MARKER_RE = re.compile(r"^-\s*//.*\bskip\w*\b", re.IGNORECASE)
# Rendered by _unskip-core.yml: "- [ ] **[`file.spec.ts`:120](url)** — closed bug [owner/repo#n](url)"
MANUAL_ITEM_RE = re.compile(r"^- \[[ xX]\] \*\*\[`[^`]+`:(\d+)\]")
MANUAL_PATH_RE = re.compile(r"^\s+- Path: `(.+?)`\s*$")
MANUAL_REASON_RE = re.compile(r"^\s+- Why it was skipped: (.+?)\s*$")
MANUAL_BUG_RE = re.compile(r"closed bug \[([^\]]+)\]\(([^)]+)\)")


def gh(args, repo):
    """Run a `gh` command and return stdout, or exit with its error."""
    cmd = ["gh"] + args + ["--repo", repo]
    result = subprocess.run(cmd, capture_output=True, text=True, check=False)
    if result.returncode != 0:
        sys.exit(f"::error::{' '.join(cmd)} failed: {result.stderr.strip()}")
    return result.stdout


def resolve_pr(repo, pr_number, branch):
    """Return the PR metadata dict, looking it up by branch when needed."""
    if not pr_number:
        if not branch:
            sys.exit("::error::one of --pr or --branch is required")
        listing = json.loads(
            gh(
                [
                    "pr",
                    "list",
                    "--head",
                    branch,
                    "--state",
                    "open",
                    "--json",
                    "number",
                    "--limit",
                    "1",
                ],
                repo,
            )
        )
        if not listing:
            sys.exit(f"::error::no open PR found for branch {branch}")
        pr_number = listing[0]["number"]
    fields = "number,baseRefName,headRefName,body,title,url,state"
    return json.loads(gh(["pr", "view", str(pr_number), "--json", fields], repo))


def version_of(base_ref):
    """`stable/8.9` -> ("8.9", "stable-8.9"); `main` -> ("main", "main")."""
    if base_ref == "main":
        return "main", "main"
    if base_ref.startswith("stable/"):
        version = base_ref.split("/", 1)[1]
        return version, f"stable-{version}"
    sys.exit(f"::error::unsupported base branch: {base_ref}")


def parse_diff(diff):
    """Map each changed suite-relative spec path to its un-skipped tests.

    A test is attributed to the bug whose skip marker was removed immediately
    above it; markers and the `test(` call can be separated by kept prose
    comments, so the most recent unconsumed marker in the hunk wins.
    """
    files = {}
    current = None
    pending_bugs = []
    for line in diff.splitlines():
        match = DIFF_FILE_RE.match(line)
        if match:
            path = match.group(1)
            current = None
            pending_bugs = []
            if path.startswith(f"{SUITE}/") and path.endswith(".spec.ts"):
                current = path[len(SUITE) + 1 :]
                files.setdefault(current, {"file": current, "tests": []})
            continue
        if current is None:
            continue
        if SKIP_MARKER_RE.match(line):
            found = ISSUE_URL_RE.search(line)
            if found:
                pending_bugs.append(found.group(0))
            continue
        test_match = TEST_CALL_RE.match(line)
        if test_match:
            files[current]["tests"].append(
                {
                    "test_name": test_match.group(1),
                    "bug": pending_bugs.pop(0) if pending_bugs else None,
                }
            )
    return files


def parse_manual_markers(body):
    """Extract the checklist under "Needs manual review" from the PR body."""
    if not body:
        return []
    markers = []
    in_section = False
    current = None
    for line in body.splitlines():
        if line.startswith("###"):
            in_section = "Needs manual review" in line
            if not in_section and current:
                markers.append(current)
                current = None
            continue
        if not in_section:
            continue
        item = MANUAL_ITEM_RE.match(line)
        if item:
            if current:
                markers.append(current)
            bug = MANUAL_BUG_RE.search(line)
            current = {
                "line": int(item.group(1)),
                "bug": bug.group(2) if bug else None,
                "bug_key": bug.group(1) if bug else None,
                "path": None,
                "reason": "",
            }
            continue
        if current is None:
            continue
        path = MANUAL_PATH_RE.match(line)
        if path:
            raw = path.group(1)
            current["path"] = (
                raw[len(SUITE) + 1 :] if raw.startswith(f"{SUITE}/") else raw
            )
            continue
        reason = MANUAL_REASON_RE.match(line)
        if reason:
            current["reason"] = reason.group(1)
    if current:
        markers.append(current)
    return [m for m in markers if m["path"]]


def build_matrix(version, has_e2e, has_api):
    """One cell per environment dimension this version's matrix covers."""
    cells = []
    if has_e2e:
        for mode in E2E_TASKLIST_MODES.get(version, ["v2"]):
            cells.append(
                {
                    "cell": f"e2e-es-{mode}",
                    "category": "e2e",
                    "database": "es",
                    "tasklist_mode": mode,
                }
            )
    if has_api:
        for database in API_DATABASES.get(version, ["es"]):
            cells.append(
                {
                    "cell": f"api-{database}",
                    "category": "api",
                    "database": database,
                    # API jobs have no tasklist dimension; the env script's
                    # default (v2) matches what on-demand's API jobs run.
                    "tasklist_mode": "v2",
                }
            )
    return cells


def emit_outputs(pairs):
    path = os.environ.get("GITHUB_OUTPUT")
    if not path:
        for key, value in pairs:
            print(f"{key}={value}")
        return
    with open(path, "a", encoding="utf-8") as handle:
        for key, value in pairs:
            handle.write(f"{key}={value}\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pr", type=int, default=0)
    parser.add_argument("--branch", default="")
    parser.add_argument(
        "--repo", default=os.environ.get("GITHUB_REPOSITORY", "camunda/camunda")
    )
    parser.add_argument("--out", default="scope.json")
    args = parser.parse_args()

    pr = resolve_pr(args.repo, args.pr, args.branch)
    version, safe_version = version_of(pr["baseRefName"])

    diff = gh(["pr", "diff", str(pr["number"])], args.repo)
    files = parse_diff(diff)
    markers = parse_manual_markers(pr.get("body", ""))

    # A manual-review marker can sit in a file the transform never touched, so
    # it widens the run scope beyond the diff.
    for marker in markers:
        files.setdefault(marker["path"], {"file": marker["path"], "tests": []})

    specs = sorted(files.values(), key=lambda f: f["file"])
    has_api = any(f["file"].startswith("tests/api/") for f in specs)
    has_e2e = any(not f["file"].startswith("tests/api/") for f in specs)

    scope = {
        "pr_number": pr["number"],
        "pr_url": pr["url"],
        "branch": pr["headRefName"],
        "base_ref": pr["baseRefName"],
        "version": version,
        "safe_version": safe_version,
        "specs": specs,
        "manual_markers": markers,
    }
    with open(args.out, "w", encoding="utf-8") as handle:
        json.dump(scope, handle, indent=2)

    matrix = {"include": build_matrix(version, has_e2e, has_api)}
    emit_outputs(
        [
            ("pr_number", pr["number"]),
            ("branch", pr["headRefName"]),
            ("base_ref", pr["baseRefName"]),
            ("version", version),
            ("safe_version", safe_version),
            ("has_e2e", str(has_e2e).lower()),
            ("has_api", str(has_api).lower()),
            ("spec_count", len(specs)),
            ("manual_marker_count", len(markers)),
            ("matrix", json.dumps(matrix)),
        ]
    )
    print(
        f"PR #{pr['number']} ({pr['baseRefName']}): {len(specs)} spec file(s), "
        f"{sum(len(f['tests']) for f in specs)} un-skipped test(s), "
        f"{len(markers)} manual marker(s), {len(matrix['include'])} matrix cell(s)"
    )


if __name__ == "__main__":
    main()
