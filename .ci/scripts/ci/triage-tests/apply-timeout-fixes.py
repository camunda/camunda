#!/usr/bin/env python3
# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
# one or more contributor license agreements. See the NOTICE file distributed
# with this work for additional information regarding copyright ownership.
# Camunda licenses this file to you under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with the License.
# You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed
# under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
# CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
# language governing permissions and limitations under the License.

"""
Apply deterministic fixes to flaky Playwright tests.

Strategies applied:
  - root_cause_hint == "timing" AND error_type == "timeout"
      → insert `test.slow();` as the first statement in the test body.
        This triples the default timeout and is only appropriate when the
        triage report is confident the failure is a genuine timing / race
        condition, not a wrong assertion or test-isolation bug.
  - all other flaky tests
      → insert a `// TODO(triage-agent): flaky – <hint>: <reason>` comment
        above the test so the human reviewer sees the classification and
        reasons from the triage report without any code being changed.
        This avoids masking real test-code or product issues with a timeout
        increase that wouldn't fix the underlying problem.

Usage:
    python3 apply-timeout-fixes.py <triage-report.json> <test-suite-dir> [--version-label <label>]

Arguments:
    triage-report.json  Output of analyze-playwright-results.py
    test-suite-dir      Root of the Playwright test suite
    --version-label     When supplied, only fix tests whose `versions` list
                        contains this label (e.g. "api-stable-8.9").  Useful
                        when applying fixes on a specific stable branch so
                        that tests that only fail on main are not touched.

Output (JSON to stdout):
    { "fixed": [ { "file", "test", "fix_type" }, ... ],
      "skipped": [ { "file", "test", "reason" }, ... ] }

Exit codes:
    0 – completed (fixes may or may not have been applied)
    1 – usage error
"""

import json
import re
import sys
from pathlib import Path


# ---------------------------------------------------------------------------
# Regex helpers
# ---------------------------------------------------------------------------

# Matches the opening of a Playwright test call, capturing:
#   group 1: test( or test.only(
#   group 2: quote character around the title
#   group 3: the test title
#   group 4: the `{` that opens the arrow-function body
#
# Anchoring on `=>\s*{` rather than the first `{` avoids matching the `{`
# inside parameter destructuring (e.g. `async ({request, page}) => {`).
_TEST_OPEN_RE = re.compile(
    r"""(test(?:\.only)?\s*\(\s*)"""    # test( or test.only(
    r"""(['"`])((?:(?!\2).|\\.)*)\2"""  # 'title', "title", or `title`
    r"""[\s\S]*?=>\s*(\{)""",          # => {   (arrow function body opening)
    re.MULTILINE,
)

_SLOW_ALREADY_RE = re.compile(r"\btest\.slow\(\s*\)\s*;")
_TODO_ALREADY_RE = re.compile(r"// TODO\(triage-agent\)")


def _apply_slow(content: str, test_title: str) -> tuple[str, bool]:
    """Insert `test.slow();` as the first statement in the named test body."""
    modified = False

    def replacer(m: re.Match) -> str:
        nonlocal modified
        if m.group(3) != test_title:
            return m.group(0)
        # Don't double-add — check the 100 chars immediately after the opening {
        if _SLOW_ALREADY_RE.search(content[m.end():m.end() + 100]):
            return m.group(0)
        modified = True
        # m.group(0) ends with the opening { — append the new statement after it
        return m.group(0) + "\n    test.slow();"

    new_content = _TEST_OPEN_RE.sub(replacer, content)
    return new_content, modified


def _apply_todo_comment(content: str, test_title: str, detail: str = "investigate") -> tuple[str, bool]:
    """Insert a TODO comment on the line before the named test call."""
    modified = False
    comment_text = f"// TODO(triage-agent): flaky – {detail}"

    def replacer(m: re.Match) -> str:
        nonlocal modified
        if m.group(3) != test_title:
            return m.group(0)
        # Don't double-add — check the 200 chars before the match
        preceding = content[max(0, m.start() - 200):m.start()]
        if _TODO_ALREADY_RE.search(preceding):
            return m.group(0)
        modified = True
        # Preserve the leading whitespace of the test() call so the comment
        # matches the indentation of the surrounding describe block.
        line_start = content.rfind("\n", 0, m.start()) + 1
        indent = content[line_start:m.start()]
        return indent + comment_text + "\n" + m.group(0)

    new_content = _TEST_OPEN_RE.sub(replacer, content)
    return new_content, modified


# ---------------------------------------------------------------------------
# Per-test fix dispatch
# ---------------------------------------------------------------------------

def fix_test_in_file(
    ts_file: Path,
    test_name: str,
    error_type: str,
    root_cause_hint: str,
    root_cause_reasons: list[str],
) -> tuple[bool, str]:
    """
    Apply the appropriate fix for a single test in *ts_file*.

    Returns (was_modified, fix_description).
    The caller is responsible for writing the file back.

    Decision logic
    --------------
    test.slow() is inserted ONLY when the triage report agrees the failure is a
    genuine timing / race-condition issue (root_cause_hint == "timing") AND the
    error itself is a timeout.  For every other category — wrong assertion,
    data isolation, product regression, or ambiguous signals — we insert an
    informative TODO comment instead, because test.slow() would mask the real
    problem without fixing it.
    """
    leaf_title = test_name.rsplit(" > ", 1)[-1]
    content = ts_file.read_text(encoding="utf-8")

    if error_type == "timeout" and root_cause_hint == "timing":
        new_content, changed = _apply_slow(content, leaf_title)
        fix_desc = "added test.slow()"
    else:
        # Build a concise detail string from the triage reasons so reviewers
        # know exactly why the test was flagged (e.g. "test_code: locator/
        # selector error — typically an outdated test selector").
        hint_label = root_cause_hint.replace("_", " ") if root_cause_hint else "investigate"
        if root_cause_reasons:
            detail = f"{hint_label}: {root_cause_reasons[0]}"
        else:
            detail = hint_label
        new_content, changed = _apply_todo_comment(content, leaf_title, detail)
        fix_desc = f"added TODO comment ({hint_label})"

    if changed:
        ts_file.write_text(new_content, encoding="utf-8")

    return changed, fix_desc


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main(report_path: Path, suite_dir: Path, version_label: str | None = None) -> None:
    report = json.loads(report_path.read_text())
    all_flaky: list[dict] = report.get("flaky_tests", [])

    # When a version label is given, only process tests that actually appeared
    # in runs labelled for that version.  This allows the workflow to target
    # stable branches: a test flaky only on stable/8.9 won't be touched when
    # fixing main, and vice-versa.
    if version_label:
        flaky_tests = [t for t in all_flaky if version_label in t.get("versions", [])]
    else:
        flaky_tests = all_flaky

    fixed: list[dict] = []
    skipped: list[dict] = []

    for test in flaky_tests:
        rel_file = test.get("file", "")
        test_name = test.get("name", "")
        error_type = test.get("error_type", "other")

        if not rel_file or not test_name:
            skipped.append({"file": rel_file, "test": test_name, "reason": "missing file or name"})
            continue

        # Resolve the file path relative to the test suite directory
        ts_file = suite_dir / rel_file
        if not ts_file.exists():
            # Try a glob search by filename in case path differs
            matches = list(suite_dir.rglob(Path(rel_file).name))
            if len(matches) == 1:
                ts_file = matches[0]
            else:
                skipped.append({"file": rel_file, "test": test_name,
                                 "reason": f"file not found ({len(matches)} glob matches)"})
                continue

        root_cause_hint = test.get("root_cause_hint", "")
        root_cause_reasons = test.get("root_cause_reasons", [])

        try:
            changed, fix_desc = fix_test_in_file(
                ts_file, test_name, error_type, root_cause_hint, root_cause_reasons
            )
        except Exception as exc:
            skipped.append({"file": rel_file, "test": test_name, "reason": str(exc)})
            continue

        if changed:
            fixed.append({"file": str(ts_file.relative_to(suite_dir)),
                           "test": test_name, "fix_type": fix_desc})
        else:
            skipped.append({"file": rel_file, "test": test_name,
                             "reason": "pattern not found or fix already present"})

    print(json.dumps({"fixed": fixed, "skipped": skipped}, indent=2))


if __name__ == "__main__":
    args = sys.argv[1:]
    version_label: str | None = None

    if "--version-label" in args:
        idx = args.index("--version-label")
        if idx + 1 >= len(args):
            print("ERROR: --version-label requires a value", file=sys.stderr)
            sys.exit(1)
        version_label = args[idx + 1]
        args = args[:idx] + args[idx + 2:]

    if len(args) != 2:
        print(
            f"Usage: {sys.argv[0]} <triage-report.json> <test-suite-dir> [--version-label <label>]",
            file=sys.stderr,
        )
        sys.exit(1)

    main(Path(args[0]), Path(args[1]), version_label)
