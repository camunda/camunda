"""Unit tests for render-comparison.py.

Run with:
    pytest .github/scripts/test_render_comparison.py
"""

import importlib.util
import json
import os
import sys
from datetime import datetime, timedelta

# Load the hyphen-named module via importlib and register it so mock.patch works.
_SCRIPT = os.path.join(os.path.dirname(__file__), "render-comparison.py")
_spec = importlib.util.spec_from_file_location("render_comparison", _SCRIPT)
rc = importlib.util.module_from_spec(_spec)
sys.modules["render_comparison"] = rc
_spec.loader.exec_module(rc)


class TestDailyWindowStart:
    def test_normal_case_subtracts_duration(self):
        # given: anchor is the end of the 1800s window
        # when
        result = rc.daily_window_start("2026-07-01T03:30:00Z", 1800)
        # then: window start = anchor - 1800s
        assert result == "2026-07-01T03:00:00Z"

    def test_empty_string_returns_none(self):
        # given / when / then
        assert rc.daily_window_start("", 1800) is None

    def test_invalid_timestamp_returns_none(self):
        # given / when / then
        assert rc.daily_window_start("not-a-timestamp", 1800) is None

    def test_crosses_midnight_backwards(self):
        # given: anchor is 15 min past midnight; window extends into the previous day
        # when
        result = rc.daily_window_start("2026-07-01T00:15:00Z", 1800)
        # then
        assert result == "2026-06-30T23:45:00Z"

    def test_zero_duration_returns_same_time(self):
        # given / when
        result = rc.daily_window_start("2026-07-01T03:30:00Z", 0)
        # then
        assert result == "2026-07-01T03:30:00Z"

    def test_matches_full_pr_anchor_math(self):
        # given: soak.started_at = 02:45:00, with WARMUP=900s and WINDOW=1800s.
        #        resolve-daily.py sets anchor = soak_start + warmup + window = 03:30:00.
        soak_start = "2026-07-01T02:45:00Z"
        warmup_s = 900
        window_s = 1800
        start_dt = datetime.fromisoformat(soak_start.replace("Z", "+00:00"))
        anchor = (start_dt + timedelta(seconds=warmup_s + window_s)).strftime("%Y-%m-%dT%H:%M:%SZ")
        # when: recover the window start from the anchor
        window_start = rc.daily_window_start(anchor, window_s)
        # then: window_start = soak_start + warmup (the first steady-state second)
        expected = (start_dt + timedelta(seconds=warmup_s)).strftime("%Y-%m-%dT%H:%M:%SZ")
        assert window_start == expected


class TestOptimalMetrics:
    _OPTIMAL = os.path.join(
        os.path.dirname(__file__), "..", "..", "load-tests", "docs", "scripts", "optimal.json"
    )

    def _load(self):
        with open(self._OPTIMAL, encoding="utf-8") as f:
            return json.load(f)

    def test_storage_without_override_uses_the_base_values(self):
        # given
        optimal = self._load()
        # when
        metrics = rc.optimal_metrics(optimal, "elasticsearch")
        # then
        assert metrics == optimal["metrics"]

    def test_no_secondary_storage_expects_the_higher_rate(self):
        # when
        metrics = rc.optimal_metrics(self._load(), "none")
        # then: 500 PI/s over the 1800 s window, as the max scenario starts without storage
        assert metrics["completed-pi-rate-per-second"]["expected"] == 500
        assert metrics["process-instances-completed"]["expected"] == 900_000
        assert metrics["data-availability"]["expected"] is None

    def test_override_keeps_the_base_tolerance(self):
        # when
        metrics = rc.optimal_metrics(self._load(), "none")
        # then
        assert metrics["throughput-per-second"]["tolerance-percent"] == 5


class TestReadResults:
    def test_reads_metrics_from_file(self, tmp_path):
        # given
        f = tmp_path / "results.json"
        f.write_text(json.dumps({"a": 1.5}))
        # when / then
        assert rc.read_results(str(f), "Current") == {"a": 1.5}

    def test_missing_file_yields_empty_results(self, tmp_path):
        # given / when / then
        assert rc.read_results(str(tmp_path / "absent.json"), "Daily") == {}

    def test_unset_path_yields_empty_results(self):
        # given / when / then
        assert rc.read_results(None, "Daily") == {}

    def test_load_inputs_reads_both_result_files(self, tmp_path, monkeypatch):
        # given
        pr = tmp_path / "pr.json"
        pr.write_text(json.dumps({"a": 1}))
        queries = tmp_path / "queries.json"
        queries.write_text(json.dumps({"queries": []}))
        optimal = tmp_path / "optimal.json"
        optimal.write_text(json.dumps({}))
        monkeypatch.setenv("QUERIES_JSON_PATH", str(queries))
        monkeypatch.setenv("OPTIMAL_JSON_PATH", str(optimal))
        monkeypatch.setenv("PR_RESULTS_FILE", str(pr))
        monkeypatch.setenv("DAILY_RESULTS_FILE", str(tmp_path / "absent.json"))
        # when
        inputs = rc.load_inputs()
        # then: present file is parsed, absent file degrades to empty
        assert inputs["results"] == {"a": 1}
        assert inputs["daily_results"] == {}

