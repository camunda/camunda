"""Unit tests for the sticky-alert flaky-test detector.

Run with:
    python3 -m unittest .github/actions/detect-new-flaky-tests/test_detect_new_flaky_tests.py
"""

import json
import os
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(__file__))

import detect_new_flaky_tests as d  # noqa: E402


# ---------------------------------------------------------------------------
# Test-name parsing
# ---------------------------------------------------------------------------

class TestParse(unittest.TestCase):
    def test_strips_params_and_index(self):
        p = d.parse_test_name(
            "io.camunda.it.foo.BarTest.shouldDoX(CamundaClient)[1]"
        )
        self.assertEqual(p["packageName"], "io.camunda.it.foo")
        self.assertEqual(p["className"], "BarTest")
        self.assertEqual(p["methodName"], "shouldDoX")

    def test_handles_no_dot(self):
        p = d.parse_test_name("Plain")
        self.assertEqual(p, {"fullName": "Plain"})

    def test_get_key_normalises(self):
        # The legacy regex collapses [N](Param) into just N, so the index is
        # preserved while parens are stripped: m[1](Param) -> m1.
        key = d.get_test_key({
            "packageName": "p",
            "className": "C",
            "methodName": "m[1](Param)",
        })
        self.assertEqual(key, "p.C.m1")


# ---------------------------------------------------------------------------
# Flaky-data deduplication
# ---------------------------------------------------------------------------

class TestProcessFlakyTestsData(unittest.TestCase):
    def test_dedupe_and_job_merge(self):
        raw = [
            {"job": "j1", "flaky_tests": "p.C.foo(Cli)"},
            {"job": "j2", "flaky_tests": "p.C.foo(Cli)"},
        ]
        out = d.process_flaky_tests_data(raw)
        self.assertEqual(len(out), 1)
        self.assertEqual(set(out[0]["jobs"]), {"j1", "j2"})
        self.assertEqual(out[0]["currentRunFailures"], 2)

    def test_lifecycle_dropped(self):
        raw = [{"job": "j", "flaky_tests": "p.C.<beforeAll>"}]
        out = d.process_flaky_tests_data(raw)
        self.assertEqual(out, [])

    def test_empty_input_ignored(self):
        self.assertEqual(d.process_flaky_tests_data([]), [])
        self.assertEqual(
            d.process_flaky_tests_data([{"job": "j", "flaky_tests": " "}]), []
        )


# ---------------------------------------------------------------------------
# Baseline boundary matching
# ---------------------------------------------------------------------------

class TestBaseline(unittest.TestCase):
    def test_exact(self):
        self.assertTrue(d.is_in_baseline("p.C.m", {"p.C.m"}))

    def test_paren_boundary(self):
        self.assertTrue(d.is_in_baseline("p.C.m", {"p.C.m(Cli) variant"}))

    def test_index_boundary(self):
        self.assertTrue(d.is_in_baseline("p.C.m", {"p.C.m[5]"}))

    def test_prefix_of_different_method_no_match(self):
        self.assertFalse(d.is_in_baseline("p.C.shouldFind",
                                          {"p.C.shouldFindAll(Cli)"}))

    def test_underscore_no_match(self):
        self.assertFalse(d.is_in_baseline("p.C.m", {"p.C.m_v2(Cli)"}))


# ---------------------------------------------------------------------------
# State persistence
# ---------------------------------------------------------------------------

class TestStatePersistence(unittest.TestCase):
    def test_empty_load_when_missing(self):
        s = d.load_state("/nonexistent/path.json", 42, "abc")
        self.assertEqual(s["pr_number"], 42)
        self.assertEqual(s["last_known_head_sha"], "abc")
        self.assertEqual(s["tests"], [])

    def test_roundtrip(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "subdir", "state.json")
            original = d.empty_state(99, "head1")
            original["tests"].append({"key": "p.C.m", "status": "active"})
            d.save_state(path, original)
            loaded = d.load_state(path, 99, "head1")
            self.assertEqual(loaded["pr_number"], 99)
            self.assertEqual(loaded["tests"][0]["key"], "p.C.m")

    def test_schema_mismatch_resets(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "state.json")
            with open(path, "w") as fh:
                json.dump({"schema_version": 999}, fh)
            s = d.load_state(path, 1, "head")
            self.assertEqual(s["tests"], [])


# ---------------------------------------------------------------------------
# Sticky-state reconciliation (mocked git)
# ---------------------------------------------------------------------------

def _make_state(*entries):
    return {
        "schema_version": d.SCHEMA_VERSION,
        "pr_number": 1,
        "last_known_head_sha": "head0",
        "last_updated_at": "now",
        "tests": list(entries),
    }


def _make_entry(**overrides):
    base = {
        "key": "p.C.m",
        "package": "p",
        "class_name": "C",
        "method_name": "m",
        "file_path": "p/C.java",
        "first_flagged_sha": "head0",
        "flagged_jobs": ["general-unit-tests/General"],
        "method_last_modified_sha": None,
        "clean_runs_since_modified": 0,
        "last_observed_sha": "head0",
        "last_observed_at": "t0",
        "status": "active",
        "cleared_at": None,
    }
    base.update(overrides)
    return base


class TestMergeNewFlakes(unittest.TestCase):
    def test_brand_new_entry_added(self):
        state = _make_state()
        with mock.patch.object(d, "find_class_file", return_value="p/C.java"):
            d.merge_new_flakes(
                state,
                [{"packageName": "p", "className": "C",
                  "methodName": "m", "jobs": ["j1"]}],
                "headN", repo_root=".",
            )
        self.assertEqual(len(state["tests"]), 1)
        self.assertEqual(state["tests"][0]["status"], "active")
        self.assertEqual(state["tests"][0]["first_flagged_sha"], "headN")

    def test_re_flake_resets_active_counter(self):
        state = _make_state(_make_entry(
            method_last_modified_sha="fix1",
            clean_runs_since_modified=2,
        ))
        d.merge_new_flakes(
            state,
            [{"packageName": "p", "className": "C",
              "methodName": "m", "jobs": ["j1"]}],
            "headN", repo_root=".",
        )
        self.assertEqual(state["tests"][0]["clean_runs_since_modified"], 0)

    def test_re_flake_demotes_cleared(self):
        state = _make_state(_make_entry(
            status="cleared_via_fix",
            cleared_at="earlier",
            method_last_modified_sha="fix1",
            clean_runs_since_modified=3,
        ))
        with mock.patch.object(d, "find_class_file", return_value="p/C.java"):
            d.merge_new_flakes(
                state,
                [{"packageName": "p", "className": "C",
                  "methodName": "m", "jobs": ["j1"]}],
                "headN", repo_root=".",
            )
        entry = state["tests"][0]
        self.assertEqual(entry["status"], "active")
        self.assertEqual(entry["first_flagged_sha"], "headN")
        self.assertIsNone(entry["method_last_modified_sha"])
        self.assertEqual(entry["clean_runs_since_modified"], 0)
        self.assertNotIn("cleared_at", entry)


class TestIncrementCounters(unittest.TestCase):
    def test_increment_when_modified_and_clean_and_job_ran(self):
        state = _make_state(_make_entry(
            method_last_modified_sha="fix1",
            clean_runs_since_modified=1,
        ))
        d.increment_counters_if_clean(
            state, current_flake_keys=set(),
            ran_parent_jobs={"general-unit-tests"}, head_sha="hN",
        )
        self.assertEqual(state["tests"][0]["clean_runs_since_modified"], 2)

    def test_no_increment_without_modification(self):
        state = _make_state(_make_entry(method_last_modified_sha=None))
        d.increment_counters_if_clean(
            state, set(), {"general-unit-tests"}, "hN")
        self.assertEqual(state["tests"][0]["clean_runs_since_modified"], 0)

    def test_reflake_resets_counter_to_zero(self):
        state = _make_state(_make_entry(
            method_last_modified_sha="fix1",
            clean_runs_since_modified=2,
        ))
        d.increment_counters_if_clean(
            state, current_flake_keys={"p.C.m"},
            ran_parent_jobs={"general-unit-tests"}, head_sha="hN",
        )
        # Re-flake must reset the counter, not leave it unchanged, so the
        # "fix + 3 clean runs" clearance restarts after any intermittent flake.
        self.assertEqual(state["tests"][0]["clean_runs_since_modified"], 0)

    def test_reflake_resets_counter_even_without_modification(self):
        state = _make_state(_make_entry(
            method_last_modified_sha=None,
            clean_runs_since_modified=2,
        ))
        d.increment_counters_if_clean(
            state, current_flake_keys={"p.C.m"},
            ran_parent_jobs={"general-unit-tests"}, head_sha="hN",
        )
        self.assertEqual(state["tests"][0]["clean_runs_since_modified"], 0)

    def test_no_increment_when_job_not_in_ran(self):
        state = _make_state(_make_entry(
            method_last_modified_sha="fix1",
            clean_runs_since_modified=1,
            flagged_jobs=["identity-tests/identity-tests - elasticsearch9"],
        ))
        d.increment_counters_if_clean(
            state, set(), {"general-unit-tests"}, "hN")
        # identity-tests parent did not run; counter unchanged.
        self.assertEqual(state["tests"][0]["clean_runs_since_modified"], 1)

    def test_increment_with_matrix_job_key(self):
        state = _make_state(_make_entry(
            method_last_modified_sha="fix1",
            clean_runs_since_modified=0,
            flagged_jobs=["identity-tests/identity-tests - elasticsearch9"],
        ))
        d.increment_counters_if_clean(
            state, set(), {"identity-tests"}, "hN")
        self.assertEqual(state["tests"][0]["clean_runs_since_modified"], 1)


class TestClearance(unittest.TestCase):
    def test_clears_at_threshold(self):
        state = _make_state(_make_entry(
            method_last_modified_sha="fix1",
            clean_runs_since_modified=d.MIN_CLEAN_RUNS,
        ))
        d.apply_clearance(state)
        self.assertEqual(state["tests"][0]["status"], "cleared_via_fix")
        self.assertIsNotNone(state["tests"][0]["cleared_at"])

    def test_does_not_clear_below_threshold(self):
        state = _make_state(_make_entry(
            method_last_modified_sha="fix1",
            clean_runs_since_modified=d.MIN_CLEAN_RUNS - 1,
        ))
        d.apply_clearance(state)
        self.assertEqual(state["tests"][0]["status"], "active")


class TestBypass(unittest.TestCase):
    def test_marks_all_active_as_bypassed(self):
        state = _make_state(
            _make_entry(key="t1"),
            _make_entry(key="t2", status="cleared_via_fix"),
            _make_entry(key="t3"),
        )
        d.apply_bypass(state)
        self.assertEqual(state["tests"][0]["status"], "cleared_via_bypass")
        self.assertEqual(state["tests"][1]["status"], "cleared_via_fix")
        self.assertEqual(state["tests"][2]["status"], "cleared_via_bypass")


class TestReconcileForcePush(unittest.TestCase):
    def test_drops_when_anchor_unreachable_and_not_in_run(self):
        state = _make_state(_make_entry(first_flagged_sha="ghostSha"))
        with mock.patch.object(d, "is_sha_reachable", return_value=False), \
             mock.patch.object(d, "method_last_modified_in_range", return_value=None):
            d.reconcile_state(state, set(), "headN", "baseN", ".")
        self.assertEqual(state["tests"][0]["status"], "dropped_force_push")

    def test_keeps_when_anchor_unreachable_but_still_flaking(self):
        state = _make_state(_make_entry(first_flagged_sha="ghostSha", key="p.C.m"))
        with mock.patch.object(d, "is_sha_reachable", return_value=False), \
             mock.patch.object(d, "method_last_modified_in_range", return_value=None):
            d.reconcile_state(state, {"p.C.m"}, "headN", "baseN", ".")
        # The reconcile keeps it active (anchor unreachable check only fires when
        # test isn't flaking); the merge step will then reset its counter.
        self.assertEqual(state["tests"][0]["status"], "active")

    def test_q4c_counter_resets_when_method_modified_again(self):
        entry = _make_entry(
            method_last_modified_sha="fix1",
            clean_runs_since_modified=2,
            _prev_last_mod="fix1",
        )
        state = _make_state(entry)
        with mock.patch.object(d, "is_sha_reachable", return_value=True), \
             mock.patch.object(d, "method_last_modified_in_range",
                                return_value="fix2"):
            d.reconcile_state(state, set(), "headN", "baseN", ".")
        self.assertEqual(state["tests"][0]["method_last_modified_sha"], "fix2")
        self.assertEqual(state["tests"][0]["clean_runs_since_modified"], 0)

    def test_q4c_counter_preserved_when_modification_unchanged(self):
        entry = _make_entry(
            method_last_modified_sha="fix1",
            clean_runs_since_modified=2,
            _prev_last_mod="fix1",
        )
        state = _make_state(entry)
        with mock.patch.object(d, "is_sha_reachable", return_value=True), \
             mock.patch.object(d, "method_last_modified_in_range",
                                return_value="fix1"):
            d.reconcile_state(state, set(), "headN", "baseN", ".")
        self.assertEqual(state["tests"][0]["clean_runs_since_modified"], 2)


# ---------------------------------------------------------------------------
# Comment rendering
# ---------------------------------------------------------------------------

class TestRenderComment(unittest.TestCase):
    def test_active_comment_contains_state_block(self):
        state = _make_state(_make_entry(
            method_last_modified_sha="abcdef0",
            clean_runs_since_modified=1,
        ))
        body = d.render_comment(state, "art-name")
        self.assertIn("⚠️ New Flaky Tests Detected", body)
        self.assertIn("Clean re-runs since fix: 1 / 3", body)
        self.assertIn("Method last modified at: `abcdef0`", body)
        self.assertIn("art-name", body)

    def test_all_clear_template(self):
        state = _make_state(_make_entry(
            status="cleared_via_fix",
            method_last_modified_sha="fix1",
            cleared_at="t",
        ))
        body = d.render_comment(state, "art-name")
        self.assertIn("✅ Cleared", body)
        self.assertNotIn("⚠️", body)
        self.assertIn("<details>", body)

    def test_mixed_active_and_cleared(self):
        state = _make_state(
            _make_entry(key="t1"),
            _make_entry(key="t2", status="cleared_via_bypass",
                         cleared_at="t"),
        )
        body = d.render_comment(state, "art-name")
        self.assertIn("⚠️", body)
        self.assertIn("1 cleared test(s) (history)", body)
        self.assertIn("cleared via `ci:flaky-test-bypass`", body)


class TestMergeBase(unittest.TestCase):
    def test_prefers_base_sha_over_ref_names(self):
        calls = []

        def fake_run_git(args, repo_root):
            calls.append(args)
            # Only the base_sha candidate resolves; ref names fail (as in a
            # PR merge-ref checkout with no local main/origin/main).
            if args[:2] == ["merge-base", "baseSha123"]:
                return 0, "mergeBaseSha\n", ""
            return 1, "", "fatal: Not a valid object name"

        with mock.patch.object(d, "_run_git", side_effect=fake_run_git):
            result = d.get_merge_base("headSha", "main", ".", "baseSha123")

        self.assertEqual(result, "mergeBaseSha")
        # base_sha must be the first candidate tried.
        self.assertEqual(calls[0], ["merge-base", "baseSha123", "headSha"])

    def test_skips_empty_candidates_and_falls_back_to_ref(self):
        def fake_run_git(args, repo_root):
            if args[:2] == ["merge-base", "origin/main"]:
                return 0, "fromOriginMain\n", ""
            return 1, "", ""

        with mock.patch.object(d, "_run_git", side_effect=fake_run_git):
            # Empty base_sha is skipped, resolution falls through to origin/main.
            result = d.get_merge_base("headSha", "main", ".", "")

        self.assertEqual(result, "fromOriginMain")


# ---------------------------------------------------------------------------
# Touch-check filter
# ---------------------------------------------------------------------------

def _make_test(package: str, class_name: str = "SomeIT",
               method: str = "shouldWork") -> dict:
    return {"packageName": package, "className": class_name,
            "methodName": method, "testSuiteName": class_name}


class TestGetPrChangedPaths(unittest.TestCase):
    def _fake_merge_base(self, head_sha, base_ref, repo_root, base_sha=None):
        return "mergeBase123"

    def test_uses_merge_base_sha_not_origin_ref(self):
        """Diff must be against the resolved merge-base SHA, not origin/<ref>."""
        diff_calls = []

        def fake_run_git(args, repo_root):
            diff_calls.append(args)
            return 0, "src/main/java/io/camunda/Foo.java\n", ""

        with mock.patch.object(d, "get_merge_base", side_effect=self._fake_merge_base), \
             mock.patch.object(d, "_run_git", side_effect=fake_run_git):
            pkgs, files, avail = d.get_pr_changed_paths("main", "headSha", ".", "baseSha456")

        self.assertTrue(avail)
        self.assertIn("mergeBase123...headSha", diff_calls[0][2])
        self.assertNotIn("origin/", diff_calls[0][2])

    def test_disabled_when_merge_base_unresolvable(self):
        with mock.patch.object(d, "get_merge_base", return_value=None):
            pkgs, files, avail = d.get_pr_changed_paths("main", "headSha", ".")

        self.assertFalse(avail)
        self.assertEqual(pkgs, set())

    def test_yaml_only_pr_returns_empty_pkgs_available_true(self):
        def fake_run_git(args, repo_root):
            return 0, ".github/workflows/ci.yml\naction.yml\n", ""

        with mock.patch.object(d, "get_merge_base", side_effect=self._fake_merge_base), \
             mock.patch.object(d, "_run_git", side_effect=fake_run_git):
            pkgs, files, avail = d.get_pr_changed_paths("main", "headSha", ".")

        self.assertTrue(avail)
        self.assertEqual(pkgs, set())
        self.assertEqual(files, set())

    def test_git_diff_failure_returns_available_false(self):
        def fake_run_git(args, repo_root):
            return 1, "", "fatal: not a git repo"

        with mock.patch.object(d, "get_merge_base", side_effect=self._fake_merge_base), \
             mock.patch.object(d, "_run_git", side_effect=fake_run_git):
            pkgs, files, avail = d.get_pr_changed_paths("main", "headSha", ".")

        self.assertFalse(avail)


class TestFilterByTouchCheck(unittest.TestCase):
    def _run(self, tests, changed_pkgs, changed_files=None, blank_fqns=None,
             available=True):
        return d.filter_by_touch_check(
            tests,
            set(changed_pkgs),
            set(changed_files or []),
            set(blank_fqns or []),
            available,
        )

    def test_skips_filter_when_unavailable(self):
        tests = [_make_test("io.camunda.unrelated")]
        result = self._run(tests, set(), available=False)
        self.assertEqual(result, tests)

    def test_yaml_only_pr_suppresses_unrelated_package(self):
        """YAML-only PR: changed_pkgs empty + available=True → suppress."""
        tests = [_make_test("io.camunda.zeebe.backup.gcs")]
        result = self._run(tests, changed_pkgs=[], available=True)
        self.assertEqual(result, [])

    def test_unrelated_package_suppressed(self):
        tests = [_make_test("io.camunda.zeebe.backup.gcs")]
        result = self._run(tests, changed_pkgs=["io/camunda/zeebe/agent"])
        self.assertEqual(result, [])

    def test_related_package_kept(self):
        tests = [_make_test("io.camunda.zeebe.backup.gcs")]
        result = self._run(tests, changed_pkgs=["io/camunda/zeebe/backup/gcs"])
        self.assertEqual(result, tests)

    def test_blank_class_suppressed_when_file_not_in_diff(self):
        test = _make_test("io.camunda.zeebe.backup.gcs", "GcsBackupIT")
        result = self._run(
            [test],
            changed_pkgs=["io/camunda/zeebe/backup/gcs"],
            changed_files=[],
            blank_fqns=["io.camunda.zeebe.backup.gcs.GcsBackupIT"],
        )
        self.assertEqual(result, [])

    def test_blank_class_kept_when_test_file_in_diff(self):
        test = _make_test("io.camunda.zeebe.backup.gcs", "GcsBackupIT")
        result = self._run(
            [test],
            changed_pkgs=["io/camunda/zeebe/backup/gcs"],
            changed_files=["GcsBackupIT.java"],
            blank_fqns=["io.camunda.zeebe.backup.gcs.GcsBackupIT"],
        )
        self.assertEqual(result, [test])

    def test_unparseable_package_is_kept(self):
        """Tests with empty packageName must not be suppressed — no signal."""
        test = _make_test("")
        result = self._run([test], changed_pkgs=[], available=True)
        self.assertEqual(result, [test])


if __name__ == "__main__":
    unittest.main()
