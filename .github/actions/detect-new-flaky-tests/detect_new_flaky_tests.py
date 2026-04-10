#!/usr/bin/env python3
"""Detect new flaky tests introduced by a PR.

Compares flaky tests from the current PR run against known flaky tests
(queried from BigQuery). Posts a PR comment and fails if new flaky tests
are found.

Inputs (environment variables):
  PR_FLAKY_TESTS_DATA  – JSON array of {job, flaky_tests}
  KNOWN_FLAKY_TESTS    – JSON array of {test_class_name, test_name}
  PR_NUMBER            – Pull request number
  GITHUB_TOKEN         – GitHub API token
  GITHUB_REPOSITORY    – owner/repo
  GITHUB_OUTPUT        – Path to the output file for setting step outputs
"""

import json
import os
import re
import sys
import urllib.request
import urllib.error

PREFIX = "[new-flaky]"


# ---------------------------------------------------------------------------
# Test‑name helpers (mirror the JS helpers in flaky-tests-summary-comment)
# ---------------------------------------------------------------------------

def parse_test_name(test_name: str) -> dict:
    """Parse a fully-qualified test name into package, class, and method."""
    last_dot = test_name.rfind(".")
    if last_dot == -1:
        return {"fullName": test_name.strip()}

    fully_qualified_class = test_name[:last_dot]
    method_name = test_name[last_dot + 1 :]

    # Remove trailing [index]
    method_name = re.sub(r"\[.*?]\s*$", "", method_name)
    # Remove parameter list
    method_name = re.sub(r"\(.*?\)\s*$", "", method_name).strip()

    class_parts = fully_qualified_class.split(".")
    class_name = class_parts[-1]
    package_name = ".".join(class_parts[:-1])

    return {
        "packageName": package_name,
        "className": class_name,
        "methodName": method_name,
    }


def get_test_key(test: dict) -> str:
    """Build a unique key for a parsed test dict."""
    if "fullName" in test and test["fullName"]:
        return test["fullName"]
    method = test.get("methodName", "")
    method = re.sub(r"\[([^\]]+)]\([^)]+\)", r"\1", method)
    return f"{test.get('packageName', '')}.{test.get('className', '')}.{method}"


# ---------------------------------------------------------------------------
# Data processing (mirrors processFlakyTestsData from JS)
# ---------------------------------------------------------------------------

def process_flaky_tests_data(raw_data: list) -> list:
    """Deduplicate and structure raw flaky test entries."""
    test_map: dict[str, dict] = {}

    for entry in raw_data:
        job = entry.get("job", "")
        flaky_tests_str = entry.get("flaky_tests", "")
        if not flaky_tests_str or not flaky_tests_str.strip():
            print(f'{PREFIX} Skipping job "{job}" - no flaky tests')
            continue

        # Tests are space-separated; each is: fully.qualified.Class.method(Params)[index]
        # Params are Java class names (no spaces), e.g. (CamundaClient, CamundaClient).
        test_names = re.findall(r"[^\s\[(]+(?:\([^)]*\))?(?:\[[^\]]*])?", flaky_tests_str)

        for test_name in test_names:
            parsed = parse_test_name(test_name)
            key = get_test_key(parsed)
            if key in test_map:
                existing = test_map[key]
                if job not in existing["jobs"]:
                    existing["jobs"].append(job)
                existing["currentRunFailures"] += 1
            else:
                test_map[key] = {
                    **parsed,
                    "jobs": [job],
                    "currentRunFailures": 1,
                }

    return list(test_map.values())


# ---------------------------------------------------------------------------
# GitHub helpers
# ---------------------------------------------------------------------------

COMMENT_MARKER = "<!-- new-flaky-tests-alert -->"


def set_output(name: str, value: str) -> None:
    output_file = os.environ.get("GITHUB_OUTPUT")
    if output_file:
        with open(output_file, "a") as fh:
            fh.write(f"{name}={value}\n")


def _github_api(url: str, token: str, method: str = "GET", data: bytes | None = None) -> bytes:
    req = urllib.request.Request(
        url,
        data=data,
        headers={
            "Authorization": f"token {token}",
            "Accept": "application/vnd.github+json",
            "Content-Type": "application/json",
        },
        method=method,
    )
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.read()
    except urllib.error.HTTPError as exc:
        body = exc.read().decode(errors="replace")
        print(f"{PREFIX} GitHub API error: {exc.status} {exc.reason} — {body}")
        sys.exit(1)


def _find_existing_comment(owner: str, repo: str, pr_number: int, token: str) -> int | None:
    """Find an existing comment with COMMENT_MARKER and return its id, or None."""
    page = 1
    while True:
        url = f"https://api.github.com/repos/{owner}/{repo}/issues/{pr_number}/comments?per_page=100&page={page}"
        raw = _github_api(url, token)
        comments = json.loads(raw)
        if not comments:
            break
        for c in comments:
            if COMMENT_MARKER in (c.get("body") or ""):
                return c["id"]
        page += 1
    return None


def post_or_update_comment(owner: str, repo: str, pr_number: int, body: str, token: str) -> None:
    """Create a new comment or update the existing one (matched by COMMENT_MARKER)."""
    existing_id = _find_existing_comment(owner, repo, pr_number, token)
    payload = json.dumps({"body": body}).encode()
    if existing_id:
        url = f"https://api.github.com/repos/{owner}/{repo}/issues/comments/{existing_id}"
        _github_api(url, token, method="PATCH", data=payload)
        print(f"{PREFIX} Updated existing comment (id={existing_id}).")
    else:
        url = f"https://api.github.com/repos/{owner}/{repo}/issues/{pr_number}/comments"
        _github_api(url, token, method="POST", data=payload)
        print(f"{PREFIX} Created new comment.")


def _resolve_comment(owner: str, repo: str, comment_id: int, token: str) -> None:
    """Update an existing warning comment to show it's resolved, keeping the old body as strikethrough."""
    # Fetch the current comment body
    url = f"https://api.github.com/repos/{owner}/{repo}/issues/comments/{comment_id}"
    raw = _github_api(url, token)
    current_body = json.loads(raw).get("body", "")
    # Strip the marker line — we'll re-add it at the top
    old_body = current_body.replace(COMMENT_MARKER, "").strip()
    # Wrap each line in strikethrough, preserving markdown structural syntax
    # (headings and list markers must stay outside ~~ or they break rendering)
    struck_lines = []
    for line in old_body.splitlines():
        stripped = line.strip()
        if not stripped:
            struck_lines.append("")
        elif stripped.startswith("#"):
            text = stripped.lstrip("#").strip()
            struck_lines.append(f"~~{text}~~")
        elif stripped.startswith("- "):
            indent = line[: len(line) - len(line.lstrip())]
            struck_lines.append(f"{indent}- ~~{stripped[2:]}~~")
        else:
            struck_lines.append(f"~~{stripped}~~")
    struck = "\n".join(struck_lines)
    resolved_body = "\n".join([
        COMMENT_MARKER,
        "# ✅ Resolved — No New Flaky Tests",
        "",
        "A previous CI run flagged new flaky tests, but the latest run found none.",
        "",
        "<details>",
        "<summary>Previous warning (resolved)</summary>",
        "",
        struck,
        "",
        "</details>",
    ])
    payload = json.dumps({"body": resolved_body}).encode()
    _github_api(url, token, method="PATCH", data=payload)
    print(f"{PREFIX} Updated comment (id={comment_id}) to resolved.")


# ---------------------------------------------------------------------------
# Test comparison
# ---------------------------------------------------------------------------

def _is_same_test(baseline_key: str, normalized_key: str) -> bool:
    """Check if a BigQuery baseline key matches a normalized PR key.

    Baseline keys (from BigQuery) may have trailing params/suffixes that
    parse_test_name strips:
      baseline:   "pkg.Class.method(Param) variant"
      normalized: "pkg.Class.method"

    We accept a match if the baseline key equals the normalized key, or starts
    with the normalized key followed by a non-alphanumeric, non-underscore char
    (to avoid "shouldFind" matching "shouldFindAll").
    """
    if baseline_key == normalized_key:
        return True
    if not baseline_key.startswith(normalized_key):
        return False
    # The character right after the normalized key must be a boundary
    next_char = baseline_key[len(normalized_key)]
    return not next_char.isalnum() and next_char != "_"


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> None:
    pr_flaky_json = os.environ.get("PR_FLAKY_TESTS_DATA", "")
    baseline_flaky_json = os.environ.get("KNOWN_FLAKY_TESTS", "")
    pr_number = int(os.environ.get("PR_NUMBER", "0"))
    github_token = os.environ.get("GITHUB_TOKEN", "")
    github_repository = os.environ.get("GITHUB_REPOSITORY", "")
    blocking = os.environ.get("BLOCKING", "true").lower() == "true"

    # -- Parse PR flaky tests ------------------------------------------------
    print(f"\n{'='*80}")
    print(f"{PREFIX} === STEP 1: Parse PR flaky tests ===")
    print(f"{'='*80}")

    if not pr_flaky_json or pr_flaky_json.strip() in ("", "[]"):
        print(f"{PREFIX} No PR flaky tests data provided - nothing to check.")
        set_output("has-new-flaky-tests", "false")
        return

    try:
        pr_flaky_entries = json.loads(pr_flaky_json)
    except json.JSONDecodeError as exc:
        print(f"{PREFIX} Failed to parse PR flaky tests data: {exc}")
        set_output("has-new-flaky-tests", "false")
        return

    if not isinstance(pr_flaky_entries, list) or len(pr_flaky_entries) == 0:
        print(f"{PREFIX} No PR flaky tests found.")
        set_output("has-new-flaky-tests", "false")
        return

    print(f"{PREFIX} Raw PR flaky test entries ({len(pr_flaky_entries)} jobs):")
    for i, entry in enumerate(pr_flaky_entries):
        job = entry.get('job', '<unknown>')
        tests = entry.get('flaky_tests', '')
        print(f"{PREFIX}   [{i+1}] job='{job}' flaky_tests='{tests}'")

    pr_flaky_tests = process_flaky_tests_data(pr_flaky_entries)
    if not pr_flaky_tests:
        print(f"{PREFIX} No processed flaky tests from PR.")
        set_output("has-new-flaky-tests", "false")
        return

    print(f"\n{PREFIX} Processed PR flaky tests ({len(pr_flaky_tests)} unique):")
    for i, test in enumerate(pr_flaky_tests):
        key = get_test_key(test)
        print(f"{PREFIX}   [{i+1}] key='{key}' jobs={test['jobs']} retries={test['currentRunFailures']}")

    # -- Parse known flaky tests from BigQuery --------------------------------
    print(f"\n{'='*80}")
    print(f"{PREFIX} === STEP 2: Parse known flaky tests (BigQuery baseline) ===")
    print(f"{'='*80}")

    baseline_flaky_tests: list[dict] = []
    try:
        if baseline_flaky_json and baseline_flaky_json.strip():
            baseline_flaky_tests = json.loads(baseline_flaky_json)
    except json.JSONDecodeError as exc:
        print(f"{PREFIX} Failed to parse baseline flaky tests - treating all PR flaky tests as new: {exc}")

    print(f"{PREFIX} Baseline flaky tests from main/stable (last 60 days): {len(baseline_flaky_tests)} entries")

    # BigQuery test_name may include parameters and suffixes, e.g.
    #   "shouldDoSomething(CamundaRdbmsTestApplication) camundaWithOracleDB"
    # Keep the raw keys for full fidelity in debug output.
    baseline_keys: set[str] = set()
    for entry in baseline_flaky_tests:
        key = f"{entry['test_class_name']}.{entry['test_name']}"
        baseline_keys.add(key)

    print(f"{PREFIX} Unique baseline flaky test keys: {len(baseline_keys)}")
    for i, key in enumerate(sorted(baseline_keys)):
        print(f"{PREFIX}   [{i+1}] {key}")

    # -- Compare --------------------------------------------------------------
    print(f"\n{'='*80}")
    print(f"{PREFIX} === STEP 3: Compare PR flaky tests against known baseline ===")
    print(f"{'='*80}")

    new_flaky_tests: list[dict] = []
    for test in pr_flaky_tests:
        normalized_key = get_test_key(test)
        # Normalized keys have no params; baseline keys may have params/suffixes.
        # Match if the baseline key is the same test method (exact or with param suffix).
        is_known = any(_is_same_test(k, normalized_key) for k in baseline_keys)
        matched = [k for k in baseline_keys if _is_same_test(k, normalized_key)] if is_known else []
        status = f"KNOWN (matched: {matched})" if is_known else "NEW (not seen on main/stable)"
        print(f"{PREFIX}   {normalized_key} → {status}")
        if not is_known:
            new_flaky_tests.append(test)

    print(f"\n{PREFIX} --- Result ---")
    print(f"{PREFIX} Total PR flaky tests: {len(pr_flaky_tests)}")
    print(f"{PREFIX} Already known:        {len(pr_flaky_tests) - len(new_flaky_tests)}")
    print(f"{PREFIX} NEW flaky tests:      {len(new_flaky_tests)}")

    if not new_flaky_tests:
        print(f"{PREFIX} ✅ All flaky tests in this PR are already known. No new flaky tests.")
        set_output("has-new-flaky-tests", "false")
        # If a previous run left a warning comment, update it to "resolved".
        owner, repo = github_repository.split("/", 1)
        existing_id = _find_existing_comment(owner, repo, pr_number, github_token)
        if existing_id:
            _resolve_comment(owner, repo, existing_id, github_token)
        return

    print(f"\n{PREFIX} ❌ Found {len(new_flaky_tests)} NEW flaky test(s)!")
    for i, test in enumerate(new_flaky_tests):
        key = get_test_key(test)
        print(f"{PREFIX}   [{i+1}] {key}")
        print(f"{PREFIX}        jobs: {', '.join(test['jobs'])}")
        print(f"{PREFIX}        retries: {test['currentRunFailures']}")

    # -- Build alert comment --------------------------------------------------
    lines = [
        COMMENT_MARKER,
        "# ⚠️ New Flaky Tests Detected",
        "",
        f"This PR introduces **{len(new_flaky_tests)} new flaky test(s)** that are not currently flaky on `main` or `stable/*` branches.",
        "",
    ]

    for test in new_flaky_tests:
        test_name = test.get("methodName") or test.get("fullName", "unknown")
        lines.append(f"- **{test_name}**")
        lines.append(f"  - Jobs: `{', '.join(test['jobs'])}`")
        if test.get("packageName"):
            lines.append(f"  - Package: `{test['packageName']}`")
        if test.get("className"):
            lines.append(f"  - Class: `{test['className']}`")
        lines.append(f"  - Retries in this run: {test['currentRunFailures']}")
        lines.append("")

    lines.extend(
        [
            "---",
            "",
            "**What to do:**",
            "1. Check if the flaky test is caused by your changes and fix it",
            "2. If the test is unrelated to your changes:",
            "   - Contact the monorepo devops (#top-monorepo-ci) to triage and discuss the next steps",
            "   - Create an issue with the `kind/flake` label documenting the test, job, and a link to this CI run",
            "   - Add the `ci:flaky-test-bypass` label to this PR to skip the gate and unblock merging",
            "   - Re-run CI after adding the label",
            "",
            "_This check compares flaky tests in this PR against tests known to be flaky on `main`/`stable/*` in the last 60 days._",
        ]
    )

    comment_body = "\n".join(lines)

    # -- Post or update comment ------------------------------------------------
    owner, repo = github_repository.split("/", 1)
    post_or_update_comment(owner, repo, pr_number, comment_body, github_token)

    set_output("has-new-flaky-tests", "true")
    if blocking:
        print(f"::error::{len(new_flaky_tests)} new flaky test(s) detected. See the PR comment for details.")
        sys.exit(1)
    else:
        print(f"::warning::{len(new_flaky_tests)} new flaky test(s) detected (non-blocking mode). See the PR comment for details.")


if __name__ == "__main__":
    main()
