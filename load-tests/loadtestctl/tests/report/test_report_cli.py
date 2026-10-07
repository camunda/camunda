import io
import sys
from datetime import UTC
from datetime import datetime
from datetime import timedelta
from pathlib import Path
from unittest import mock

import pytest

import loadtestctl.report
from loadtestctl.report.cli import build_parser
from loadtestctl.report.cli import parse_args
from loadtestctl.report.cli import run
from loadtestctl.report.errors import ReportError
from loadtestctl.report.prometheus import auth_headers

PROJECT_DIR = Path(loadtestctl.report.__file__).resolve().parent


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


def test_should_derive_end_from_start_and_duration(tmp_path: Path) -> None:
    queries_file = tmp_path / "queries.yaml"
    queries_file.write_text("queries: []", encoding="utf-8")

    options = parse_args(
        [
            "c8-ck-test",
            "--start",
            "2026-08-14T10:00:00Z",
            "--duration-seconds",
            "1800",
            "--queries",
            str(queries_file),
        ]
    )

    assert options.duration_seconds == 1800
    assert options.time_anchor == "2026-08-14T10:30:00Z"
    assert options.start_label == "2026-08-14T10:00:00Z"
    assert options.end_label == "2026-08-14T10:30:00Z"


def test_should_default_to_window_ending_now() -> None:
    before = datetime.now(UTC).replace(microsecond=0)

    options = parse_args(["c8-ck-test", "--duration-seconds", "1800"])

    after = datetime.now(UTC)
    start = datetime.strptime(options.start_label, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=UTC)
    end = datetime.strptime(options.end_label, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=UTC)
    assert options.duration_seconds == 1800
    assert end - start == timedelta(seconds=1800)
    assert before <= end <= after
    assert options.time_anchor == options.end_label


def test_should_reject_window_ending_in_the_future() -> None:
    with pytest.raises(ReportError, match="must not end in the future"):
        parse_args(["c8-ck-test", "--start", "2999-01-01T00:00:00Z"])


def test_should_normalize_timezone_less_time_window() -> None:
    options = parse_args(
        [
            "c8-ck-test",
            "--start",
            "2026-08-14T10:00:00",
            "--duration-seconds",
            "1800",
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
        parse_args(["c8-ck-test", "--start", "999999999999999999999"])


def test_should_reject_fractional_timestamp() -> None:
    with pytest.raises(SystemExit):
        parse_args(["c8-ck-test", "--start", "2026-08-14T10:00:00.5Z"])


def test_should_reject_wrong_format_timestamp() -> None:
    with pytest.raises(SystemExit):
        parse_args(["c8-ck-test", "--start", "2026-08-XXT10:00:00.5Z"])


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


def test_should_reject_unrepresentable_reporting_window_with_no_start() -> None:
    with pytest.raises(ReportError, match="reporting window is outside the supported timestamp range"):
        parse_args(
            [
                "c8-ck-test",
                "--duration-seconds",
                "999999999999999999999",
            ]
        )


def test_should_reject_unrepresentable_reporting_window() -> None:
    with pytest.raises(ReportError, match="reporting window is outside the supported timestamp range"):
        parse_args(
            [
                "c8-ck-test",
                "--start",
                "0",
                "--duration-seconds",
                "999999999999999999999",
            ]
        )


def test_should_reject_negative_duration(capsys: pytest.CaptureFixture[str]) -> None:
    with pytest.raises(SystemExit):
        parse_args(
            [
                "c8-ck-test",
                "--start",
                "2026-08-14T10:00:00",
                "--duration-seconds",
                "-1",
            ]
        )

    assert "'-1' must be a positive integer" in capsys.readouterr().err


def test_should_reject_too_long_namespace(capsys: pytest.CaptureFixture[str]) -> None:
    with pytest.raises(SystemExit):
        parse_args(
            [
                "a" * 64,
                "--start",
                "2026-08-14T10:00:00",
                "--duration-seconds",
                "123",
            ]
        )

    assert "(max 63 characters; lowercase alphanumeric or '-', and must start and end " in capsys.readouterr().err


def test_should_reject_invalid_namespace(capsys: pytest.CaptureFixture[str]) -> None:
    with pytest.raises(SystemExit):
        parse_args(
            [
                "Invalid_Namespace",
                "--start",
                "2026-08-14T10:00:00",
                "--duration-seconds",
                "123",
            ]
        )

    assert "(max 63 characters; lowercase alphanumeric or '-', and must start and end " in capsys.readouterr().err
