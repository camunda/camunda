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
Claude claude-sonnet-4-6 powered flaky-test fix generator.

Drop-in replacement for apply-timeout-fixes.py.  Uses the Claude API to decide
the best fix strategy per test, then applies the change deterministically:

  timing + timeout   → insert test.slow()
  test_code          → insert a targeted TODO comment with Claude's specific
                       diagnosis and recommended action
  product_regression → insert a TODO comment noting a product-side issue
  everything else    → insert a generic TODO comment

Using Claude here means the TODO comment contains a concrete, actionable
description rather than just the generic hint label — so reviewers know exactly
what to look at.

Usage:
    python3 claude-fix.py <triage-report.json> <test-suite-dir>
                          [--version-label <label>]

Output (JSON to stdout):
    { "fixed": [ { "file", "test", "fix_type" }, ... ],
      "skipped": [ { "file", "test", "reason" }, ... ] }

Environment:
    ANTHROPIC_API_KEY  Required.
"""

import json
import re
import sys
import time
from pathlib import Path

import anthropic

sys.path.insert(0, str(Path(__file__).parent))

# Reuse the same regex helpers from apply-timeout-fixes.py
_TEST_OPEN_RE = re.compile(
    r"""(test(?:\.only)?\s*\(\s*)"""
    r"""(['"`])((?:(?!\2).|\\.)*)\2"""
    r"""[\s\S]*?=>\s*(\{)""",
    re.MULTILINE,
)
_SLOW_ALREADY_RE = re.compile(r"\btest\.slow\(\s*\)\s*;")
_TODO_ALREADY_RE = re.compile(r"// TODO\(triage-agent\)")

_MODEL = "claude-sonnet-4-6"

# ---------------------------------------------------------------------------
# File-modification helpers (same as apply-timeout-fixes.py)
# ---------------------------------------------------------------------------

def _apply_slow(content: str, test_title: str) -> tuple[str, bool]:
    modified = False

    def replacer(m: re.Match) -> str:
        nonlocal modified
        if m.group(3) != test_title:
            return m.group(0)
        if _SLOW_ALREADY_RE.search(content[m.end():m.end() + 100]):
            return m.group(0)
        modified = True
        return m.group(0) + "\n    test.slow();"

    return _TEST_OPEN_RE.sub(replacer, content), modified


def _apply_todo_comment(content: str, test_title: str, detail: str) -> tuple[str, bool]:
    modified = False
    comment_text = f"// TODO(triage-agent): flaky – {detail}"

    def replacer(m: re.Match) -> str:
        nonlocal modified
        if m.group(3) != test_title:
            return m.group(0)
        preceding = content[max(0, m.start() - 200):m.start()]
        if _TODO_ALREADY_RE.search(preceding):
            return m.group(0)
        modified = True
        line_start = content.rfind("\n", 0, m.start()) + 1
        indent = content[line_start:m.start()]
        return indent + comment_text + "\n" + m.group(0)

    return _TEST_OPEN_RE.sub(replacer, content), modified


# ---------------------------------------------------------------------------
# Claude: decide fix strategy and generate a specific TODO message
# ---------------------------------------------------------------------------

_FIX_SYSTEM = """\
You are a senior test engineer deciding how to fix a flaky Playwright test in the Camunda 8 suite.

You receive:
- The triage classification (why the test is failing)
- The test source code
- The error message

Your job: choose the right fix strategy and provide a precise description.

Fix strategies:
1. "slow"         – Add test.slow() to triple the Playwright timeout.
                    Use ONLY when hint is "timing" AND the error is a genuine timeout
                    (e.g. "Timeout exceeded", "waiting for expect").
2. "todo_comment" – Insert a TODO comment above the test.  Use for everything else.
                    The comment must be specific and actionable, e.g.:
                    "test_code: assertion toHaveLength(1) contradicts spec — brokers is
                    an unconstrained array, use toBeGreaterThanOrEqual(1)"

Rules:
- Never use "slow" for locator errors, wrong assertions, or data isolation issues.
- The "todo_detail" must explain WHAT is wrong and HOW to fix it — not just repeat
  the hint label.  Reference the actual code or error.

Respond with ONLY valid JSON, no markdown:
{
  "strategy": "slow" | "todo_comment",
  "todo_detail": "<concise, actionable description — used as the TODO comment text>"
}"""


def _claude_fix_strategy(
    client: anthropic.Anthropic,
    test_name: str,
    hint: str,
    reasons: list[str],
    error: str,
    test_source: str,
) -> tuple[str, str]:
    """
    Ask Claude for the best fix strategy.

    Returns (strategy, todo_detail).
    strategy: "slow" | "todo_comment"
    todo_detail: text for the TODO comment (empty string when strategy is "slow")
    """
    parts = [
        f"Test: {test_name}",
        f"Triage hint: {hint}",
        f"Reasons: {'; '.join(reasons)}",
        f"Error: {error[:400]}",
    ]
    if test_source:
        parts.append(f"Test source:\n```typescript\n{test_source[:1500]}\n```")

    user_msg = "\n\n".join(parts)

    for attempt in range(3):
        try:
            resp = client.messages.create(
                model=_MODEL,
                max_tokens=256,
                system=_FIX_SYSTEM,
                messages=[{"role": "user", "content": user_msg}],
            )
            text = resp.content[0].text.strip()
            text = re.sub(r"^```(?:json)?\s*|\s*```$", "", text, flags=re.MULTILINE).strip()
            data = json.loads(text)
            strategy = data.get("strategy", "todo_comment")
            if strategy not in ("slow", "todo_comment"):
                strategy = "todo_comment"
            detail = data.get("todo_detail", hint)
            return strategy, detail
        except Exception as exc:
            wait = 2 ** attempt
            print(f"[warn] Claude fix call failed for '{test_name}' (attempt {attempt+1}/3): {exc}",
                  file=sys.stderr)
            if attempt < 2:
                time.sleep(wait)

    # Fallback: deterministic
    if hint == "timing" and "timeout" in error.lower():
        return "slow", ""
    hint_label = hint.replace("_", " ") if hint else "investigate"
    detail = f"{hint_label}: {reasons[0]}" if reasons else hint_label
    return "todo_comment", detail


def _read_test_source(ts_file: Path, leaf_title: str) -> str:
    """Return the test function body for display in the Claude prompt."""
    try:
        from spec_validator import _find_test_body
        content = ts_file.read_text(encoding="utf-8")
        body = _find_test_body(content, leaf_title)
        if body:
            return f"test('{leaf_title}', async ({{...}}) => {{{body}}})"
    except Exception:
        pass
    return ""


# ---------------------------------------------------------------------------
# Per-test fix dispatch
# ---------------------------------------------------------------------------

def fix_test_in_file(
    client: anthropic.Anthropic,
    ts_file: Path,
    test_name: str,
    hint: str,
    reasons: list[str],
    error: str,
) -> tuple[bool, str]:
    leaf_title = test_name.rsplit(" > ", 1)[-1]
    content = ts_file.read_text(encoding="utf-8")
    test_source = _read_test_source(ts_file, leaf_title)

    strategy, todo_detail = _claude_fix_strategy(
        client, test_name, hint, reasons, error, test_source
    )

    if strategy == "slow":
        new_content, changed = _apply_slow(content, leaf_title)
        fix_desc = "added test.slow()"
    else:
        new_content, changed = _apply_todo_comment(content, leaf_title, todo_detail)
        fix_desc = f"added TODO comment ({hint})"

    if changed:
        ts_file.write_text(new_content, encoding="utf-8")

    return changed, fix_desc


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main(report_path: Path, suite_dir: Path, version_label: "str | None" = None) -> None:
    client = anthropic.Anthropic()

    report = json.loads(report_path.read_text())
    all_flaky: list[dict] = report.get("flaky_tests", [])

    if version_label:
        flaky_tests = [t for t in all_flaky if version_label in t.get("versions", [])]
    else:
        flaky_tests = all_flaky

    fixed: list[dict] = []
    skipped: list[dict] = []

    for test in flaky_tests:
        rel_file  = test.get("file", "")
        test_name = test.get("name", "")
        hint      = test.get("root_cause_hint", "needs_investigation")
        reasons   = test.get("root_cause_reasons", [])
        error     = test.get("error", "")

        if not rel_file or not test_name:
            skipped.append({"file": rel_file, "test": test_name, "reason": "missing file or name"})
            continue

        ts_file = suite_dir / rel_file
        if not ts_file.exists():
            matches = list(suite_dir.rglob(Path(rel_file).name))
            if len(matches) == 1:
                ts_file = matches[0]
            else:
                skipped.append({"file": rel_file, "test": test_name,
                                 "reason": f"file not found ({len(matches)} glob matches)"})
                continue

        try:
            changed, fix_desc = fix_test_in_file(
                client, ts_file, test_name, hint, reasons, error
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
    version_label: "str | None" = None

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
