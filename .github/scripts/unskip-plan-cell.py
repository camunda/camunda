#!/usr/bin/env python3
"""Plan one environment cell of an unskip-verify run.

The matrix covers every environment dimension a version runs, but a given
spec only executes in some of them — `playwright.config.ts` selects
`tests/tasklist/v1/**` in Tasklist v1 mode and everything else in v2 mode,
and puts `task-panel.spec.ts` in its own teardown project. Rather than
re-implementing those rules here (they differ per branch), ask Playwright
itself with `--list` and read the answer back.

Subcommands:
    candidates  Print the scope's spec paths for this cell's category — the
                path filters to hand to `playwright test --list`.
    plan        Read that `--list` JSON and emit what the cell should actually
                run: `spec_paths`, `project_args`, and the un-skipped tests
                and manual-review markers that belong to those files. Emits
                `run=false` when nothing executes here, so the cell can exit
                before standing up an environment.

An unreadable or empty `--list` result never prunes: the cell falls back to
running every candidate path with no project filter.
"""

import argparse
import json
import os
import sys

# The projects CI itself runs: `--project=chromium` for E2E and
# `--project=api-tests` for API (each pulling in its own `-subset` teardown).
# Verifying under exactly these keeps the signal comparable to the nightly.
ALLOWED_PROJECTS = {
    "chromium",
    "chromium-subset",
    "api-tests",
    "api-tests-subset",
}

# Projects that re-run the same specs under another browser or a
# component-scoped alias of chromium — dropping them is free.
REDUNDANT_PROJECTS = {
    "firefox",
    "firefox-subset",
    "msedge",
    "msedge-subset",
    "webkit",
    "webkit-subset",
    "operate-e2e",
    "tasklist-e2e",
    "identity-e2e",
}


def load_json(path):
    """Parse a JSON file, tolerating a tool banner printed before the body."""
    try:
        with open(path, encoding="utf-8") as handle:
            raw = handle.read()
    except OSError:
        return None
    try:
        return json.loads(raw)
    except ValueError:
        pass
    start = raw.find("\n{")
    if start == -1:
        return None
    try:
        return json.loads(raw[start + 1 :])
    except ValueError:
        return None


def category_of(spec_path):
    return "api" if spec_path.startswith("tests/api/") else "e2e"


def candidate_paths(scope, category):
    """Every scope path in this category — diff files plus marker-only files."""
    paths = {s["file"] for s in scope.get("specs", [])}
    paths |= {m["path"] for m in scope.get("manual_markers", []) if m.get("path")}
    return sorted(p for p in paths if category_of(p) == category)


def walk_specs(node, acc):
    """Collect (file, projectName) from a Playwright `--list` report tree."""
    if isinstance(node, list):
        for item in node:
            walk_specs(item, acc)
        return
    if not isinstance(node, dict):
        return
    for spec in node.get("specs", []) or []:
        spec_file = spec.get("file") or node.get("file")
        for test in spec.get("tests", []) or []:
            project = test.get("projectName") or test.get("projectId")
            if spec_file and project:
                acc.append((spec_file, project))
    walk_specs(node.get("suites", []) or [], acc)


def plan(scope, listing, category):
    """Return the cell plan: which paths run here, and under which projects.

    A candidate lands in one of three buckets: it runs here (listed under a
    project CI runs), it needs a project this environment cannot provide (e.g.
    `optimize-startup`, which wants an Optimize container) — reported so a human
    sees it — or it simply does not run in this dimension and is pruned.
    """
    candidates = candidate_paths(scope, category)
    if listing is None:
        return {
            "spec_paths": candidates,
            "projects": [],
            "unsupported": [],
            "fallback": True,
        }

    found = []
    walk_specs(listing.get("suites", []), found)
    allowed = set(candidates)
    by_file = {}
    for spec_file, project in found:
        if spec_file in allowed:
            by_file.setdefault(spec_file, set()).add(project)

    spec_paths, projects, unsupported = set(), set(), []
    for spec_file, listed in by_file.items():
        runnable = listed & ALLOWED_PROJECTS
        if runnable:
            spec_paths.add(spec_file)
            projects |= runnable
            continue
        other = sorted(listed - REDUNDANT_PROJECTS)
        if other:
            unsupported.append({"file": spec_file, "projects": other})
    return {
        "spec_paths": sorted(spec_paths),
        "projects": sorted(projects),
        "unsupported": sorted(unsupported, key=lambda u: u["file"]),
        "fallback": False,
    }


def emit_outputs(pairs):
    path = os.environ.get("GITHUB_OUTPUT")
    if not path:
        for key, value in pairs:
            print(f"{key}={value}")
        return
    with open(path, "a", encoding="utf-8") as handle:
        for key, value in pairs:
            handle.write(f"{key}={value}\n")


def cmd_candidates(args):
    scope = load_json(args.scope)
    if scope is None:
        sys.exit(f"::error::cannot read scope file {args.scope}")
    print(" ".join(candidate_paths(scope, args.category)))


def cmd_plan(args):
    scope = load_json(args.scope)
    if scope is None:
        sys.exit(f"::error::cannot read scope file {args.scope}")
    result = plan(scope, load_json(args.list), args.category)
    spec_paths = result["spec_paths"]

    by_file = {s["file"]: s for s in scope.get("specs", [])}
    cell = {
        "cell": args.cell,
        "category": args.category,
        "database": args.database,
        "tasklist_mode": args.tasklist_mode,
        "spec_paths": spec_paths,
        "projects": result["projects"],
        "fallback": result["fallback"],
        "unsupported": result.get("unsupported", []),
        "unskipped_tests": [
            {"file": path, **test}
            for path in spec_paths
            for test in by_file.get(path, {}).get("tests", [])
        ],
        "manual_markers": [
            marker
            for marker in scope.get("manual_markers", [])
            if marker.get("path") in spec_paths
        ],
    }
    with open(args.out, "w", encoding="utf-8") as handle:
        json.dump(cell, handle, indent=2)

    project_args = " ".join(f"--project={p}" for p in result["projects"])
    emit_outputs(
        [
            ("run", "true" if spec_paths else "false"),
            ("spec_paths", " ".join(spec_paths)),
            ("project_args", project_args),
            ("spec_count", len(spec_paths)),
            ("test_count", len(cell["unskipped_tests"])),
            ("marker_count", len(cell["manual_markers"])),
            ("unsupported_count", len(cell["unsupported"])),
        ]
    )
    for entry in cell["unsupported"]:
        print(
            f"::warning::{entry['file']} only runs under "
            f"{', '.join(entry['projects'])}, which this environment does not "
            f"provide — not verified here."
        )
    if not spec_paths:
        print(f"Cell {args.cell}: no scope spec runs in this dimension — pruning.")
    else:
        print(
            f"Cell {args.cell}: {len(spec_paths)} spec(s), "
            f"projects=[{', '.join(result['projects']) or 'all'}], "
            f"{len(cell['manual_markers'])} manual marker(s)"
            + (" (list unavailable — no pruning)" if result["fallback"] else "")
        )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)

    candidates = sub.add_parser("candidates")
    candidates.add_argument("--scope", required=True)
    candidates.add_argument("--category", required=True, choices=["e2e", "api"])
    candidates.set_defaults(func=cmd_candidates)

    planner = sub.add_parser("plan")
    planner.add_argument("--scope", required=True)
    planner.add_argument("--list", required=True)
    planner.add_argument("--category", required=True, choices=["e2e", "api"])
    planner.add_argument("--cell", default="")
    planner.add_argument("--database", default="es")
    planner.add_argument("--tasklist-mode", dest="tasklist_mode", default="v2")
    planner.add_argument("--out", default="cell-plan.json")
    planner.set_defaults(func=cmd_plan)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
