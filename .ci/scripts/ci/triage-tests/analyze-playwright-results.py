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
Analyze Playwright JSON test results from multiple nightly runs to triage failures.

Usage:
    python3 analyze-playwright-results.py <artifacts_dir>

The artifacts directory must contain subdirectories named after the run label
(e.g., "api-stable-8.7", "e2e-stable-8.7-v1"), each holding a results.json file.

Classification
--------------
A test failure is assessed across three independent signals:

1. Retry outcome (from Playwright's own test.status field):
   - "flaky"      — failed on attempt 1, passed on a later retry
   - "unexpected" — failed on every attempt

2. Error message pattern — what kind of error was thrown:
   - locator      : selector/locator not found, strict-mode violation, element detached
   - timeout      : operation exceeded time limit (could be test OR product)
   - data         : duplicate key, unique constraint, overlapping test data
   - assertion    : expect() mismatch — value the test asserts doesn't match reality
   - http_error   : HTTP 4xx/5xx responses from the product API
   - network      : connection refused / network-level errors
   - other

3. Version spread — how many distinct branches show the same failure:
   - single version  → more likely a product regression on that branch
   - multiple versions → more likely a test code problem (shared across branches)

root_cause_hint values
-----------------------
test_code           High confidence the test implementation is wrong:
                      outdated locator, data isolation issue, OR
                      same failure present on ALL tested versions (can't be a
                      regression if it fails everywhere the same way).

product_regression  High confidence the product regressed:
                      HTTP error / assertion failure on exactly ONE version.

timing              Timeout or race-condition — could be either side.
                    Requires human judgement.

needs_investigation Signals conflict or are insufficient to decide.

Output (JSON to stdout):
    {
      "summary": {
        "total_unique_failures": N,
        "test_code": N, "product_regression": N, "timing": N,
        "needs_investigation": N
      },
      "failures": [
        {
          "name", "file", "versions", "error", "error_type",
          "retry_outcome",        # "flaky" | "hard_fail"
          "root_cause_hint",      # see above
          "root_cause_reasons"    # list of human-readable strings explaining the hint
        }, ...
      ],
      "run_stats": { "<label>": { "expected", "unexpected", "flaky", "skipped" } }
    }
"""

import json
import sys
from dataclasses import dataclass, field
from pathlib import Path


# ---------------------------------------------------------------------------
# Data model
# ---------------------------------------------------------------------------

@dataclass
class TestFailure:
    name: str           # spec title (full path through describe blocks)
    file: str           # source file path relative to test root
    retry_outcome: str  # "flaky" | "hard_fail"
    error: str          # first error message, truncated
    error_type: str     # see _classify_error()
    versions: list[str] = field(default_factory=list)


# ---------------------------------------------------------------------------
# Error pattern classification
# ---------------------------------------------------------------------------

# Each entry: (error_type_label, list_of_keyword_fragments)
# Checked in order — first match wins.
_ERROR_PATTERNS: list[tuple[str, list[str]]] = [
    ("locator", [
        "locator", "selector", "strict mode violation",
        "element is not attached", "detached from dom",
        "element is outside the viewport", "element is not visible",
        "element handle is not valid", "no element found",
        "unable to find element",
    ]),
    ("data", [
        "duplicate key", "unique constraint", "already exists",
        "overlapping", "conflict", "foreign key", "integrity constraint",
    ]),
    ("http_error", [
        "status 400", "status 401", "status 403", "status 404",
        "status 409", "status 422", "status 500", "status 502",
        "status 503", "expected status", "response status",
        "http error", "request failed with status",
    ]),
    ("timeout", [
        "timeout", "timed out", "time out", "exceeded the timeout",
        "waiting for expect", "exceeded time limit",
    ]),
    ("network", [
        "networkerror", "net::", "econnrefused", "fetch failed",
        "econnreset", "socket hang up", "connection refused",
    ]),
    ("assertion", [
        "expect(", "toequal", "tobetruthy", "tocontain", "tohavetextcontent",
        "tohavevalue", "tobevisible", "tobehidden", "assert",
        "expected", "received",
    ]),
]


def _classify_error(message: str) -> str:
    msg = message.lower()
    for label, keywords in _ERROR_PATTERNS:
        if any(k in msg for k in keywords):
            return label
    return "other"


# ---------------------------------------------------------------------------
# Root-cause heuristic
# ---------------------------------------------------------------------------

def _root_cause(failure: "TestFailure", total_versions: int) -> tuple[str, list[str]]:
    """
    Return (hint, reasons) based on the three signals.

    hint values: "test_code" | "product_regression" | "timing" | "needs_investigation"
    """
    reasons: list[str] = []
    et = failure.error_type
    retry = failure.retry_outcome
    n_versions = len(failure.versions)

    # ── Signal 1: error type ──────────────────────────────────────────────
    if et == "locator":
        reasons.append("locator/selector error — typically an outdated test selector")
    elif et == "data":
        reasons.append("data conflict error — likely overlapping or non-isolated test data")
    elif et == "http_error":
        reasons.append("HTTP error from the product API")
    elif et == "timeout":
        reasons.append("timeout — could be slow product response or missing await in test")
    elif et == "network":
        reasons.append("network-level error — infrastructure or service startup issue")
    elif et == "assertion":
        reasons.append("assertion mismatch — expected vs actual value differ")

    # ── Signal 2: retry outcome ───────────────────────────────────────────
    if retry == "flaky":
        reasons.append("passed on retry — intermittent, not a consistent product failure")
    else:
        reasons.append("failed on all retries — consistent failure")

    # ── Signal 3: version spread ──────────────────────────────────────────
    if n_versions == total_versions and total_versions > 1:
        reasons.append(
            f"fails on all {n_versions} tested versions — unlikely to be a version-specific regression"
        )
    elif n_versions == 1:
        reasons.append("fails on exactly one version — consistent with a version-specific regression")
    else:
        reasons.append(f"fails on {n_versions}/{total_versions} versions")

    # ── Combine into a hint ───────────────────────────────────────────────

    # Strong test-code signals regardless of retry outcome
    if et in ("locator", "data"):
        return "test_code", reasons

    # Fails identically on every tested version → almost certainly not a product regression
    if n_versions == total_versions and total_versions > 1:
        return "test_code", reasons

    # HTTP error on a single version → product regression
    if et == "http_error" and n_versions == 1:
        return "product_regression", reasons

    # Assertion failure on a single version → product regression
    if et == "assertion" and n_versions == 1 and retry == "hard_fail":
        return "product_regression", reasons

    # Timeout regardless of retry or version spread → timing, needs investigation
    if et == "timeout":
        return "timing", reasons

    # Network errors are infrastructure, not product or test code
    if et == "network":
        return "timing", reasons

    # Passed on retry, not already classified → timing / intermittent test issue
    if retry == "flaky":
        return "timing", reasons

    return "needs_investigation", reasons


# ---------------------------------------------------------------------------
# Parsing helpers
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


# ---------------------------------------------------------------------------
# Core analysis
# ---------------------------------------------------------------------------

def extract_failures(report: dict, label: str) -> list[TestFailure]:
    failures = []
    for spec in _walk_suites(report.get("suites", [])):
        for test in spec["tests"]:
            playwright_status = test.get("status", "")
            if playwright_status not in ("unexpected", "flaky"):
                continue
            raw_error = _first_error(test.get("results", []))
            failures.append(TestFailure(
                name=spec["title"],
                file=spec["file"],
                retry_outcome="flaky" if playwright_status == "flaky" else "hard_fail",
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
                # A hard fail on any version overrides a flaky classification
                if f.retry_outcome == "hard_fail":
                    existing.retry_outcome = "hard_fail"
                if not existing.error and f.error:
                    existing.error = f.error
                    existing.error_type = f.error_type
            else:
                merged[f.name] = f
    return merged


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main(artifacts_dir: Path) -> None:
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
        hint, reasons = _root_cause(f, total_versions)
        entry = {
            "name":               f.name,
            "file":               f.file,
            "versions":           f.versions,
            "error":              f.error,
            "error_type":         f.error_type,
            "retry_outcome":      f.retry_outcome,
            "root_cause_hint":    hint,
            "root_cause_reasons": reasons,
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
        "failures":  all_failures,
        # Backward-compat keys consumed by the fix script and workflow
        "flaky_tests":  [e for e in all_failures if e["retry_outcome"] == "flaky"],
        "product_bugs": [e for e in all_failures if e["root_cause_hint"] == "product_regression"],
        "run_stats":    run_stats,
    }

    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(f"Usage: {sys.argv[0]} <artifacts_dir>", file=sys.stderr)
        sys.exit(1)
    main(Path(sys.argv[1]))
