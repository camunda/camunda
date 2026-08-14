"""Unit tests for post-load-test-results-to-sheet.py

Run with:
    pytest .github/scripts/test_post_load_test_results_to_sheet.py
"""

import importlib.util
import os
import sys

# Load the hyphen-named module via importlib and register it so mock.patch works.
_SCRIPT = os.path.join(os.path.dirname(__file__), "post-load-test-results-to-sheet.py")
_spec = importlib.util.spec_from_file_location("post_load_test_results_to_sheet", _SCRIPT)
s = importlib.util.module_from_spec(_spec)
sys.modules["post_load_test_results_to_sheet"] = s
_spec.loader.exec_module(s)

QUERIES = [
    {"name": "process-instances-started"},
    {"name": "throughput-per-second"},
]


class TestParseBenchmark:
    def test_extracts_date_and_short_sha(self):
        # given
        benchmark = "medic-daily-2026-08-12-abc1234-test"
        # when
        date, sha = s.parse_benchmark(benchmark)
        # then
        assert date == "2026-08-12"
        assert sha == "abc1234"

    def test_returns_empty_strings_when_name_is_unparseable(self):
        # given: a name that doesn't match the medic-daily-<date>-<sha>-test shape
        # when / then
        assert s.parse_benchmark("not-a-benchmark-name") == ("", "")


class TestMetricValue:
    def test_returns_float_on_success(self):
        # given
        results = {"throughput-per-second": "123.4"}
        # when / then
        assert s.metric_value("success", results, "throughput-per-second") == 123.4

    def test_returns_blank_when_status_is_failed(self):
        # given: metric present but the run itself is marked failed
        results = {"throughput-per-second": "123.4"}
        # when / then
        assert s.metric_value("failed", results, "throughput-per-second") == ""

    def test_returns_blank_when_metric_missing(self):
        # given
        results = {}
        # when / then
        assert s.metric_value("success", results, "throughput-per-second") == ""

    def test_returns_blank_when_value_is_not_numeric(self):
        # given: malformed upstream value
        results = {"throughput-per-second": "n/a"}
        # when / then
        assert s.metric_value("success", results, "throughput-per-second") == ""


class TestBuildRows:
    def test_builds_one_row_per_protocol_on_full_success(self):
        # given
        grpc_results = {"process-instances-started": "1000", "throughput-per-second": "10.5"}
        rest_results = {"process-instances-started": "900", "throughput-per-second": "9.1"}
        # when
        rows = s.build_rows(
            "medic-daily-2026-08-12-abc1234-test",
            "https://github.com/camunda/camunda/actions/runs/42",
            "success", grpc_results,
            "success", rest_results,
            QUERIES,
        )
        # then
        assert rows == [
            ["2026-08-12", "grpc", "success", "medic-daily-2026-08-12-abc1234-test", "abc1234",
             "https://github.com/camunda/camunda/actions/runs/42", 1000.0, 10.5],
            ["2026-08-12", "rest", "success", "medic-daily-2026-08-12-abc1234-test", "abc1234",
             "https://github.com/camunda/camunda/actions/runs/42", 900.0, 9.1],
        ]

    def test_failed_protocol_gets_blank_metrics(self):
        # given: rest side crashed before metrics were collected
        # when
        rows = s.build_rows(
            "medic-daily-2026-08-12-abc1234-test",
            "https://github.com/camunda/camunda/actions/runs/42",
            "success", {"process-instances-started": "1000", "throughput-per-second": "10.5"},
            "failed", {},
            QUERIES,
        )
        # then
        grpc_row, rest_row = rows
        assert grpc_row[2] == "success"
        assert grpc_row[-2:] == [1000.0, 10.5]
        assert rest_row[2] == "failed"
        assert rest_row[-2:] == ["", ""]

    def test_both_protocols_failed_gets_blank_metrics_for_both(self):
        # given: hard failure before either side collected metrics
        # when
        rows = s.build_rows(
            "medic-daily-2026-08-12-abc1234-test",
            "https://github.com/camunda/camunda/actions/runs/42",
            "failed", {},
            "failed", {},
            QUERIES,
        )
        # then
        assert all(row[2] == "failed" for row in rows)
        assert all(row[-2:] == ["", ""] for row in rows)
