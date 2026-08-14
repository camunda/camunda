"""Unit tests for unskip-scope.py

Run with:
    pytest .github/scripts/test_unskip_scope.py
"""

import importlib.util
import os
import sys

import pytest

# Load the hyphen-named module via importlib and register it so mock.patch works.
_SCRIPT = os.path.join(os.path.dirname(__file__), "unskip-scope.py")
_spec = importlib.util.spec_from_file_location("unskip_scope", _SCRIPT)
u = importlib.util.module_from_spec(_spec)
sys.modules["unskip_scope"] = u
_spec.loader.exec_module(u)

SUITE = u.SUITE

# Verbatim excerpt of camunda/camunda#60156's diff (stable/8.9 E2E unskip).
REAL_DIFF = f"""diff --git a/{SUITE}/tests/operate/dashboard.spec.ts b/{SUITE}/tests/operate/dashboard.spec.ts
--- a/{SUITE}/tests/operate/dashboard.spec.ts
+++ b/{SUITE}/tests/operate/dashboard.spec.ts
@@ -148,8 +148,7 @@ test.describe('Dashboard', () => {{
     }});
   }});

-  //Skipped due to bug 45129: https://github.com/camunda/camunda/issues/45129
-  test.skip('Navigate to processes view (same truncated error message)', async ({{
+  test('Navigate to processes view (same truncated error message)', async ({{
     operateDashboardPage,
   }}) => {{
diff --git a/{SUITE}/tests/operate/operations.spec.ts b/{SUITE}/tests/operate/operations.spec.ts
--- a/{SUITE}/tests/operate/operations.spec.ts
+++ b/{SUITE}/tests/operate/operations.spec.ts
@@ -65,9 +65,8 @@ test.describe('Operations', () => {{
   }});

-  // Skipped due to bug 42375: https://github.com/camunda/camunda/issues/42375
   // !Note: assert the code after the bug is fixed
-  test.skip('Retry and cancel single instance', async ({{
+  test('Retry and cancel single instance', async ({{
     page,
   }}) => {{
"""


class TestParseDiff:
    def test_extracts_unskipped_tests_with_their_bugs(self):
        files = u.parse_diff(REAL_DIFF)
        assert sorted(files) == [
            "tests/operate/dashboard.spec.ts",
            "tests/operate/operations.spec.ts",
        ]
        assert files["tests/operate/dashboard.spec.ts"]["tests"] == [
            {
                "test_name": "Navigate to processes view (same truncated error message)",
                "bug": "https://github.com/camunda/camunda/issues/45129",
            }
        ]

    def test_marker_separated_from_the_call_by_kept_prose_still_attributes(self):
        # operations.spec.ts keeps a "!Note:" comment between marker and call.
        tests = u.parse_diff(REAL_DIFF)["tests/operate/operations.spec.ts"]["tests"]
        assert tests[0]["bug"] == "https://github.com/camunda/camunda/issues/42375"

    def test_ignores_files_outside_the_suite_and_non_specs(self):
        diff = (
            "+++ b/operate/client/src/App.tsx\n"
            "+  test('not a suite spec', async () => {\n"
            f"+++ b/{SUITE}/utils/helper.ts\n"
            "+  test('not a spec file', async () => {\n"
        )
        assert u.parse_diff(diff) == {}

    @pytest.mark.parametrize(
        "line,expected",
        [
            ("+  test(\"double quoted\", async () => {", "double quoted"),
            ("+  test.describe('a describe block', () => {", "a describe block"),
            ("+    test.step(`a step`, async () => {", "a step"),
        ],
    )
    def test_recognises_every_flipped_call_shape(self, line, expected):
        diff = f"+++ b/{SUITE}/tests/operate/x.spec.ts\n{line}\n"
        tests = u.parse_diff(diff)["tests/operate/x.spec.ts"]["tests"]
        assert tests[0]["test_name"] == expected

    def test_test_without_a_marker_has_no_bug(self):
        diff = f"+++ b/{SUITE}/tests/operate/x.spec.ts\n+  test('orphan', async () => {{\n"
        tests = u.parse_diff(diff)["tests/operate/x.spec.ts"]["tests"]
        assert tests[0]["bug"] is None

    def test_a_marker_is_consumed_by_only_one_test(self):
        diff = (
            f"+++ b/{SUITE}/tests/operate/x.spec.ts\n"
            "-  // Skipped due to bug: https://github.com/camunda/camunda/issues/1\n"
            "+  test('first', async () => {\n"
            "+  test('second', async () => {\n"
        )
        tests = u.parse_diff(diff)["tests/operate/x.spec.ts"]["tests"]
        assert tests[0]["bug"] == "https://github.com/camunda/camunda/issues/1"
        assert tests[1]["bug"] is None


class TestParseManualMarkers:
    BODY = f"""## Auto-Unskip

### Files Changed (1)

- `{SUITE}/tests/operate/dashboard.spec.ts`: 1 skip(s) removed

### ⚠️ Needs manual review — left untouched (2)

- [ ] **[`operations.spec.ts`:120](https://github.com/camunda/camunda/blob/sha/x#L120)** — closed bug [camunda/camunda#42375](https://github.com/camunda/camunda/issues/42375)
  - Why it was skipped: Skip is inside a conditional/ternary.
  - Path: `{SUITE}/tests/operate/operations.spec.ts`
- [ ] **[`task-panel.spec.ts`:44](https://github.com/camunda/camunda/blob/sha/y#L44)** — closed bug [camunda/camunda#44583](https://github.com/camunda/camunda/issues/44583)
  - Why it was skipped: Marker sits above commented-out code.
  - Path: `{SUITE}/tests/tasklist/v1/task-panel.spec.ts`

### Checklist

- [ ] On-demand test run passes on this branch
"""

    def test_extracts_every_marker_with_path_line_bug_and_reason(self):
        markers = u.parse_manual_markers(self.BODY)
        assert len(markers) == 2
        assert markers[0] == {
            "line": 120,
            "bug": "https://github.com/camunda/camunda/issues/42375",
            "bug_key": "camunda/camunda#42375",
            "path": "tests/operate/operations.spec.ts",
            "reason": "Skip is inside a conditional/ternary.",
        }
        assert markers[1]["path"] == "tests/tasklist/v1/task-panel.spec.ts"

    def test_stops_at_the_next_section(self):
        # The trailing "Checklist" items are also `- [ ]` lines; they must not
        # be read as markers.
        assert len(u.parse_manual_markers(self.BODY)) == 2

    def test_body_without_the_section_yields_nothing(self):
        assert u.parse_manual_markers("## Auto-Unskip\n\n### Files Changed\n") == []

    def test_empty_body_is_tolerated(self):
        assert u.parse_manual_markers("") == []
        assert u.parse_manual_markers(None) == []


class TestVersionOf:
    @pytest.mark.parametrize(
        "base,expected",
        [
            ("main", ("main", "main")),
            ("stable/8.9", ("8.9", "stable-8.9")),
            ("stable/8.7", ("8.7", "stable-8.7")),
        ],
    )
    def test_maps_base_branch_to_version(self, base, expected):
        assert u.version_of(base) == expected

    def test_rejects_an_unsupported_base(self):
        with pytest.raises(SystemExit):
            u.version_of("release-8.9.1")


class TestBuildMatrix:
    def test_e2e_on_89_covers_both_tasklist_generations(self):
        cells = u.build_matrix("8.9", has_e2e=True, has_api=False)
        assert [c["tasklist_mode"] for c in cells] == ["v1", "v2"]
        assert {c["database"] for c in cells} == {"es"}

    def test_e2e_on_main_is_v2_only(self):
        cells = u.build_matrix("main", has_e2e=True, has_api=False)
        assert [c["cell"] for c in cells] == ["e2e-es-v2"]

    def test_e2e_on_87_is_v1_only(self):
        cells = u.build_matrix("8.7", has_e2e=True, has_api=False)
        assert [c["cell"] for c in cells] == ["e2e-es-v1"]

    def test_api_adds_rdbms_only_where_the_nightly_runs_it(self):
        assert [c["database"] for c in u.build_matrix("8.9", False, True)] == [
            "es",
            "h2",
        ]
        assert [c["database"] for c in u.build_matrix("8.8", False, True)] == ["es"]

    def test_mixed_pr_unions_both_categories(self):
        cells = u.build_matrix("8.9", has_e2e=True, has_api=True)
        assert [c["cell"] for c in cells] == [
            "e2e-es-v1",
            "e2e-es-v2",
            "api-es",
            "api-h2",
        ]

    def test_no_specs_yields_no_cells(self):
        assert u.build_matrix("8.9", has_e2e=False, has_api=False) == []

    def test_unknown_version_falls_back_to_the_safe_dimensions(self):
        assert [c["cell"] for c in u.build_matrix("9.0", True, True)] == [
            "e2e-es-v2",
            "api-es",
        ]
