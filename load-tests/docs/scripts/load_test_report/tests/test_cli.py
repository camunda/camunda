import io
import sys
from pathlib import Path
from unittest import mock

import pytest

import load_test_report
from load_test_report.cli import build_parser
from load_test_report.cli import parse_args
from load_test_report.cli import run
from load_test_report.errors import ReportError
from load_test_report.prometheus import auth_headers

PROJECT_DIR = Path(load_test_report.__file__).resolve().parent


def test_should_build_parser() -> None:
    parser = build_parser()

    assert parser.prog == "load-test-report"


def test_should_return_argparse_exit_code_for_help() -> None:
    assert run(["--help"]) == 0


def test_should_return_error_for_missing_namespace() -> None:
    stderr = io.StringIO()

    with mock.patch.object(sys, "stderr", stderr):
        exit_code = run([])

    assert exit_code == 2
    assert "the following arguments are required: namespace" in stderr.getvalue()


def test_should_parse_auth_flags(tmp_path: Path) -> None:
    queries_file = tmp_path / "queries.yaml"
    queries_file.write_text("queries: []", encoding="utf-8")

    options = parse_args(
        [
            "c8-ck-test",
            "--user",
            "user",
            "--password",
            "pass",
            "--queries",
            str(queries_file),
        ]
    )

    assert options.basic_auth_user == "user"
    assert options.basic_auth_password == "pass"
    assert options.queries_file == queries_file


def test_should_derive_duration_from_start_and_end(tmp_path: Path) -> None:
    queries_file = tmp_path / "queries.yaml"
    queries_file.write_text("queries: []", encoding="utf-8")

    options = parse_args(
        [
            "c8-ck-test",
            "--start",
            "2026-08-14T10:00:00Z",
            "--end",
            "2026-08-14T10:30:00Z",
            "--queries",
            str(queries_file),
        ]
    )

    assert options.duration_seconds == 1800
    assert options.time_anchor == "2026-08-14T10:30:00Z"
    assert options.start_label == "2026-08-14T10:00:00Z"
    assert options.end_label == "2026-08-14T10:30:00Z"


def test_should_normalize_timezone_less_time_window() -> None:
    options = parse_args(
        [
            "c8-ck-test",
            "--start",
            "2026-08-14T10:00:00",
            "--end",
            "2026-08-14T10:30:00",
        ]
    )

    assert options.time_anchor == "2026-08-14T10:30:00Z"


def test_should_accept_composite_prometheus_durations() -> None:
    options = parse_args(["c8-ck-test", "--rate-interval", "1h30m"])

    assert options.rate_interval == "1h30m"


@pytest.mark.parametrize("duration", ["1m500ms", "1s500ms", "1h30m500ms"])
def test_should_accept_composite_prometheus_durations_with_milliseconds(duration: str) -> None:
    options = parse_args(["c8-ck-test", "--rate-interval", duration])

    assert options.rate_interval == duration


def test_should_reject_out_of_order_prometheus_durations() -> None:
    with pytest.raises(SystemExit):
        parse_args(["c8-ck-test", "--rate-interval", "1m1h"])

    with pytest.raises(SystemExit):
        parse_args(["c8-ck-test", "--rate-interval", "1h1h"])

    with pytest.raises(SystemExit):
        parse_args(["c8-ck-test", "--rate-interval", "1ms1m"])

    with pytest.raises(SystemExit):
        parse_args(["c8-ck-test", "--rate-interval", "1ms1ms"])


def test_should_reject_unrepresentable_timestamp() -> None:
    with pytest.raises(SystemExit):
        parse_args(["c8-ck-test", "--at", "999999999999999999999"])


def test_should_reject_fractional_timestamp() -> None:
    with pytest.raises(SystemExit):
        parse_args(["c8-ck-test", "--at", "2026-08-14T10:00:00.5Z"])


def test_should_use_packaged_default_queries() -> None:
    options = parse_args(["c8-ck-test"])

    assert options.queries_file == PROJECT_DIR / "report-queries.yaml"


def test_should_use_packaged_queries_file_by_path(tmp_path: Path) -> None:
    options = parse_args(["c8-ck-test", "--queries", "report-queries-stable-87.yaml"])

    assert options.queries_file == PROJECT_DIR / "report-queries-stable-87.yaml"


def test_should_reject_missing_queries_file() -> None:
    with pytest.raises(SystemExit):
        parse_args(["c8-ck-test", "--queries", "daily"])


def test_should_build_basic_auth_header() -> None:
    headers = auth_headers("user", "pass")

    assert headers["Authorization"] == "Basic dXNlcjpwYXNz"


def test_should_reject_incomplete_basic_auth() -> None:
    with pytest.raises(ReportError, match="--user and --password"):
        auth_headers("user", "")


def test_should_reject_unrepresentable_reporting_window() -> None:
    with pytest.raises(ReportError, match="reporting window is outside the supported timestamp range"):
        parse_args(
            [
                "c8-ck-test",
                "--at",
                "0",
                "--duration-seconds",
                "999999999999999999999",
            ]
        )
