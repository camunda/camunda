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
Claude claude-sonnet-4-6 powered test triage for Playwright failures.

Drop-in replacement for analyze-playwright-results.py that uses the Claude API
instead of keyword heuristics.  Produces the same triage-report.json format.

Usage:
    python3 claude-triage.py <artifacts_dir>
                             [--spec <openapi-spec.yaml>]
                             [--suite-dir <test-suite-dir>]

Environment:
    ANTHROPIC_API_KEY  Required.  API key for the Anthropic API.

Falls back to deterministic heuristics for any failure where the Claude call
fails, so a transient API error never aborts the whole triage run.
"""

import json
import re
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path

import anthropic

# ---------------------------------------------------------------------------
# Sibling-module imports (spec_validator lives in the same directory)
# ---------------------------------------------------------------------------
sys.path.insert(0, str(Path(__file__).parent))
try:
    from spec_validator import (
        SpecValidator,
        extract_api_calls,
        extract_status_from_error,
        extract_asserted_length,
    )
    _SPEC_AVAILABLE = True
except ImportError:
    _SPEC_AVAILABLE = False

# ---------------------------------------------------------------------------
# Model
# ---------------------------------------------------------------------------
_MODEL = "claude-sonnet-4-6"

# ---------------------------------------------------------------------------
# Data model (same as analyze-playwright-results.py)
# ---------------------------------------------------------------------------

@dataclass
class TestFailure:
    name: str
    file: str
    retry_outcome: str  # "flaky" | "hard_fail"
    error: str
    error_type: str
    versions: list[str] = field(default_factory=list)


# ---------------------------------------------------------------------------
# Error-type classifier (kept for context injection into the Claude prompt)
# ---------------------------------------------------------------------------

_ERROR_PATTERNS: list[tuple[str, list[str]]] = [
    ("timeout",    ["timeout", "timed out", "time out", "exceeded the timeout",
                    "waiting for expect", "exceeded time limit"]),
    ("locator",    ["strict mode violation", "element is not attached", "detached from dom",
                    "element is outside the viewport", "element is not visible",
                    "element handle is not valid", "no element found",
                    "unable to find element", "not found", "selector", "resolved to"]),
    ("data",       ["duplicate key", "unique constraint", "already exists",
                    "overlapping", "conflict", "foreign key", "integrity constraint"]),
    ("http_error", ["status 400", "status 401", "status 403", "status 404",
                    "status 409", "status 422", "status 500", "status 502", "status 503",
                    "expected status", "response status", "http error",
                    "request failed with status"]),
    ("network",    ["networkerror", "net::", "econnrefused", "fetch failed",
                    "econnreset", "socket hang up", "connection refused"]),
    ("assertion",  ["expect(", "toequal", "tobetruthy", "tocontain", "tohavetextcontent",
                    "tohavevalue", "tobevisible", "tobehidden", "assert",
                    "expected", "received"]),
]


def _classify_error(message: str) -> str:
    msg = message.lower()
    for label, keywords in _ERROR_PATTERNS:
        if any(k in msg for k in keywords):
            return label
    return "other"


# ---------------------------------------------------------------------------
# Playwright JSON parsing helpers
# ---------------------------------------------------------------------------

def _first_error(results: list[dict]) -> str:
    for r in results:
        for err in r.get("errors", []):
            msg = err.get("message", "").strip()
            if msg:
                return msg[:600]
    return ""


def _walk_suites(suites: list[dict], parent_title: str = "") -> list[dict]:
    specs = []
    for suite in suites:
        title = suite.get("title", "")
        full_title = f"{parent_title} > {title}".strip(" >") if parent_title else title
        for spec in suite.get("specs", []):
            specs.append({
                "title": f"{full_title} > {spec['title']}".strip(" >"),
                "file":  spec.get("file", suite.get("file", "")),
                "tests": spec.get("tests", []),
            })
        specs.extend(_walk_suites(suite.get("suites", []), full_title))
    return specs


def extract_failures(report: dict, label: str) -> list[TestFailure]:
    failures = []
    for spec in _walk_suites(report.get("suites", [])):
        for test in spec["tests"]:
            status = test.get("status", "")
            if status not in ("unexpected", "flaky"):
                continue
            raw_error = _first_error(test.get("results", []))
            failures.append(TestFailure(
                name=spec["title"],
                file=spec["file"],
                retry_outcome="flaky" if status == "flaky" else "hard_fail",
                error=raw_error,
                error_type=_classify_error(raw_error),
                versions=[label],
            ))
    return failures


def merge_failures(all_by_label: dict[str, list[TestFailure]]) -> dict[str, TestFailure]:
    merged: dict[str, TestFailure] = {}
    for label, failures in all_by_label.items():
        for f in failures:
            if f.name in merged:
                existing = merged[f.name]
                if label not in existing.versions:
                    existing.versions.append(label)
                if f.retry_outcome == "hard_fail":
                    existing.retry_outcome = "hard_fail"
                if not existing.error and f.error:
                    existing.error = f.error
                    existing.error_type = f.error_type
            else:
                merged[f.name] = f
    return merged


# ---------------------------------------------------------------------------
# Spec context builder
# ---------------------------------------------------------------------------

def _build_spec_context(
    failure: TestFailure,
    suite_dir: Path,
    validator: "SpecValidator",
) -> str:
    """Return a compact YAML-ish excerpt of the spec relevant to this failure."""
    if not _SPEC_AVAILABLE:
        return ""
    leaf_title = failure.name.rsplit(" > ", 1)[-1]
    test_file = suite_dir / failure.file
    api_info = extract_api_calls(test_file, leaf_title)
    if not api_info.get("endpoints"):
        return ""
    endpoint = api_info["endpoints"][0]
    method = (api_info["methods"] or ["get"])[0]
    op = validator.get_operation(endpoint, method)
    if not op:
        return ""
    valid_codes = list(validator.get_valid_status_codes(endpoint, method))
    schema = validator.get_response_schema(endpoint, method, "200")
    props_summary = ""
    if isinstance(schema, dict):
        prop_lines = []
        for name, pschema in schema.get("properties", {}).items():
            resolved = validator._resolve_schema(pschema)
            ptype = resolved.get("type", "?")
            constraints = []
            if ptype == "array":
                if "minItems" in resolved:
                    constraints.append(f"minItems: {resolved['minItems']}")
                if "maxItems" in resolved:
                    constraints.append(f"maxItems: {resolved['maxItems']}")
                if not constraints:
                    constraints.append("unconstrained length")
            prop_lines.append(
                f"  {name}: {ptype}" + (f" ({', '.join(constraints)})" if constraints else "")
            )
        props_summary = "\n".join(prop_lines[:20])  # cap to avoid huge prompts

    lines = [
        f"Endpoint: {method.upper()} {endpoint}",
        f"Defined response codes: {', '.join(sorted(valid_codes))}",
    ]
    if props_summary:
        lines.append(f"200 response properties:\n{props_summary}")
    return "\n".join(lines)


def _read_test_function(failure: TestFailure, suite_dir: Path) -> str:
    """Return just the relevant test function body from the source file."""
    if not _SPEC_AVAILABLE:
        return ""
    leaf_title = failure.name.rsplit(" > ", 1)[-1]
    test_file = suite_dir / failure.file
    try:
        from spec_validator import _find_test_body
        content = test_file.read_text(encoding="utf-8")
        body = _find_test_body(content, leaf_title)
        if body:
            return f"test('{leaf_title}', async ({{request, page}}) => {{{body}}})"
    except Exception:
        pass
    return ""


# ---------------------------------------------------------------------------
# Claude API call
# ---------------------------------------------------------------------------

_SYSTEM_PROMPT = """\
You are a senior test engineer triaging Playwright test failures for the Camunda 8 platform.

Classify each failure into exactly ONE of:
- "test_code"           – the test implementation is wrong: outdated selector, bad assertion,
                          test-data isolation issue, assertion that contradicts the API spec
- "product_regression"  – the product regressed: HTTP 4xx/5xx not in the spec, response body
                          violates the contract, feature broken on exactly one branch
- "timing"              – race condition / flakiness: intermittent timeout, passed on retry
                          with no other strong signal, infrastructure instability
- "needs_investigation" – genuinely ambiguous: conflicting signals, unusual error, need logs

Key rules:
1. If the same test fails identically on ALL tested versions → almost certainly test_code
   (a product regression would typically break only one branch)
2. If the test passed on retry → consider timing, unless there is a stronger structural signal
3. For assertion errors: if the assertion contradicts the API spec (e.g. asserting exact length
   on an array the spec leaves unconstrained) → test_code
4. For HTTP errors: if the spec defines the expected status as valid but the product returned
   a different one → product_regression
5. Be concise and specific in your reasons — cite the actual error text, the test code, or the
   spec to justify your conclusion

Respond with ONLY valid JSON, no markdown:
{
  "hint": "<test_code|product_regression|timing|needs_investigation>",
  "reasons": ["<specific reason 1>", "<specific reason 2>"]
}"""


def _claude_classify(
    client: anthropic.Anthropic,
    failure: TestFailure,
    total_versions: int,
    test_code: str,
    spec_context: str,
) -> tuple[str, list[str], bool]:
    """
    Ask Claude to classify the failure.

    Returns (hint, reasons, used_claude).
    used_claude is False when we fell back to a deterministic heuristic.
    """
    parts = [
        f"Test name: {failure.name}",
        f"Test file: {failure.file}",
        f"Versions affected: {', '.join(failure.versions)} ({len(failure.versions)} of {total_versions} branch(es))",
        f"Retry outcome: {failure.retry_outcome}",
        f"Error type (pre-classified): {failure.error_type}",
        f"Error message:\n{failure.error}",
    ]
    if test_code:
        parts.append(f"Test source:\n```typescript\n{test_code}\n```")
    if spec_context:
        parts.append(f"API spec context:\n{spec_context}")

    user_message = "\n\n".join(parts)

    for attempt in range(3):
        try:
            response = client.messages.create(
                model=_MODEL,
                max_tokens=512,
                system=_SYSTEM_PROMPT,
                messages=[{"role": "user", "content": user_message}],
            )
            text = response.content[0].text.strip()
            # Strip markdown code fences if Claude adds them
            text = re.sub(r"^```(?:json)?\s*|\s*```$", "", text, flags=re.MULTILINE).strip()
            data = json.loads(text)
            hint = data.get("hint", "needs_investigation")
            reasons = data.get("reasons", [])
            if hint not in ("test_code", "product_regression", "timing", "needs_investigation"):
                hint = "needs_investigation"
            return hint, reasons, True
        except Exception as exc:
            wait = 2 ** attempt
            print(f"[warn] Claude call failed for '{failure.name}' (attempt {attempt+1}/3): {exc}",
                  file=sys.stderr)
            if attempt < 2:
                time.sleep(wait)

    # Fallback: simple heuristic
    return _heuristic_classify(failure, total_versions)


def _heuristic_classify(failure: TestFailure, total_versions: int) -> tuple[str, list[str], bool]:
    """Deterministic fallback when the Claude API is unavailable."""
    reasons: list[str] = []
    et, retry = failure.error_type, failure.retry_outcome
    n = len(failure.versions)

    if et == "locator":
        reasons.append("locator/selector error — typically an outdated test selector")
    elif et == "data":
        reasons.append("data conflict — non-isolated test data")
    elif et == "http_error":
        reasons.append("HTTP error from the product API")
    elif et == "timeout":
        reasons.append("timeout — slow product response or missing await")
    elif et == "assertion":
        reasons.append("assertion mismatch — expected vs actual value differ")

    if retry == "flaky":
        reasons.append("passed on retry — intermittent")
    else:
        reasons.append("failed on all retries — consistent failure")

    if n == total_versions and total_versions > 1:
        reasons.append(f"fails on all {n} tested versions — unlikely a version-specific regression")
        return "test_code", reasons, False
    if et in ("locator", "data"):
        return "test_code", reasons, False
    if et == "http_error" and n == 1:
        return "product_regression", reasons, False
    if et == "assertion" and n == 1 and retry == "hard_fail":
        return "product_regression", reasons, False
    if et in ("timeout", "network") or retry == "flaky":
        return "timing", reasons, False
    return "needs_investigation", reasons, False


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main(
    artifacts_dir: Path,
    spec_path: "Path | None" = None,
    suite_dir: "Path | None" = None,
) -> None:
    client = anthropic.Anthropic()  # reads ANTHROPIC_API_KEY from env

    validator = None
    if spec_path and suite_dir and _SPEC_AVAILABLE:
        try:
            validator = SpecValidator(spec_path)
            print(f"[info] spec validation enabled from {spec_path}", file=sys.stderr)
        except Exception as exc:
            print(f"[warn] could not load spec: {exc}", file=sys.stderr)

    all_by_label: dict[str, list[TestFailure]] = {}
    run_stats: dict[str, dict] = {}

    for json_file in sorted(artifacts_dir.glob("*/results.json")):
        label = json_file.parent.name
        try:
            report = json.load(json_file.open())
        except Exception as exc:
            print(f"[warn] skipping {json_file}: {exc}", file=sys.stderr)
            continue
        stats = report.get("stats", {})
        run_stats[label] = {
            "expected":   stats.get("expected", 0),
            "unexpected": stats.get("unexpected", 0),
            "flaky":      stats.get("flaky", 0),
            "skipped":    stats.get("skipped", 0),
        }
        all_by_label[label] = extract_failures(report, label)

    if not all_by_label:
        print(json.dumps({"error": "no results.json files found", "run_stats": {}}))
        return

    merged = merge_failures(all_by_label)
    total_versions = len(all_by_label)

    by_hint: dict[str, list[dict]] = {
        "test_code": [], "product_regression": [], "timing": [], "needs_investigation": []
    }
    all_failures = []

    for f in sorted(merged.values(), key=lambda x: x.name):
        test_code_str = ""
        spec_context = ""
        if suite_dir:
            test_code_str = _read_test_function(f, suite_dir)
        if validator and suite_dir:
            try:
                spec_context = _build_spec_context(f, suite_dir, validator)
            except Exception:
                pass

        hint, reasons, used_claude = _claude_classify(
            client, f, total_versions, test_code_str, spec_context
        )

        entry = {
            "name":               f.name,
            "file":               f.file,
            "versions":           f.versions,
            "error":              f.error,
            "error_type":         f.error_type,
            "retry_outcome":      f.retry_outcome,
            "root_cause_hint":    hint,
            "root_cause_reasons": reasons,
            "spec_validated":     bool(spec_context),
            "classified_by":      "claude" if used_claude else "heuristic",
        }
        all_failures.append(entry)
        by_hint[hint].append(entry)

    result = {
        "summary": {
            "total_unique_failures": len(merged),
            "test_code":             len(by_hint["test_code"]),
            "product_regression":    len(by_hint["product_regression"]),
            "timing":                len(by_hint["timing"]),
            "needs_investigation":   len(by_hint["needs_investigation"]),
        },
        "failures":     all_failures,
        "flaky_tests":  [e for e in all_failures if e["retry_outcome"] == "flaky"],
        "product_bugs": [e for e in all_failures if e["root_cause_hint"] == "product_regression"],
        "run_stats":    run_stats,
    }
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    args = sys.argv[1:]
    spec_path: "Path | None" = None
    suite_dir: "Path | None" = None

    if "--spec" in args:
        idx = args.index("--spec")
        if idx + 1 >= len(args):
            print("ERROR: --spec requires a value", file=sys.stderr)
            sys.exit(1)
        spec_path = Path(args[idx + 1])
        args = args[:idx] + args[idx + 2:]

    if "--suite-dir" in args:
        idx = args.index("--suite-dir")
        if idx + 1 >= len(args):
            print("ERROR: --suite-dir requires a value", file=sys.stderr)
            sys.exit(1)
        suite_dir = Path(args[idx + 1])
        args = args[:idx] + args[idx + 2:]

    if len(args) != 1:
        print(
            f"Usage: {sys.argv[0]} <artifacts_dir> [--spec <openapi.yaml>] [--suite-dir <dir>]",
            file=sys.stderr,
        )
        sys.exit(1)

    main(Path(args[0]), spec_path, suite_dir)
