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

Drop-in replacement for apply-timeout-fixes.py.  Uses the Claude API to generate
real code fixes per test:

  timing + timeout   → insert test.slow()
  fixable test_code  → rewrite the test function directly (fix the wrong assertion,
                       change parallel→serial, update the locator, etc.)
  everything else    → insert a targeted TODO comment with Claude's diagnosis

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
# Test-function extraction helpers
# ---------------------------------------------------------------------------

def _extract_test_span(content: str, test_title: str) -> "tuple[str, int, int] | None":
    """
    Locate the complete test() call in *content* and return
    (full_text, start_pos, end_pos_exclusive).

    The span runs from the leading 't' of `test(` to the closing ')' (and
    optional ';') of the outer test() call — i.e. the entire statement.
    """
    for m in _TEST_OPEN_RE.finditer(content):
        if m.group(3) != test_title:
            continue
        start = m.start()
        # m.end() is immediately after the opening '{' of the arrow-function body.
        # Walk forward counting braces to find the matching '}'.
        depth = 1
        i = m.end()
        while i < len(content) and depth > 0:
            ch = content[i]
            if ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
            i += 1
        # i is now just past the closing '}' of the arrow function.
        # Consume the closing ')' of test(...) and an optional ';'.
        while i < len(content) and content[i] in " \t\r\n":
            i += 1
        if i < len(content) and content[i] == ")":
            i += 1
        if i < len(content) and content[i] == ";":
            i += 1
        return content[start:i], start, i
    return None


# ---------------------------------------------------------------------------
# File-modification helpers
# ---------------------------------------------------------------------------

def _apply_slow(content: str, test_title: str) -> "tuple[str, bool]":
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


def _apply_todo_comment(content: str, test_title: str, detail: str) -> "tuple[str, bool]":
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


def _apply_rewrite(content: str, test_title: str, new_code: str) -> "tuple[str, bool]":
    """Replace the complete test() statement with *new_code* from Claude."""
    result = _extract_test_span(content, test_title)
    if result is None:
        return content, False
    _orig, start, end = result
    # Preserve the leading indentation of the original statement so the
    # replacement lands at the right column inside its describe block.
    line_start = content.rfind("\n", 0, start) + 1
    indent = content[line_start:start]
    # Re-indent every line of new_code to match.
    stripped = new_code.strip()
    reindented_lines = []
    for lineno, line in enumerate(stripped.splitlines()):
        if lineno == 0:
            reindented_lines.append(indent + line.lstrip())
        elif line.strip():
            reindented_lines.append(indent + line.lstrip())
        else:
            reindented_lines.append("")
    reindented = "\n".join(reindented_lines)
    return content[:line_start] + reindented + content[end:], True


# ---------------------------------------------------------------------------
# Read the complete test source for the Claude prompt
# ---------------------------------------------------------------------------

def _read_test_source(ts_file: Path, leaf_title: str) -> str:
    """Return the complete test() statement as it appears in the source file."""
    try:
        content = ts_file.read_text(encoding="utf-8")
        result = _extract_test_span(content, leaf_title)
        if result:
            return result[0]
    except Exception:
        pass
    return ""


# ---------------------------------------------------------------------------
# Claude: generate the actual fix
# ---------------------------------------------------------------------------

_FIX_SYSTEM = """\
You are a senior test engineer fixing a flaky Playwright test in the Camunda 8 suite.

You receive:
- The complete test function source code (TypeScript)
- The triage classification (why the test is failing)
- The error message

Your job: produce the best possible fix.

Fix strategies:
1. "slow"
   Add test.slow() as the very first statement in the test body.
   Use ONLY when hint is "timing" AND the error is a genuine Playwright timeout
   ("Timeout exceeded", "waiting for expect", etc.).

2. "rewrite"
   Return the complete, corrected test function — every line from test( to });.
   Use for concrete, high-confidence fixes such as:
   - Wrong assertion: e.g. toHaveLength(1) on an unconstrained array → toBeGreaterThanOrEqual(1)
   - Parallel→serial: remove Promise.all() wrapping around ordered steps
   - Outdated locator: replace deprecated selector with a working one
   - Wrong expected HTTP status: e.g. expect 200 but spec says 201
   - Hard-coded count that should be ≥ N instead of === N
   Only use "rewrite" when the fix is mechanical and you are confident the new
   code is correct.  Do NOT rewrite the test title string.  Do NOT add new imports.

3. "todo_comment"
   Insert a // TODO(triage-agent) comment above the test.
   Use when the real fix requires knowledge of product internals you cannot be
   certain about, or when the failure looks like a product regression that a
   developer needs to investigate.

Rules:
- Never use "slow" for locator errors, wrong assertions, or data isolation issues.
- When using "rewrite", return the COMPLETE test function — from test( (or test.only()
  if the original used that) all the way to the final });  Keep the original indentation
  style and TypeScript conventions.
- The "todo_detail" must explain WHAT is wrong and HOW to fix it when strategy is
  "todo_comment".  Reference the actual code or error message.

Respond with ONLY valid JSON, no markdown:
{
  "strategy": "slow" | "rewrite" | "todo_comment",
  "fixed_code": "<complete test function — only when strategy is rewrite>",
  "todo_detail": "<actionable description — only when strategy is todo_comment>"
}"""


def _claude_fix_strategy(
    client: anthropic.Anthropic,
    test_name: str,
    hint: str,
    reasons: list[str],
    error: str,
    test_source: str,
) -> "tuple[str, str, str]":
    """
    Ask Claude for the best fix.

    Returns (strategy, todo_detail, fixed_code).
      strategy:   "slow" | "rewrite" | "todo_comment"
      todo_detail: comment text (non-empty when strategy is "todo_comment")
      fixed_code:  complete rewritten test function (non-empty when strategy is "rewrite")
    """
    parts = [
        f"Test: {test_name}",
        f"Triage hint: {hint}",
        f"Reasons: {'; '.join(reasons)}",
        f"Error: {error[:400]}",
    ]
    if test_source:
        parts.append(f"Test source:\n```typescript\n{test_source[:3000]}\n```")

    user_msg = "\n\n".join(parts)

    for attempt in range(3):
        try:
            resp = client.messages.create(
                model=_MODEL,
                max_tokens=2048,
                system=_FIX_SYSTEM,
                messages=[{"role": "user", "content": user_msg}],
            )
            text = resp.content[0].text.strip()
            text = re.sub(r"^```(?:json)?\s*|\s*```$", "", text, flags=re.MULTILINE).strip()
            data = json.loads(text)
            strategy = data.get("strategy", "todo_comment")
            if strategy not in ("slow", "rewrite", "todo_comment"):
                strategy = "todo_comment"
            todo_detail = data.get("todo_detail") or ""
            fixed_code = data.get("fixed_code") or ""
            # Sanity-check "rewrite": reject if fixed_code is empty or suspiciously short
            if strategy == "rewrite" and len(fixed_code.strip()) < 30:
                strategy = "todo_comment"
                todo_detail = todo_detail or f"{hint}: rewrite returned empty code, investigate manually"
            return strategy, todo_detail, fixed_code
        except Exception as exc:
            wait = 2 ** attempt
            print(f"[warn] Claude fix call failed for '{test_name}' (attempt {attempt + 1}/3): {exc}",
                  file=sys.stderr)
            if attempt < 2:
                time.sleep(wait)

    # Deterministic fallback when all API attempts fail
    if hint == "timing" and "timeout" in error.lower():
        return "slow", "", ""
    hint_label = hint.replace("_", " ") if hint else "investigate"
    detail = f"{hint_label}: {reasons[0]}" if reasons else hint_label
    return "todo_comment", detail, ""


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
) -> "tuple[bool, str]":
    leaf_title = test_name.rsplit(" > ", 1)[-1]
    content = ts_file.read_text(encoding="utf-8")
    test_source = _read_test_source(ts_file, leaf_title)

    strategy, todo_detail, fixed_code = _claude_fix_strategy(
        client, test_name, hint, reasons, error, test_source
    )

    if strategy == "slow":
        new_content, changed = _apply_slow(content, leaf_title)
        fix_desc = "added test.slow()"
    elif strategy == "rewrite":
        new_content, changed = _apply_rewrite(content, leaf_title, fixed_code)
        fix_desc = f"rewrote test ({hint})"
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
