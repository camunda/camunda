"""Unit tests for unskip-plan-cell.py

Run with:
    pytest .github/scripts/test_unskip_plan_cell.py
"""

import importlib.util
import json
import os
import sys

import pytest

# Load the hyphen-named module via importlib and register it so mock.patch works.
_SCRIPT = os.path.join(os.path.dirname(__file__), "unskip-plan-cell.py")
_spec = importlib.util.spec_from_file_location("unskip_plan_cell", _SCRIPT)
p = importlib.util.module_from_spec(_spec)
sys.modules["unskip_plan_cell"] = p
_spec.loader.exec_module(p)

SCOPE = {
    "specs": [
        {
            "file": "tests/operate/dashboard.spec.ts",
            "tests": [{"test_name": "navigate", "bug": "https://x/issues/1"}],
        },
        {
            "file": "tests/tasklist/v1/task-panel.spec.ts",
            "tests": [{"test_name": "scrolling", "bug": "https://x/issues/2"}],
        },
        {
            "file": "tests/api/v2/variable/variable-search-api.spec.ts",
            "tests": [{"test_name": "search", "bug": None}],
        },
    ],
    "manual_markers": [
        {"path": "tests/operate/operations.spec.ts", "line": 12, "bug": "https://x/3"},
        {"path": "tests/api/v2/clock/clock-pin-api.spec.ts", "line": 8, "bug": None},
    ],
}


def listing(entries):
    """Build a Playwright `--list` report tree from (file, project) pairs."""
    suites = []
    for spec_file, project in entries:
        suites.append(
            {
                "title": spec_file,
                "file": spec_file,
                "specs": [],
                "suites": [
                    {
                        "title": "a describe",
                        "file": spec_file,
                        "specs": [
                            {
                                "title": "a test",
                                "file": spec_file,
                                "tests": [{"projectName": project}],
                            }
                        ],
                    }
                ],
            }
        )
    return {"suites": suites, "errors": []}


class TestCandidatePaths:
    def test_e2e_candidates_exclude_api_and_include_marker_only_files(self):
        assert p.candidate_paths(SCOPE, "e2e") == [
            "tests/operate/dashboard.spec.ts",
            "tests/operate/operations.spec.ts",
            "tests/tasklist/v1/task-panel.spec.ts",
        ]

    def test_api_candidates_are_only_api_paths(self):
        assert p.candidate_paths(SCOPE, "api") == [
            "tests/api/v2/clock/clock-pin-api.spec.ts",
            "tests/api/v2/variable/variable-search-api.spec.ts",
        ]

    def test_marker_without_a_path_is_ignored(self):
        scope = {"specs": [], "manual_markers": [{"line": 3}]}
        assert p.candidate_paths(scope, "e2e") == []


class TestPlan:
    def test_v2_mode_keeps_only_the_specs_playwright_lists(self):
        # In v2 mode `tests/tasklist/v1/**` is in testIgnore, so it never lists.
        result = p.plan(
            SCOPE,
            listing([("tests/operate/dashboard.spec.ts", "chromium")]),
            "e2e",
        )
        assert result["spec_paths"] == ["tests/operate/dashboard.spec.ts"]
        assert result["projects"] == ["chromium"]

    def test_v1_mode_keeps_the_v1_spec_under_its_teardown_project(self):
        result = p.plan(
            SCOPE,
            listing([("tests/tasklist/v1/task-panel.spec.ts", "chromium-subset")]),
            "e2e",
        )
        assert result["spec_paths"] == ["tests/tasklist/v1/task-panel.spec.ts"]
        assert result["projects"] == ["chromium-subset"]

    def test_cross_browser_and_component_scoped_projects_are_dropped(self):
        # CI runs `--project=chromium`; firefox/msedge and the component-scoped
        # aliases (operate-e2e) would re-run the very same specs.
        result = p.plan(
            SCOPE,
            listing(
                [
                    ("tests/operate/dashboard.spec.ts", "chromium"),
                    ("tests/operate/dashboard.spec.ts", "firefox"),
                    ("tests/operate/dashboard.spec.ts", "msedge"),
                    ("tests/operate/dashboard.spec.ts", "operate-e2e"),
                ]
            ),
            "e2e",
        )
        assert result["projects"] == ["chromium"]
        assert result["unsupported"] == []

    def test_a_spec_needing_an_unprovided_project_is_reported_not_pruned(self):
        # optimize-startup wants an Optimize container the verify env never
        # starts — silently dropping it would read as "verified".
        result = p.plan(
            SCOPE,
            listing([("tests/operate/dashboard.spec.ts", "optimize-startup")]),
            "e2e",
        )
        assert result["spec_paths"] == []
        assert result["unsupported"] == [
            {"file": "tests/operate/dashboard.spec.ts", "projects": ["optimize-startup"]}
        ]

    def test_a_spec_reachable_under_chromium_is_not_reported_unsupported(self):
        result = p.plan(
            SCOPE,
            listing(
                [
                    ("tests/operate/dashboard.spec.ts", "chromium"),
                    ("tests/operate/dashboard.spec.ts", "optimize-startup"),
                ]
            ),
            "e2e",
        )
        assert result["spec_paths"] == ["tests/operate/dashboard.spec.ts"]
        assert result["unsupported"] == []

    def test_api_specs_map_to_the_api_projects(self):
        result = p.plan(
            SCOPE,
            listing(
                [
                    ("tests/api/v2/variable/variable-search-api.spec.ts", "api-tests"),
                    ("tests/api/v2/clock/clock-pin-api.spec.ts", "api-tests-subset"),
                ]
            ),
            "api",
        )
        assert result["projects"] == ["api-tests", "api-tests-subset"]

    def test_a_file_outside_the_scope_is_never_added(self):
        result = p.plan(
            SCOPE,
            listing([("tests/operate/unrelated.spec.ts", "chromium")]),
            "e2e",
        )
        assert result["spec_paths"] == []

    def test_nothing_listed_prunes_the_cell(self):
        result = p.plan(SCOPE, listing([]), "e2e")
        assert result["spec_paths"] == []
        assert result["fallback"] is False

    def test_unreadable_list_falls_back_to_every_candidate(self):
        result = p.plan(SCOPE, None, "api")
        assert result["spec_paths"] == p.candidate_paths(SCOPE, "api")
        assert result["projects"] == []
        assert result["fallback"] is True


class TestLoadJson:
    def test_reads_plain_json(self, tmp_path):
        path = tmp_path / "a.json"
        path.write_text('{"suites": []}', encoding="utf-8")
        assert p.load_json(str(path)) == {"suites": []}

    def test_tolerates_a_tool_banner_before_the_body(self, tmp_path):
        # Some npx/bun wrappers print e.g. "◇ injected env (6) from .env" on
        # stdout ahead of the report.
        path = tmp_path / "b.json"
        path.write_text('◇ injected env (6) from .env\n{\n  "suites": []\n}\n', encoding="utf-8")
        assert p.load_json(str(path)) == {"suites": []}

    def test_missing_or_garbage_returns_none(self, tmp_path):
        assert p.load_json(str(tmp_path / "nope.json")) is None
        path = tmp_path / "c.json"
        path.write_text("not json at all", encoding="utf-8")
        assert p.load_json(str(path)) is None


class TestCmdPlan:
    def _run(self, tmp_path, monkeypatch, list_payload, category="e2e"):
        scope_file = tmp_path / "scope.json"
        scope_file.write_text(json.dumps(SCOPE), encoding="utf-8")
        list_file = tmp_path / "list.json"
        list_file.write_text(json.dumps(list_payload), encoding="utf-8")
        out_file = tmp_path / "cell.json"
        github_output = tmp_path / "gh_output"
        monkeypatch.setenv("GITHUB_OUTPUT", str(github_output))

        args = type(
            "Args",
            (),
            {
                "scope": str(scope_file),
                "list": str(list_file),
                "category": category,
                "cell": "e2e-es-v2",
                "database": "es",
                "tasklist_mode": "v2",
                "out": str(out_file),
            },
        )
        p.cmd_plan(args)
        outputs = dict(
            line.split("=", 1)
            for line in github_output.read_text(encoding="utf-8").splitlines()
        )
        return json.loads(out_file.read_text(encoding="utf-8")), outputs

    def test_emits_project_args_and_carries_tests_and_markers(
        self, tmp_path, monkeypatch
    ):
        cell, outputs = self._run(
            tmp_path,
            monkeypatch,
            listing(
                [
                    ("tests/operate/dashboard.spec.ts", "chromium"),
                    ("tests/operate/operations.spec.ts", "chromium"),
                ]
            ),
        )
        assert outputs["run"] == "true"
        assert outputs["project_args"] == "--project=chromium"
        assert outputs["test_count"] == "1"
        assert outputs["marker_count"] == "1"
        assert cell["unskipped_tests"] == [
            {
                "file": "tests/operate/dashboard.spec.ts",
                "test_name": "navigate",
                "bug": "https://x/issues/1",
            }
        ]
        assert cell["manual_markers"][0]["path"] == "tests/operate/operations.spec.ts"

    def test_pruned_cell_reports_run_false(self, tmp_path, monkeypatch):
        _, outputs = self._run(tmp_path, monkeypatch, listing([]))
        assert outputs["run"] == "false"
        assert outputs["spec_paths"] == ""


@pytest.mark.parametrize(
    "path,expected",
    [
        ("tests/api/v2/x.spec.ts", "api"),
        ("tests/operate/x.spec.ts", "e2e"),
        ("tests/tasklist/v1/x.spec.ts", "e2e"),
    ],
)
def test_category_of(path, expected):
    assert p.category_of(path) == expected
