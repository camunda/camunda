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
  - timeout errors  → insert `test.slow();` as the first statement in the test
    body, which triples the default timeout.
  - other flaky     → insert a `// TODO(triage-agent): flaky – investigate`
    comment above the test so it's visible during review.

Usage:
    python3 apply-timeout-fixes.py <triage-report.json> <test-suite-dir>

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
#   group 2: the test title (single- or double-quoted, including escaped quotes)
#   group 3: everything up to and including the opening `{` of the arrow function body
_TEST_OPEN_RE = re.compile(
    r"""(test(?:\.only)?\s*\(\s*)"""  # test( or test.only(
    r"""(['"])((?:(?!\2).|\\.)*)\2"""  # 'title' or "title"
    r"""([\s\S]*?\{)""",              # , async ({...}) => {   — non-greedy up to first {
    re.MULTILINE,
)

_SLOW_ALREADY_RE = re.compile(r"\btest\.slow\(\s*\)\s*;")
_TODO_ALREADY_RE = re.compile(r"// TODO\(triage-agent\)")


def _escape_for_search(title: str) -> str:
    """Escape a test title for use as a literal substring match (no regex)."""
    return title


def _apply_slow(content: str, test_title: str) -> tuple[str, bool]:
    """Insert `test.slow();` after the opening brace of the named test."""
    modified = False

    def replacer(m: re.Match) -> str:
        nonlocal modified
        captured_title = m.group(3)
        if captured_title != test_title:
            return m.group(0)
        body_open = m.group(4)
        # Don't double-add
        remaining = content[m.end():]
        if _SLOW_ALREADY_RE.search(content[m.start():m.start() + 200]):
            return m.group(0)
        modified = True
        return m.group(1) + m.group(2) + captured_title + m.group(2) + body_open + "\n    test.slow();"

    new_content = _TEST_OPEN_RE.sub(replacer, content)
    return new_content, modified


def _apply_todo_comment(content: str, test_title: str) -> tuple[str, bool]:
    """Insert a TODO comment on the line before the named test call."""
    modified = False

    def replacer(m: re.Match) -> str:
        nonlocal modified
        if m.group(3) != test_title:
            return m.group(0)
        # Don't double-add
        start = m.start()
        preceding = content[max(0, start - 200):start]
        if _TODO_ALREADY_RE.search(preceding):
            return m.group(0)
        modified = True
        return "// TODO(triage-agent): flaky – investigate\n" + m.group(0)

    new_content = _TEST_OPEN_RE.sub(replacer, content)
    return new_content, modified


# ---------------------------------------------------------------------------
# Per-test fix dispatch
# ---------------------------------------------------------------------------

def fix_test_in_file(ts_file: Path, test_name: str, error_type: str) -> tuple[bool, str]:
    """
    Apply the appropriate fix for a single test in *ts_file*.

    Returns (was_modified, fix_description).
    The caller is responsible for writing the file back.
    """
    # Extract the leaf test title from the full "Suite > … > title" path
    leaf_title = test_name.rsplit(" > ", 1)[-1]

    content = ts_file.read_text(encoding="utf-8")

    if error_type == "timeout":
        new_content, changed = _apply_slow(content, leaf_title)
        fix_desc = "added test.slow()"
    else:
        new_content, changed = _apply_todo_comment(content, leaf_title)
        fix_desc = "added TODO comment"

    if changed:
        ts_file.write_text(new_content, encoding="utf-8")

    return changed, fix_desc


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main(report_path: Path, suite_dir: Path) -> None:
    report = json.loads(report_path.read_text())
    flaky_tests: list[dict] = report.get("flaky_tests", [])

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

        try:
            changed, fix_desc = fix_test_in_file(ts_file, test_name, error_type)
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
    if len(sys.argv) != 3:
        print(f"Usage: {sys.argv[0]} <triage-report.json> <test-suite-dir>", file=sys.stderr)
        sys.exit(1)
    main(Path(sys.argv[1]), Path(sys.argv[2]))
