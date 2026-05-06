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

Output (JSON to stdout):
    {
      "summary": { "total_unique_failures": N, "flaky": N, "product_bugs": N,
                   "cross_version": N },
      "flaky_tests":    [ { "name", "file", "versions", "error", "type" }, ... ],
      "product_bugs":   [ { "name", "file", "versions", "error", "type" }, ... ],
      "run_stats":      { "<label>": { "expected", "unexpected", "flaky", "skipped" } }
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
    name: str        # spec title (full path through describe blocks)
    file: str        # source file path relative to test root
    status: str      # "flaky" | "unexpected"
    error: str       # first error message, truncated
    error_type: str  # "timeout" | "assertion" | "network" | "other"
    versions: list[str] = field(default_factory=list)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _classify_error(message: str) -> str:
    msg = message.lower()
    if any(k in msg for k in ("timeout", "timed out", "time out")):
        return "timeout"
    if any(k in msg for k in ("networkerror", "net::", "econnrefused", "fetch failed", "econnreset")):
        return "network"
    if any(k in msg for k in ("expect(", "toequal", "tobetruthy", "tocontain", "assert")):
        return "assertion"
    return "other"


def _first_error(results: list[dict]) -> str:
    for r in results:
        for err in r.get("errors", []):
            msg = err.get("message", "").strip()
            if msg:
                return msg[:600]
    return ""


def _walk_suites(suites: list[dict], parent_title: str = "") -> list[dict]:
    """Yield flat list of {title, file, tests} for every spec found recursively."""
    specs = []
    for suite in suites:
        title = suite.get("title", "")
        full_title = f"{parent_title} > {title}".strip(" >") if parent_title else title
        for spec in suite.get("specs", []):
            specs.append({
                "title": f"{full_title} > {spec['title']}".strip(" >"),
                "file": spec.get("file", suite.get("file", "")),
                "tests": spec.get("tests", []),
            })
        specs.extend(_walk_suites(suite.get("suites", []), full_title))
    return specs


# ---------------------------------------------------------------------------
# Core analysis
# ---------------------------------------------------------------------------

def extract_failures(report: dict, label: str) -> list[TestFailure]:
    failures = []
    top_suites = report.get("suites", [])
    for spec in _walk_suites(top_suites):
        for test in spec["tests"]:
            status = test.get("status", "")
            if status not in ("unexpected", "flaky"):
                continue
            raw_error = _first_error(test.get("results", []))
            failures.append(TestFailure(
                name=spec["title"],
                file=spec["file"],
                status=status,
                error=raw_error,
                error_type=_classify_error(raw_error),
                versions=[label],
            ))
    return failures


def merge_failures(all_by_version: dict[str, list[TestFailure]]) -> dict[str, TestFailure]:
    """Merge failures from multiple versions keyed by test name."""
    merged: dict[str, TestFailure] = {}
    for label, failures in all_by_version.items():
        for f in failures:
            if f.name in merged:
                existing = merged[f.name]
                if label not in existing.versions:
                    existing.versions.append(label)
                # Upgrade to unexpected if seen as hard failure anywhere
                if f.status == "unexpected":
                    existing.status = "unexpected"
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

    flaky_tests    = [f for f in merged.values() if f.status == "flaky"]
    product_bugs   = [f for f in merged.values() if f.status == "unexpected"]
    cross_version  = [f for f in product_bugs if len(f.versions) > 1]

    def serialize(f: TestFailure) -> dict:
        return {
            "name":       f.name,
            "file":       f.file,
            "versions":   f.versions,
            "error":      f.error,
            "error_type": f.error_type,
        }

    result = {
        "summary": {
            "total_unique_failures": len(merged),
            "flaky":                 len(flaky_tests),
            "product_bugs":          len(product_bugs),
            "cross_version_bugs":    len(cross_version),
        },
        "flaky_tests":  [serialize(f) for f in sorted(flaky_tests,  key=lambda x: x.name)],
        "product_bugs": [serialize(f) for f in sorted(product_bugs, key=lambda x: x.name)],
        "run_stats":    run_stats,
    }

    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print(f"Usage: {sys.argv[0]} <artifacts_dir>", file=sys.stderr)
        sys.exit(1)
    main(Path(sys.argv[1]))
