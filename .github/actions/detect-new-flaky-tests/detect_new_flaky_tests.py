#!/usr/bin/env python3
"""Detect new flaky tests introduced by a PR with sticky alert state.

A flaky-test alert is "sticky": once a test is flagged on a PR it remains
flagged until either:
  (a) the ci:flaky-test-bypass label is applied, or
  (b) the test method body is modified AND >= MIN_CLEAN_RUNS subsequent gate
      runs observe the test passing (zero Maven retries) in the originally
      affected job.

State is persisted between gate runs via a per-PR workflow artifact (JSON).
The PR comment is a human-readable render of that state.

Inputs (environment variables):
  PR_FLAKY_TESTS_DATA    – JSON array of {job, flaky_tests}
  KNOWN_FLAKY_TESTS_FILE – Path to JSON file with [{test_class_name, test_name}]
  PR_NUMBER              – Pull request number
  GITHUB_TOKEN           – GitHub API token
  GITHUB_REPOSITORY      – owner/repo
  GITHUB_OUTPUT          – Path to the GHA step output file
  STATE_FILE_IN          – Path to existing state.json (may not exist on first run)
  STATE_FILE_OUT         – Path to write updated state.json
  RAN_JOBS_JSON          – JSON array of parent job names whose result is not 'skipped'
  BYPASS_LABEL_PRESENT   – 'true' / 'false'
  HEAD_SHA               – Current PR head SHA
  BASE_REF               – PR base branch (e.g. 'main' or 'stable/8.7')
  BLOCKING               – 'true' / 'false' — fail the job if any test is still active
  REPO_ROOT              – Absolute path to the checked-out repo (for git/find)
"""

import datetime as _dt
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request

PREFIX = "[new-flaky]"
COMMENT_MARKER = "<!-- new-flaky-tests-alert -->"
STATE_ARTIFACT_MARKER_PREFIX = "<!-- flaky-gate-state-artifact: "
SCHEMA_VERSION = 1
MIN_CLEAN_RUNS = 3


# ---------------------------------------------------------------------------
# Test-name parsing (kept compatible with parse_test_name in helpers.js)
# ---------------------------------------------------------------------------

def parse_test_name(test_name: str) -> dict:
    bare = re.sub(r"\[.*?]\s*$", "", test_name.strip()).strip()
    bare = re.sub(r"\(.*?\)\s*$", "", bare).strip()
    last_dot = bare.rfind(".")
    if last_dot == -1:
        return {"fullName": test_name.strip()}
    fqc = bare[:last_dot]
    method_name = bare[last_dot + 1:].strip()
    parts = fqc.split(".")
    return {
        "packageName": ".".join(parts[:-1]),
        "className": parts[-1],
        "methodName": method_name,
    }


def get_test_key(test: dict) -> str:
    if test.get("fullName"):
        return test["fullName"]
    method = test.get("methodName", "")
    method = re.sub(r"\[([^\]]+)]\([^)]+\)", r"\1", method)
    return f"{test.get('packageName', '')}.{test.get('className', '')}.{method}"


def process_flaky_tests_data(raw_data: list) -> list[dict]:
    test_map: dict[str, dict] = {}
    for entry in raw_data:
        job = entry.get("job", "")
        flaky_tests_str = entry.get("flaky_tests", "")
        if not flaky_tests_str or not flaky_tests_str.strip():
            continue
        for test_name in re.findall(r"[^\s\[(]+(?:\([^)]*\))?(?:\[[^\]]*])?", flaky_tests_str):
            parsed = parse_test_name(test_name)
            m = parsed.get("methodName", "")
            if m.startswith("<") and m.endswith(">"):
                continue
            key = get_test_key(parsed)
            if key in test_map:
                if job not in test_map[key]["jobs"]:
                    test_map[key]["jobs"].append(job)
                test_map[key]["currentRunFailures"] += 1
            else:
                test_map[key] = {**parsed, "jobs": [job], "currentRunFailures": 1}
    return list(test_map.values())


# ---------------------------------------------------------------------------
# State management
# ---------------------------------------------------------------------------

def empty_state(pr_number: int, head_sha: str) -> dict:
    return {
        "schema_version": SCHEMA_VERSION,
        "pr_number": pr_number,
        "last_known_head_sha": head_sha,
        "last_updated_at": _now_iso(),
        "tests": [],
    }


def load_state(path: str, pr_number: int, head_sha: str) -> dict:
    if not path or not os.path.isfile(path):
        print(f"{PREFIX} No prior state file at {path!r} — starting fresh.")
        return empty_state(pr_number, head_sha)
    try:
        with open(path, encoding="utf-8") as fh:
            state = json.load(fh)
    except (OSError, json.JSONDecodeError) as exc:
        print(f"{PREFIX} WARN: Could not read prior state ({exc}) — starting fresh.")
        return empty_state(pr_number, head_sha)
    if not isinstance(state, dict) or state.get("schema_version") != SCHEMA_VERSION:
        print(f"{PREFIX} WARN: State schema mismatch — starting fresh.")
        return empty_state(pr_number, head_sha)
    return state


def save_state(path: str, state: dict) -> None:
    state["last_updated_at"] = _now_iso()
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(state, fh, indent=2, sort_keys=True)


def _now_iso() -> str:
    return _dt.datetime.now(_dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


# ---------------------------------------------------------------------------
# Git helpers
# ---------------------------------------------------------------------------

def _run_git(args: list[str], repo_root: str) -> tuple[int, str, str]:
    proc = subprocess.run(
        ["git", *args],
        cwd=repo_root,
        check=False,
        text=True,
        capture_output=True,
    )
    return proc.returncode, proc.stdout, proc.stderr


def get_merge_base(head_sha: str, base_ref: str, repo_root: str,
                   base_sha: str | None = None) -> str | None:
    # base_sha is tried first: a PR merge-ref checkout has the base commit
    # reachable but usually no local main/origin/main ref to resolve base_ref.
    candidates = [base_sha, base_ref,
                  f"origin/{base_ref}", f"refs/remotes/origin/{base_ref}"]
    for ref in candidates:
        if not ref:
            continue
        rc, out, _ = _run_git(["merge-base", ref, head_sha], repo_root)
        if rc == 0 and out.strip():
            return out.strip()
    return None


def is_sha_reachable(sha: str, head_sha: str, repo_root: str) -> bool:
    if not sha:
        return False
    rc, _, _ = _run_git(["merge-base", "--is-ancestor", sha, head_sha], repo_root)
    return rc == 0


def find_class_file(package: str, class_name: str, repo_root: str) -> str | None:
    """Locate the .java file for a given test class via git ls-files.

    Prefers a path matching the package; falls back to a sole candidate.
    Returns repo-relative path or None.
    """
    if not class_name:
        return None
    rc, out, _ = _run_git(["ls-files", f"**/{class_name}.java"], repo_root)
    if rc != 0:
        return None
    candidates = [line for line in out.splitlines() if line.strip()]
    if not candidates:
        return None
    if package:
        suffix = package.replace(".", "/") + "/" + class_name + ".java"
        exact = [c for c in candidates if c.endswith(suffix)]
        if exact:
            return exact[0]
    if len(candidates) == 1:
        return candidates[0]
    return None


def method_last_modified_in_range(file_path: str, method_name: str,
                                   base_sha: str, head_sha: str,
                                   repo_root: str) -> str | None:
    """Return the SHA of the most recent commit that touched the body of
    method_name in file_path within base_sha..head_sha. None if not found.

    Uses git log -L which leverages the language-aware funcname matcher.
    Java overloads sharing the method name are matched together (accepted
    heuristic — see README).
    """
    if not file_path or not method_name or not base_sha or not head_sha:
        return None
    safe = re.escape(method_name)
    rc, out, err = _run_git(
        ["log", "--format=%H", f"-L:{safe}:{file_path}",
         f"{base_sha}..{head_sha}"],
        repo_root,
    )
    if rc != 0:
        print(f"{PREFIX} git log -L failed for {method_name} in {file_path}: {err.strip()}")
        return None
    for line in out.splitlines():
        sha = line.strip()
        if sha and re.fullmatch(r"[0-9a-f]{7,40}", sha):
            return sha  # First line of --format=%H output = most recent
    return None


# ---------------------------------------------------------------------------
# Sticky-state reconciliation
# ---------------------------------------------------------------------------

def reconcile_state(
    state: dict,
    current_flake_keys: set[str],
    head_sha: str,
    base_sha: str | None,
    repo_root: str,
) -> None:
    """Apply force-push handling and modification detection.

    Operates in place on state["tests"]. Counter changes happen elsewhere.
    """
    now = _now_iso()
    survivors: list[dict] = []

    for entry in state.get("tests", []):
        if entry.get("status") not in (None, "active"):
            survivors.append(entry)
            continue

        # Q5d: anchor unreachable AND test not flaking now → branch wiped
        # the offending code; drop the entry.
        first_sha = entry.get("first_flagged_sha")
        if first_sha and not is_sha_reachable(first_sha, head_sha, repo_root):
            if entry["key"] not in current_flake_keys:
                entry["status"] = "dropped_force_push"
                entry["cleared_at"] = now
                survivors.append(entry)
                print(f"{PREFIX} Dropping {entry['key']} — anchor unreachable, test not in current run.")
                continue

        # Q5a/Q1: re-evaluate method modification against current history.
        file_path = entry.get("file_path")
        if not file_path:
            file_path = find_class_file(entry.get("package", ""),
                                         entry.get("class_name", ""),
                                         repo_root)
            entry["file_path"] = file_path
        last_mod = None
        if file_path and base_sha:
            last_mod = method_last_modified_in_range(
                file_path, entry["method_name"], base_sha, head_sha, repo_root)
        prev_last_mod = entry.pop("_prev_last_mod", None)
        entry["method_last_modified_sha"] = last_mod

        # Q4c: if the modification SHA changed (new commit touched the method),
        # reset the counter to demand fresh evidence.
        if last_mod and prev_last_mod != last_mod:
            entry["clean_runs_since_modified"] = 0

        survivors.append(entry)

    state["tests"] = survivors


def merge_new_flakes(
    state: dict,
    pr_flaky_tests: list[dict],
    head_sha: str,
    repo_root: str,
) -> None:
    """Add not-yet-tracked flakes; reset counter for re-flaked active entries;
    demote cleared entries back to active on re-flake (Q4d)."""
    now = _now_iso()
    by_key: dict[str, dict] = {e["key"]: e for e in state["tests"]}

    for test in pr_flaky_tests:
        key = get_test_key(test)
        existing = by_key.get(key)
        jobs = test.get("jobs", [])

        if existing and existing.get("status") == "active":
            existing["clean_runs_since_modified"] = 0
            existing["last_observed_sha"] = head_sha
            existing["last_observed_at"] = now
            for j in jobs:
                if j not in existing["flagged_jobs"]:
                    existing["flagged_jobs"].append(j)
            continue

        if existing and existing.get("status") in (
            "cleared_via_fix", "cleared_via_bypass", "dropped_force_push"
        ):
            existing["status"] = "active"
            existing["first_flagged_sha"] = head_sha
            existing["flagged_jobs"] = list(jobs)
            existing["method_last_modified_sha"] = None
            existing["clean_runs_since_modified"] = 0
            existing["last_observed_sha"] = head_sha
            existing["last_observed_at"] = now
            existing.pop("cleared_at", None)
            continue

        # Brand new entry.
        file_path = find_class_file(test.get("packageName", ""),
                                     test.get("className", ""),
                                     repo_root)
        state["tests"].append({
            "key": key,
            "package": test.get("packageName", ""),
            "class_name": test.get("className", ""),
            "method_name": test.get("methodName", ""),
            "file_path": file_path,
            "first_flagged_sha": head_sha,
            "flagged_jobs": list(jobs),
            "method_last_modified_sha": None,
            "clean_runs_since_modified": 0,
            "last_observed_sha": head_sha,
            "last_observed_at": now,
            "status": "active",
            "cleared_at": None,
        })


def increment_counters_if_clean(
    state: dict,
    current_flake_keys: set[str],
    ran_parent_jobs: set[str],
    head_sha: str,
) -> None:
    """Q3a Def-2: per active entry with a modification, increment counter iff
    the test was observed (job ran) AND did not appear in FLAKY.xml.

    Q3b: only counts if at least one of the entry's originally flagged
    parent jobs ran in this CI run.
    """
    now = _now_iso()
    for entry in state["tests"]:
        if entry.get("status") != "active":
            continue
        # Re-flake resets the counter for every case — including a baseline-known
        # re-flake that merge_new_flakes never sees (it only gets new_flaky_tests).
        # Checked before the modification guard so the reset holds unconditionally.
        if entry["key"] in current_flake_keys:
            entry["clean_runs_since_modified"] = 0
            continue
        if not entry.get("method_last_modified_sha"):
            continue
        parents = {j.split("/", 1)[0] for j in entry.get("flagged_jobs", [])}
        if not (parents & ran_parent_jobs):
            continue
        entry["clean_runs_since_modified"] = entry.get("clean_runs_since_modified", 0) + 1
        entry["last_observed_sha"] = head_sha
        entry["last_observed_at"] = now


def apply_clearance(state: dict) -> None:
    now = _now_iso()
    for entry in state["tests"]:
        if entry.get("status") != "active":
            continue
        if entry.get("clean_runs_since_modified", 0) >= MIN_CLEAN_RUNS:
            entry["status"] = "cleared_via_fix"
            entry["cleared_at"] = now


def apply_bypass(state: dict) -> None:
    now = _now_iso()
    for entry in state["tests"]:
        if entry.get("status") == "active":
            entry["status"] = "cleared_via_bypass"
            entry["cleared_at"] = now


# ---------------------------------------------------------------------------
# Comment rendering
# ---------------------------------------------------------------------------

def _short(sha: str | None) -> str:
    if not sha:
        return "—"
    if re.fullmatch(r"[0-9a-f]{7,40}", sha):
        return f"`{sha[:7]}`"
    return sha


def _render_state_block(entry: dict) -> list[str]:
    last_mod = entry.get("method_last_modified_sha")
    last_mod_disp = _short(last_mod) if last_mod else "— (no fix detected)"
    counter = entry.get("clean_runs_since_modified", 0)
    return [
        "  - **State:**",
        f"    - First flagged at: {_short(entry.get('first_flagged_sha'))}",
        f"    - Method last modified at: {last_mod_disp}",
        f"    - Clean re-runs since fix: {counter} / {MIN_CLEAN_RUNS}",
        f"    - Last observed: {entry.get('last_observed_at', '—')}",
    ]


def _render_active_test(entry: dict) -> list[str]:
    lines = [f"- **{entry['method_name']}**"]
    lines.append(f"  - Jobs: `{', '.join(entry.get('flagged_jobs', []))}`")
    if entry.get("package"):
        lines.append(f"  - Package: `{entry['package']}`")
    if entry.get("class_name"):
        lines.append(f"  - Class: `{entry['class_name']}`")
    lines.extend(_render_state_block(entry))
    return lines


def _render_cleared_test(entry: dict) -> list[str]:
    status = entry.get("status", "")
    sha = entry.get("method_last_modified_sha")
    sha_disp = _short(sha)
    if status == "cleared_via_fix":
        reason = f"cleared on {sha_disp} after {MIN_CLEAN_RUNS} clean re-runs"
    elif status == "cleared_via_bypass":
        reason = "cleared via `ci:flaky-test-bypass` label"
    elif status == "dropped_force_push":
        reason = "dropped (anchor commit no longer in branch history)"
    else:
        reason = f"cleared ({status})"
    return [
        f"- ~~**{entry['method_name']}**~~",
        f"  - ~~Class: `{entry.get('class_name', '')}`~~",
        f"  - ~~{reason}~~",
    ]


def render_comment(state: dict, artifact_name: str) -> str:
    active = [e for e in state["tests"] if e.get("status") == "active"]
    cleared = [e for e in state["tests"] if e.get("status") in
               ("cleared_via_fix", "cleared_via_bypass", "dropped_force_push")]

    lines = [
        COMMENT_MARKER,
        f"{STATE_ARTIFACT_MARKER_PREFIX}{artifact_name} -->",
    ]

    if active:
        lines.extend([
            "# ⚠️ New Flaky Tests Detected",
            "",
            f"This PR introduces **{len(active)} new flaky test(s)** that are "
            "not currently flaky on `main`, `stable/*`, or in any other open PR.",
            "",
        ])
        for entry in active:
            lines.extend(_render_active_test(entry))
            lines.append("")
        lines.extend([
            "---",
            "",
            "**What to do:**",
            "1. Fix the flaky test method, push the commit, then let CI run "
            f"{MIN_CLEAN_RUNS} times. The counter advances only when the "
            "originally affected job runs clean (no Maven retries).",
            "2. If unrelated to your changes: add the `ci:flaky-test-bypass` "
            "label and create a `kind/flake` issue.",
            "",
            "_This check compares flaky tests in this PR against tests known "
            "to be flaky on `main`/`stable/*` or other PRs in the last 20 "
            "days. Once flagged, the alert is sticky — a passing re-run does "
            "not clear it._",
        ])
        if cleared:
            lines.extend([
                "",
                "<details>",
                f"<summary>{len(cleared)} cleared test(s) (history)</summary>",
                "",
            ])
            for entry in cleared:
                lines.extend(_render_cleared_test(entry))
            lines.extend(["", "</details>"])
    else:
        lines.extend([
            "# ✅ Cleared — No outstanding new flakes",
            "",
            f"All previously flagged tests cleared via fix + {MIN_CLEAN_RUNS} "
            "clean re-runs, or via `ci:flaky-test-bypass` label.",
            "",
        ])
        if cleared:
            lines.extend(["<details>", "<summary>Previous warning</summary>", ""])
            for entry in cleared:
                lines.extend(_render_cleared_test(entry))
            lines.extend(["", "</details>"])

    return "\n".join(lines)


# ---------------------------------------------------------------------------
# GitHub helpers
# ---------------------------------------------------------------------------

class GitHubAPIError(Exception):
    pass


def _github_api(url: str, token: str, method: str = "GET",
                data: bytes | None = None) -> bytes:
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
        raise GitHubAPIError(f"{exc.status} {exc.reason} — {body}") from exc


def _find_existing_comment(owner: str, repo: str, pr: int, token: str) -> int | None:
    page = 1
    while True:
        url = f"https://api.github.com/repos/{owner}/{repo}/issues/{pr}/comments?per_page=100&page={page}"
        raw = _github_api(url, token)
        comments = json.loads(raw)
        if not comments:
            return None
        for c in comments:
            if COMMENT_MARKER in (c.get("body") or ""):
                return c["id"]
        page += 1


def post_or_update_comment(owner: str, repo: str, pr: int, body: str, token: str) -> None:
    existing = _find_existing_comment(owner, repo, pr, token)
    payload = json.dumps({"body": body}).encode()
    if existing:
        url = f"https://api.github.com/repos/{owner}/{repo}/issues/comments/{existing}"
        _github_api(url, token, method="PATCH", data=payload)
    else:
        url = f"https://api.github.com/repos/{owner}/{repo}/issues/{pr}/comments"
        _github_api(url, token, method="POST", data=payload)


def set_output(name: str, value: str) -> None:
    out_file = os.environ.get("GITHUB_OUTPUT")
    if out_file:
        with open(out_file, "a") as fh:
            fh.write(f"{name}={value}\n")


# ---------------------------------------------------------------------------
# Baseline matching
# ---------------------------------------------------------------------------

def load_baseline_keys(path: str) -> tuple[set[str], set[str]]:
    """Return (baseline_keys, blank_class_fqns).

    baseline_keys: full FQN keys for method-level matching.
    blank_class_fqns: class FQNs with at least one blank test_name entry in BQ
                      (class/container-level infra failures recorded without a method name).
    """
    baseline_keys: set[str] = set()
    blank_class_fqns: set[str] = set()
    if not path or not os.path.isfile(path):
        return baseline_keys, blank_class_fqns
    try:
        with open(path, encoding="utf-8") as fh:
            for row in json.load(fh):
                baseline_keys.add(f"{row['test_class_name']}.{row['test_name']}")
                if not row.get("test_name"):
                    blank_class_fqns.add(row["test_class_name"])
    except (OSError, json.JSONDecodeError, KeyError) as exc:
        print(f"{PREFIX} WARN: baseline read failed ({exc}) — treating as empty.")
    return baseline_keys, blank_class_fqns


def is_in_baseline(normalized_key: str, baseline_keys: set[str]) -> bool:
    for bk in baseline_keys:
        if bk == normalized_key:
            return True
        if bk.startswith(normalized_key):
            nxt = bk[len(normalized_key)]
            if not nxt.isalnum() and nxt != "_":
                return True
    return False


def get_pr_changed_paths(
    base_ref: str, head_sha: str, repo_root: str,
    base_sha_input: str = "",
) -> tuple[set[str], set[str], bool]:
    """Return (changed_package_paths, changed_java_filenames, available).

    changed_package_paths: Java package paths like "io/camunda/zeebe/backup/gcs"
    changed_java_filenames: basenames like "GcsBackupStoreIT.java"
    available: True when the git diff succeeded (even if no .java files changed);
               False when the diff could not be computed — callers must skip the
               filter in that case to avoid accidental suppression.

    Distinguishing these two empty-set cases is critical: a PR that changes only
    YAML/config files (no .java) is a legitimate no-Java-touch signal and should
    still trigger suppression; a failed diff gives no signal at all.

    Uses get_merge_base() to resolve the diff base, which tries BASE_SHA first so
    this works in PR merge-ref checkouts where origin/<base_ref> is not present.
    """
    if not head_sha:
        return set(), set(), False
    merge_base = get_merge_base(head_sha, base_ref, repo_root, base_sha_input or None)
    if not merge_base:
        print(f"{PREFIX} WARN: could not resolve merge-base for touch-check — filter disabled.")
        return set(), set(), False
    rc, out, _ = _run_git(
        ["diff", "--name-only", f"{merge_base}...{head_sha}"],
        repo_root,
    )
    if rc != 0:
        print(f"{PREFIX} WARN: git diff for touch-check failed — filter disabled.")
        return set(), set(), False

    pkg_paths: set[str] = set()
    filenames: set[str] = set()
    for line in out.splitlines():
        if not line.endswith(".java"):
            continue
        filenames.add(line.rsplit("/", 1)[-1])
        m = re.search(r"/java/(.+)/[^/]+\.java$", line)
        if m:
            pkg_paths.add(m.group(1))
    return pkg_paths, filenames, True


def _package_touched(pkg_path: str, changed_pkg_paths: set[str]) -> bool:
    """Return True if pkg_path is the same as, a parent of, or a child of any changed package.

    Examples (pkg_path → changed_pkg_paths → result):
      "io/a/b"       {io/a/b}           → True  (exact match)
      "io/a/b"       {io/a/b/internal}  → True  (changed child of test package)
      "io/a/b/impl"  {io/a/b}           → True  (test is child of changed package)
      "io/a/b"       {io/x/y}           → False (unrelated)
    """
    for cp in changed_pkg_paths:
        if cp == pkg_path:
            return True
        if cp.startswith(pkg_path + "/"):
            return True
        if pkg_path.startswith(cp + "/"):
            return True
    return False


def filter_by_touch_check(
    new_flaky_tests: list[dict],
    changed_pkg_paths: set[str],
    changed_java_files: set[str],
    blank_class_fqns: set[str],
    touch_check_available: bool,
) -> list[dict]:
    """Suppress false-positive alerts using a three-stage touch-check filter.

    Stage 1 — package-unrelated: PR does not touch the test's Java package (or any
      sub/parent package) → suppress. A test flaking in a completely unrelated
      package cannot be this PR's fault. Note: a YAML-only PR with no .java changes
      is a valid no-touch signal (changed_pkg_paths is empty, touch_check_available
      is True). Tests whose package cannot be parsed (empty pkg_path) are kept.

    Stage 2 — blank-class infra failure: package is touched, but the class has
      blank test_name records in the BQ baseline (class/container-level infra
      failures) AND the test file itself was not modified → suppress. Infra outage
      on an untouched test is not the PR author's responsibility.

    Stage 3: keep — the PR owns the flake.

    When touch_check_available is False (git diff failed) the filter is skipped
    entirely to avoid silently suppressing genuine new flakes.
    """
    if not touch_check_available:
        return new_flaky_tests

    kept: list[dict] = []
    for test in new_flaky_tests:
        pkg_path = test.get("packageName", "").replace(".", "/")
        class_fqn = f"{test.get('packageName', '')}.{test.get('className', '')}"
        # Use the outer class filename — inner classes (e.g. Outer$Inner) live in Outer.java
        class_file = f"{test.get('className', '').split('$')[0]}.java"

        if not pkg_path:
            kept.append(test)
            continue

        if not _package_touched(pkg_path, changed_pkg_paths):
            print(f"{PREFIX} TOUCH-CHECK suppress: {get_test_key(test)}"
                  f" (package '{pkg_path}' not in PR diff)")
            continue

        if class_fqn in blank_class_fqns and class_file not in changed_java_files:
            print(f"{PREFIX} BLANK-CLASS suppress: {get_test_key(test)}"
                  f" (class '{class_fqn}' has infra-failure records, test file not in diff)")
            continue

        kept.append(test)
    return kept


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> None:
    pr_flaky_json = os.environ.get("PR_FLAKY_TESTS_DATA", "")
    known_flaky_file = os.environ.get("KNOWN_FLAKY_TESTS_FILE", "")
    pr_number = int(os.environ.get("PR_NUMBER", "0"))
    token = os.environ.get("GITHUB_TOKEN", "")
    repository = os.environ.get("GITHUB_REPOSITORY", "")
    state_in = os.environ.get("STATE_FILE_IN", "")
    state_out = os.environ.get("STATE_FILE_OUT", "")
    ran_jobs_json = os.environ.get("RAN_JOBS_JSON", "[]")
    bypass = os.environ.get("BYPASS_LABEL_PRESENT", "false").lower() == "true"
    head_sha = os.environ.get("HEAD_SHA", "")
    base_ref = os.environ.get("BASE_REF", "main")
    base_sha_input = os.environ.get("BASE_SHA", "")
    blocking = os.environ.get("BLOCKING", "true").lower() == "true"
    repo_root = os.environ.get("REPO_ROOT", os.getcwd())
    artifact_name = f"flaky-gate-state-pr-{pr_number}"

    state = load_state(state_in, pr_number, head_sha)
    state["pr_number"] = pr_number

    # Snapshot prior method_last_modified for Q4c counter-reset detection.
    for entry in state["tests"]:
        entry["_prev_last_mod"] = entry.get("method_last_modified_sha")

    try:
        ran_parent_jobs = set(json.loads(ran_jobs_json))
    except (json.JSONDecodeError, TypeError):
        ran_parent_jobs = set()

    # -- Parse current PR flakes -------------------------------------------
    raw_pr_flaky_entries: list = []
    if pr_flaky_json and pr_flaky_json.strip() not in ("", "[]"):
        try:
            raw_pr_flaky_entries = json.loads(pr_flaky_json)
        except json.JSONDecodeError:
            print(f"{PREFIX} Failed to parse PR_FLAKY_TESTS_DATA — treating as empty.")
    pr_flaky_tests = process_flaky_tests_data(raw_pr_flaky_entries)

    # Every flake observed this run, regardless of BigQuery-baseline membership.
    # Reconciliation and counter logic must see the full set: a tracked test that
    # re-flakes is still flaking even if it later joined the org-wide baseline.
    # The baseline filter applies ONLY to deciding what becomes a brand-new entry.
    all_flaky_keys = {get_test_key(t) for t in pr_flaky_tests}

    baseline_keys, blank_class_fqns = load_baseline_keys(known_flaky_file)
    new_flaky_tests = [t for t in pr_flaky_tests
                       if not is_in_baseline(get_test_key(t), baseline_keys)]

    # Fix 5 + Fix 1: suppress tests whose package is unrelated to this PR's changes,
    # and infra-failure classes whose test file was not directly modified.
    changed_pkg_paths, changed_java_files, touch_check_available = get_pr_changed_paths(
        base_ref, head_sha, repo_root, base_sha_input
    )
    new_flaky_tests = filter_by_touch_check(
        new_flaky_tests, changed_pkg_paths, changed_java_files,
        blank_class_fqns, touch_check_available
    )

    # -- Nothing-to-do short-circuit --------------------------------------
    # No prior tracked entries, no new flakes this run, and no bypass: there is
    # nothing to reconcile and nothing to show. Return without posting a comment
    # or writing state (so no artifact is uploaded) — keeps clean PRs untouched.
    if not bypass and not new_flaky_tests and not state["tests"]:
        print(f"{PREFIX} No prior state and no flaky tests this run — nothing to do.")
        set_output("has-new-flaky-tests", "false")
        return

    # -- Bypass short-circuit ---------------------------------------------
    if bypass:
        print(f"{PREFIX} Bypass label present — clearing all active entries.")
        apply_bypass(state)
        # Drop the snapshot field before saving.
        for entry in state["tests"]:
            entry.pop("_prev_last_mod", None)
        state["last_known_head_sha"] = head_sha
        save_state(state_out, state)
        comment = render_comment(state, artifact_name)
        # Bypass is an escape hatch: entries are already cleared, so a failed
        # comment post must never fail the job (would defeat the unblock).
        _post_comment_with_handling(repository, pr_number, comment, token, blocking=False)
        set_output("has-new-flaky-tests", "false")
        return

    # -- Reconcile sticky state -------------------------------------------
    base_sha = (get_merge_base(head_sha, base_ref, repo_root, base_sha_input)
                if (base_ref or base_sha_input) else None)
    if base_sha:
        print(f"{PREFIX} merge-base({base_ref}, HEAD) = {base_sha[:12]}")
    else:
        print(f"{PREFIX} WARN: could not resolve merge-base for base_ref={base_ref!r}")

    reconcile_state(state, all_flaky_keys, head_sha, base_sha, repo_root)
    merge_new_flakes(state, new_flaky_tests, head_sha, repo_root)
    increment_counters_if_clean(state, all_flaky_keys, ran_parent_jobs, head_sha)
    apply_clearance(state)

    # Strip snapshot field before persisting.
    for entry in state["tests"]:
        entry.pop("_prev_last_mod", None)

    state["last_known_head_sha"] = head_sha
    save_state(state_out, state)

    active_count = sum(1 for e in state["tests"] if e.get("status") == "active")
    print(f"{PREFIX} active={active_count} total_tracked={len(state['tests'])}")

    comment = render_comment(state, artifact_name)
    _post_comment_with_handling(repository, pr_number, comment, token, blocking)

    set_output("has-new-flaky-tests", "true" if active_count > 0 else "false")
    if active_count > 0 and blocking:
        print(f"::error::{active_count} flaky test(s) still active in this PR. See the PR comment.")
        sys.exit(1)


def _post_comment_with_handling(repo: str, pr: int, body: str,
                                 token: str, blocking: bool) -> None:
    if not repo or "/" not in repo:
        print(f"{PREFIX} WARN: GITHUB_REPOSITORY not set — skipping comment.")
        return
    owner, name = repo.split("/", 1)
    try:
        post_or_update_comment(owner, name, pr, body, token)
    except GitHubAPIError as exc:
        msg = f"comment post/update failed: {exc}"
        if blocking:
            print(f"::error::{msg}")
            sys.exit(1)
        print(f"{PREFIX} WARN: {msg}")


if __name__ == "__main__":
    main()
