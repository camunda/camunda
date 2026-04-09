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

def set_output(name: str, value: str) -> None:
    output_file = os.environ.get("GITHUB_OUTPUT")
    if output_file:
        with open(output_file, "a") as fh:
            fh.write(f"{name}={value}\n")


def post_comment(owner: str, repo: str, pr_number: int, body: str, token: str) -> None:
    url = f"https://api.github.com/repos/{owner}/{repo}/issues/{pr_number}/comments"
    data = json.dumps({"body": body}).encode()
    req = urllib.request.Request(
        url,
        data=data,
        headers={
            "Authorization": f"token {token}",
            "Accept": "application/vnd.github+json",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req) as resp:
            if resp.status < 200 or resp.status >= 300:
                print(f"{PREFIX} GitHub API responded with status {resp.status}")
                sys.exit(1)
    except urllib.error.HTTPError as exc:
        print(f"{PREFIX} Failed to post comment: {exc.status} {exc.reason}")
        sys.exit(1)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> None:
    pr_flaky_input = os.environ.get("PR_FLAKY_TESTS_DATA", "")
    known_flaky_input = os.environ.get("KNOWN_FLAKY_TESTS", "")
    pr_number = int(os.environ.get("PR_NUMBER", "0"))
    github_token = os.environ.get("GITHUB_TOKEN", "")
    github_repository = os.environ.get("GITHUB_REPOSITORY", "")

    # -- Parse PR flaky tests ------------------------------------------------
    print(f"\n{'='*80}")
    print(f"{PREFIX} === STEP 1: Parse PR flaky tests ===")
    print(f"{'='*80}")

    if not pr_flaky_input or pr_flaky_input.strip() in ("", "[]"):
        print(f"{PREFIX} No PR flaky tests data provided - nothing to check.")
        set_output("has-new-flaky-tests", "false")
        return

    try:
        pr_flaky_raw = json.loads(pr_flaky_input)
    except json.JSONDecodeError as exc:
        print(f"{PREFIX} Failed to parse PR flaky tests data: {exc}")
        set_output("has-new-flaky-tests", "false")
        return

    if not isinstance(pr_flaky_raw, list) or len(pr_flaky_raw) == 0:
        print(f"{PREFIX} No PR flaky tests found.")
        set_output("has-new-flaky-tests", "false")
        return

    print(f"{PREFIX} Raw PR flaky test entries ({len(pr_flaky_raw)} jobs):")
    for i, entry in enumerate(pr_flaky_raw):
        job = entry.get('job', '<unknown>')
        tests = entry.get('flaky_tests', '')
        print(f"{PREFIX}   [{i+1}] job='{job}' flaky_tests='{tests}'")

    processed_pr_tests = process_flaky_tests_data(pr_flaky_raw)
    if not processed_pr_tests:
        print(f"{PREFIX} No processed flaky tests from PR.")
        set_output("has-new-flaky-tests", "false")
        return

    print(f"\n{PREFIX} Processed PR flaky tests ({len(processed_pr_tests)} unique):")
    for i, test in enumerate(processed_pr_tests):
        key = get_test_key(test)
        print(f"{PREFIX}   [{i+1}] key='{key}' jobs={test['jobs']} retries={test['currentRunFailures']}")

    # -- Parse known flaky tests from BigQuery --------------------------------
    print(f"\n{'='*80}")
    print(f"{PREFIX} === STEP 2: Parse known flaky tests (BigQuery baseline) ===")
    print(f"{'='*80}")

    known_flaky_tests: list[dict] = []
    try:
        if known_flaky_input and known_flaky_input.strip():
            known_flaky_tests = json.loads(known_flaky_input)
    except json.JSONDecodeError as exc:
        print(f"{PREFIX} Failed to parse known flaky tests - treating all PR flaky tests as new: {exc}")

    print(f"{PREFIX} Known flaky tests from main/stable (last 30 days): {len(known_flaky_tests)} entries")

    # BigQuery test_name may include parameters and suffixes, e.g.
    #   "shouldDoSomething(CamundaRdbmsTestApplication) camundaWithOracleDB"
    # Keep the raw keys for full fidelity in debug output.
    known_keys: set[str] = set()
    for known in known_flaky_tests:
        key = f"{known['test_class_name']}.{known['test_name']}"
        known_keys.add(key)

    print(f"{PREFIX} Unique known flaky test keys: {len(known_keys)}")
    for i, key in enumerate(sorted(known_keys)):
        print(f"{PREFIX}   [{i+1}] {key}")

    # -- Compare --------------------------------------------------------------
    print(f"\n{'='*80}")
    print(f"{PREFIX} === STEP 3: Compare PR flaky tests against known baseline ===")
    print(f"{'='*80}")

    new_flaky_tests: list[dict] = []
    for test in processed_pr_tests:
        pr_key = get_test_key(test)
        # PR keys are normalized (no params), BigQuery keys may have params/suffixes.
        # Match if the PR key equals the known key OR the known key starts with the PR key.
        is_known = any(k == pr_key or k.startswith(pr_key) for k in known_keys)
        matched = [k for k in known_keys if k == pr_key or k.startswith(pr_key)] if is_known else []
        status = f"KNOWN (matched: {matched})" if is_known else "NEW (not seen on main/stable)"
        print(f"{PREFIX}   {pr_key} → {status}")
        if not is_known:
            new_flaky_tests.append(test)

    print(f"\n{PREFIX} --- Result ---")
    print(f"{PREFIX} Total PR flaky tests: {len(processed_pr_tests)}")
    print(f"{PREFIX} Already known:        {len(processed_pr_tests) - len(new_flaky_tests)}")
    print(f"{PREFIX} NEW flaky tests:      {len(new_flaky_tests)}")

    if not new_flaky_tests:
        print(f"{PREFIX} ✅ All flaky tests in this PR are already known. No new flaky tests.")
        set_output("has-new-flaky-tests", "false")
        return

    print(f"\n{PREFIX} ❌ Found {len(new_flaky_tests)} NEW flaky test(s)!")
    for i, test in enumerate(new_flaky_tests):
        key = get_test_key(test)
        print(f"{PREFIX}   [{i+1}] {key}")
        print(f"{PREFIX}        jobs: {', '.join(test['jobs'])}")
        print(f"{PREFIX}        retries: {test['currentRunFailures']}")

    # -- Build alert comment --------------------------------------------------
    lines = [
        "<!-- new-flaky-tests-alert -->",
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
            "1. Check if the flaky test is caused by your changes",
            "2. Fix the test if possible",
            "3. If the test is unrelated to your changes, create a `kind/flake` issue and link it in a comment",
            "",
            "_This check compares flaky tests in this PR against tests known to be flaky on `main`/`stable/*` in the last 30 days._",
        ]
    )

    comment_body = "\n".join(lines)

    # -- Post comment ---------------------------------------------------------
    owner, repo = github_repository.split("/", 1)
    post_comment(owner, repo, pr_number, comment_body, github_token)
    print(f"{PREFIX} Posted new flaky tests alert comment.")

    set_output("has-new-flaky-tests", "true")
    print(f"::error::{len(new_flaky_tests)} new flaky test(s) detected. See the PR comment for details.")
    sys.exit(1)


if __name__ == "__main__":
    main()
