"""Unit tests for resolve-daily.py

Run with:
    pytest .github/scripts/test_resolve_daily.py
"""

import importlib.util
import os
import sys
from datetime import date, datetime, timezone
from unittest import mock

import pytest

# Load the hyphen-named module via importlib and register it so mock.patch works.
_SCRIPT = os.path.join(os.path.dirname(__file__), "resolve-daily.py")
_spec = importlib.util.spec_from_file_location("resolve_daily", _SCRIPT)
r = importlib.util.module_from_spec(_spec)
sys.modules["resolve_daily"] = r
_spec.loader.exec_module(r)


def _utc(year: int, month: int, day: int, hour: int = 12, minute: int = 0) -> datetime:
    return datetime(year, month, day, hour, minute, tzinfo=timezone.utc)


class TestIsWeekday:
    @pytest.mark.parametrize("day", [
        date(2026, 6, 29), date(2026, 6, 30), date(2026, 7, 1),
        date(2026, 7, 2), date(2026, 7, 3),
    ])
    def test_monday_through_friday_are_weekdays(self, day):
        assert r.is_weekday(day)

    def test_saturday_and_sunday_are_not_weekdays(self):
        assert not r.is_weekday(date(2026, 7, 4))  # Saturday
        assert not r.is_weekday(date(2026, 7, 5))  # Sunday


class TestPreviousBusinessDay:
    def test_monday_gives_friday(self):
        # Must skip Saturday and Sunday
        assert r.previous_business_day(date(2026, 6, 29)) == date(2026, 6, 26)

    def test_tuesday_gives_monday(self):
        assert r.previous_business_day(date(2026, 6, 30)) == date(2026, 6, 29)

    def test_friday_gives_thursday(self):
        assert r.previous_business_day(date(2026, 7, 3)) == date(2026, 7, 2)

    def test_saturday_gives_friday(self):
        assert r.previous_business_day(date(2026, 7, 4)) == date(2026, 7, 3)

    def test_sunday_gives_friday(self):
        assert r.previous_business_day(date(2026, 7, 5)) == date(2026, 7, 3)


class TestCandidateDates:

    # --- start date selection ---

    def test_weekday_at_or_after_cutoff_starts_today(self):
        # given: Wednesday 2026-07-01 exactly at the availability cutoff
        now = _utc(2026, 7, 1, hour=r.TODAY_AVAILABLE_UTC_HOUR)
        # when / then
        assert r.candidate_dates(now)[0] == date(2026, 7, 1)

    def test_weekday_before_cutoff_falls_back_to_previous_business_day(self):
        # given: Wednesday 2026-07-01 at 05:59 UTC — daily not finished yet
        now = _utc(2026, 7, 1, hour=r.TODAY_AVAILABLE_UTC_HOUR - 1)
        # when / then
        assert r.candidate_dates(now)[0] == date(2026, 6, 30)  # Tuesday

    def test_saturday_falls_back_to_friday(self):
        # given: Saturday — no daily runs on weekends
        now = _utc(2026, 7, 4, hour=12)
        # when / then
        assert r.candidate_dates(now)[0] == date(2026, 7, 3)  # Friday

    def test_sunday_falls_back_to_friday(self):
        # given: Sunday — no daily runs on weekends
        now = _utc(2026, 7, 5, hour=12)
        # when / then
        assert r.candidate_dates(now)[0] == date(2026, 7, 3)  # Friday

    def test_monday_before_cutoff_falls_back_to_friday(self):
        # given: Monday 05:00 UTC — yesterday was Sunday, so previous business day is Friday
        now = _utc(2026, 6, 29, hour=5)
        # when / then
        assert r.candidate_dates(now)[0] == date(2026, 6, 26)  # Friday

    # --- list shape ---

    def test_returns_exactly_seven_dates(self):
        # given
        now = _utc(2026, 7, 1, hour=12)
        # when
        dates = r.candidate_dates(now)
        # then
        assert len(dates) == r.LOOKBACK_BUSINESS_DAYS

    def test_all_dates_are_weekdays(self):
        # given
        now = _utc(2026, 7, 1, hour=12)
        # when
        dates = r.candidate_dates(now)
        # then
        assert all(r.is_weekday(d) for d in dates)

    def test_dates_are_newest_first(self):
        # given
        now = _utc(2026, 7, 1, hour=12)
        # when
        dates = r.candidate_dates(now)
        # then
        assert all(dates[i] > dates[i + 1] for i in range(len(dates) - 1))


class TestSoakStartedAt:
    def test_returns_timestamp_on_success(self):
        # given: gh returns a clean timestamp with trailing newline
        with mock.patch.object(r, "gh", return_value="2026-07-01T02:45:00Z\n"):
            # when
            result = r.soak_started_at("123")
        # then
        assert result == "2026-07-01T02:45:00Z"

    def test_returns_none_when_gh_fails(self):
        # given: gh CLI exits non-zero
        with mock.patch.object(r, "gh", return_value=None):
            # when
            result = r.soak_started_at("123")
        # then
        assert result is None

    def test_returns_none_when_empty_output(self):
        # given: gh succeeds but returns empty output (job not yet started)
        with mock.patch.object(r, "gh", return_value=""):
            # when
            result = r.soak_started_at("123")
        # then
        assert result is None

    def test_skips_null_lines_and_returns_first_real_value(self):
        # given: jq emits "null" for non-matching jobs before the soak job
        with mock.patch.object(r, "gh", return_value="null\n2026-07-01T02:45:00Z\n"):
            # when
            result = r.soak_started_at("123")
        # then: null line skipped, real value returned
        assert result == "2026-07-01T02:45:00Z"

    def test_returns_none_when_all_lines_are_null(self):
        # given: soak job is absent from the run (e.g. setup failed before it started)
        with mock.patch.object(r, "gh", return_value="null\nnull\n"):
            # when
            result = r.soak_started_at("123")
        # then
        assert result is None


class TestBenchmarkFromArtifacts:
    def test_strips_artifact_prefix_correctly(self):
        # given
        artifact = "daily-load-test-metrics-medic-daily-2026-07-01-abc1234-test"
        with mock.patch.object(r, "gh", return_value=artifact + "\n"):
            # when
            result = r.benchmark_from_artifacts("123")
        # then: "daily-load-test-metrics-" prefix is stripped
        assert result == "medic-daily-2026-07-01-abc1234-test"

    def test_returns_none_when_gh_fails(self):
        # given: gh CLI exits non-zero
        with mock.patch.object(r, "gh", return_value=None):
            # when
            result = r.benchmark_from_artifacts("123")
        # then
        assert result is None

    def test_returns_none_when_output_is_empty(self):
        # given: no matching artifact found (expired or never uploaded)
        with mock.patch.object(r, "gh", return_value=""):
            # when
            result = r.benchmark_from_artifacts("123")
        # then
        assert result is None

    def test_returns_first_artifact_when_multiple_lines(self):
        # given: two matching artifacts (gRPC + REST runs)
        output = (
            "daily-load-test-metrics-medic-daily-2026-07-01-abc1234-test\n"
            "daily-load-test-metrics-medic-daily-2026-07-01-abc1234-test-rest\n"
        )
        with mock.patch.object(r, "gh", return_value=output):
            # when
            result = r.benchmark_from_artifacts("123")
        # then: first artifact (gRPC namespace) is returned
        assert result == "medic-daily-2026-07-01-abc1234-test"


class TestResolve:

    _SOAK_START = "2026-07-01T02:45:00Z"
    _BENCHMARK = "medic-daily-2026-07-01-abc1234-test"
    _NOW = _utc(2026, 7, 1, hour=12)

    def test_anchor_is_soak_start_plus_warmup_plus_window(self):
        """anchor = soak.started_at + WARMUP_SECONDS(900) + METRICS_WINDOW_SECONDS(1800) = +45min"""
        # given: a completed daily run with a known soak start time
        with mock.patch.object(r, "find_run_id", return_value="42"), \
             mock.patch.object(r, "soak_started_at", return_value=self._SOAK_START), \
             mock.patch.object(r, "benchmark_from_artifacts", return_value=self._BENCHMARK):
            # when
            result = r.resolve(self._NOW)
        # then: anchor = 02:45:00 + 900s (warmup) + 1800s (window) = 03:30:00
        assert result is not None
        namespace, anchor = result
        assert anchor == "2026-07-01T03:30:00Z"
        assert namespace == f"c8-{self._BENCHMARK}"

    def test_returns_none_when_no_run_found_for_any_candidate(self):
        # given: no completed run exists for any candidate date
        with mock.patch.object(r, "find_run_id", return_value=None):
            # when / then
            assert r.resolve(self._NOW) is None

    def test_skips_candidate_missing_soak_time(self):
        # given: run found but soak job has no started_at (e.g. job was cancelled)
        with mock.patch.object(r, "find_run_id", return_value="42"), \
             mock.patch.object(r, "soak_started_at", return_value=None), \
             mock.patch.object(r, "benchmark_from_artifacts", return_value=self._BENCHMARK):
            # when / then
            assert r.resolve(self._NOW) is None

    def test_skips_candidate_missing_artifact(self):
        # given: run found but metrics artifact has expired or was never uploaded
        with mock.patch.object(r, "find_run_id", return_value="42"), \
             mock.patch.object(r, "soak_started_at", return_value=self._SOAK_START), \
             mock.patch.object(r, "benchmark_from_artifacts", return_value=None):
            # when / then
            assert r.resolve(self._NOW) is None

    def test_skips_candidate_with_unparseable_timestamp(self):
        # given: soak.started_at is present but not a valid ISO-8601 timestamp
        with mock.patch.object(r, "find_run_id", return_value="42"), \
             mock.patch.object(r, "soak_started_at", return_value="not-a-timestamp"), \
             mock.patch.object(r, "benchmark_from_artifacts", return_value=self._BENCHMARK):
            # when / then
            assert r.resolve(self._NOW) is None

    def test_falls_back_to_next_candidate_on_no_run(self):
        """First candidate has no run ID; resolver must try the next one."""
        # given: only the second candidate date has a completed run
        calls = []

        def find_side_effect(d):
            calls.append(d)
            return None if len(calls) == 1 else "42"

        with mock.patch.object(r, "find_run_id", side_effect=find_side_effect), \
             mock.patch.object(r, "soak_started_at", return_value=self._SOAK_START), \
             mock.patch.object(r, "benchmark_from_artifacts", return_value=self._BENCHMARK):
            # when
            result = r.resolve(self._NOW)
        # then: succeeded after falling back to the second candidate
        assert result is not None
        assert len(calls) == 2

    def test_falls_back_to_next_candidate_on_missing_artifact(self):
        """First candidate has a run but no artifact; resolver must try the next one."""
        # given: first candidate's run has no metrics artifact, second does
        calls = []

        def artifact_side_effect(run_id):
            calls.append(run_id)
            return None if len(calls) == 1 else self._BENCHMARK

        with mock.patch.object(r, "find_run_id", return_value="42"), \
             mock.patch.object(r, "soak_started_at", return_value=self._SOAK_START), \
             mock.patch.object(r, "benchmark_from_artifacts", side_effect=artifact_side_effect):
            # when
            result = r.resolve(self._NOW)
        # then: succeeded after falling back to the second candidate
        assert result is not None
        assert len(calls) == 2
