from datetime import UTC
from datetime import datetime
from datetime import timedelta
from pathlib import Path

import pytest

import loadtestctl.report
from loadtestctl.report.errors import ReportError
from loadtestctl.report.prometheus import auth_headers

from .helpers import parse_args
from .helpers import run

PROJECT_DIR = Path(loadtestctl.report.__file__).resolve().parent


def test_should_parse_namespace_argument() -> None:
    options = parse_args(["c8-ck-test"])

    assert options.namespace == "c8-ck-test"


def test_should_return_zero_exit_code_for_help() -> None:
    assert run(["--help"]) == 0


def test_should_return_error_for_missing_namespace(capsys: pytest.CaptureFixture[str]) -> None:
    exit_code = run([])

    assert exit_code == 2
    assert "Missing argument 'NAMESPACE'" in capsys.readouterr().err


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


def test_should_reject_window_ending_in_the_future(capsys: pytest.CaptureFixture[str]) -> None:
    exit_code = run(["c8-ck-test", "--start", "2999-01-01T00:00:00Z"])

    assert exit_code == 1
    assert "must not end in the future" in capsys.readouterr().err


def test_should_derive_start_from_end_and_duration() -> None:
    options = parse_args(["c8-ck-test", "--end", "2026-08-14T10:30:00Z", "--duration-seconds", "1800"])

    assert options.duration_seconds == 1800
    assert options.start_label == "2026-08-14T10:00:00Z"
    assert options.end_label == "2026-08-14T10:30:00Z"
    assert options.time_anchor == "2026-08-14T10:30:00Z"


def test_should_accept_unix_end_with_the_default_duration() -> None:
    options = parse_args(["c8-ck-test", "--end", "1791438000"])

    assert options.duration_seconds == 600
    assert options.start_label == "2026-10-08T05:30:00Z"
    assert options.end_label == "2026-10-08T05:40:00Z"


def test_should_derive_duration_from_start_and_end() -> None:
    options = parse_args(["c8-ck-test", "--start", "2026-08-14T10:00:00Z", "--end", "2026-08-14T13:00:00Z"])

    assert options.duration_seconds == 10800
    assert options.start_label == "2026-08-14T10:00:00Z"
    assert options.end_label == "2026-08-14T13:00:00Z"


def test_should_reject_duration_with_both_start_and_end(capsys: pytest.CaptureFixture[str]) -> None:
    exit_code = run(
        [
            "c8-ck-test",
            "--start",
            "2026-08-14T10:00:00Z",
            "--end",
            "2026-08-14T10:30:00Z",
            "--duration-seconds",
            "1800",
        ]
    )

    assert exit_code == 1
    assert "cannot be combined with both --start and --end" in capsys.readouterr().err


def test_should_reject_end_not_after_start(capsys: pytest.CaptureFixture[str]) -> None:
    exit_code = run(["c8-ck-test", "--start", "2026-08-14T10:30:00Z", "--end", "2026-08-14T10:00:00Z"])

    assert exit_code == 1
    assert "must end after it starts" in capsys.readouterr().err


def test_should_reject_end_in_the_future(capsys: pytest.CaptureFixture[str]) -> None:
    exit_code = run(["c8-ck-test", "--end", "2999-01-01T00:00:00Z"])

    assert exit_code == 1
    assert "must not end in the future" in capsys.readouterr().err


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


def test_should_reject_unrepresentable_reporting_window_with_no_start(capsys: pytest.CaptureFixture[str]) -> None:
    exit_code = run(["c8-ck-test", "--duration-seconds", "999999999999999999999"])

    assert exit_code == 1
    assert "reporting window is outside the supported timestamp range" in capsys.readouterr().err


def test_should_reject_unrepresentable_reporting_window(capsys: pytest.CaptureFixture[str]) -> None:
    exit_code = run(["c8-ck-test", "--start", "0", "--duration-seconds", "999999999999999999999"])

    assert exit_code == 1
    assert "reporting window is outside the supported timestamp range" in capsys.readouterr().err


def test_should_reject_negative_duration(capsys: pytest.CaptureFixture[str]) -> None:
    exit_code = run(["c8-ck-test", "--duration-seconds", "-1"])

    assert exit_code == 2
    assert "-1 is not in the range x>=1" in capsys.readouterr().err


def test_should_reject_too_long_namespace(capsys: pytest.CaptureFixture[str]) -> None:
    exit_code = run(["a" * 64])

    assert exit_code == 2
    assert "max 63 characters; lowercase alphanumeric or '-', and must start and end" in capsys.readouterr().err


def test_should_reject_invalid_namespace(capsys: pytest.CaptureFixture[str]) -> None:
    exit_code = run(["Invalid_Namespace"])

    assert exit_code == 2
    assert "max 63 characters; lowercase alphanumeric or '-', and must start and end" in capsys.readouterr().err


def test_should_write_to_stdout_for_empty_output_path() -> None:
    options = parse_args(["c8-ck-test", "--output", ""])

    assert options.output_file is None


def test_should_pass_output_path(tmp_path: Path) -> None:
    output_file = tmp_path / "report.json"

    options = parse_args(["c8-ck-test", "--output", str(output_file)])

    assert options.output_file == output_file


def test_should_accept_short_and_alias_options(tmp_path: Path) -> None:
    output_file = tmp_path / "report.json"

    options = parse_args(
        ["c8-ck-test", "-d", "60", "-r", "1m", "-s", "30s", "-e", "http://prom:9090", "-u", "user", "-p", "pass"]
        + ["-f", "tsv", "-o", str(output_file)]
    )

    assert options.duration_seconds == 60
    assert options.rate_interval == "1m"
    assert options.sample_step == "30s"
    assert options.endpoint == "http://prom:9090"
    assert options.basic_auth_user == "user"
    assert options.basic_auth_password == "pass"
    assert options.output_format == "tsv"
    assert options.output_file == output_file
    assert parse_args(["c8-ck-test", "--duration", "60"]).duration_seconds == 60
    assert parse_args(["c8-ck-test", "--rate", "1m"]).rate_interval == "1m"
    assert parse_args(["c8-ck-test", "--step", "30s"]).sample_step == "30s"


def test_should_accept_short_queries_alias(tmp_path: Path) -> None:
    queries_file = tmp_path / "queries.yaml"
    queries_file.write_text("queries: []", encoding="utf-8")

    options = parse_args(["c8-ck-test", "-q", str(queries_file)])

    assert options.queries_file == queries_file


@pytest.mark.parametrize("args", [[""], ["--", "-abc"]])
def test_should_reject_invalid_namespace_forms(args: list[str], capsys: pytest.CaptureFixture[str]) -> None:
    exit_code = run(args)

    assert exit_code == 2
    assert "must be a valid Kubernetes DNS label" in capsys.readouterr().err


def test_should_read_options_from_environment(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("LOADTESTCTL_REPORT_DURATION_SECONDS", "90")
    monkeypatch.setenv("LOADTESTCTL_REPORT_USER", "env-user")
    monkeypatch.setenv("LOADTESTCTL_REPORT_PASSWORD", "env-pass")
    monkeypatch.setenv("LOADTESTCTL_REPORT_FORMAT", "csv")
    monkeypatch.setenv("LOADTESTCTL_REPORT_NO_HEADER", "true")

    options = parse_args(["c8-ck-test"])

    assert options.duration_seconds == 90
    assert options.basic_auth_user == "env-user"
    assert options.basic_auth_password == "env-pass"
    assert options.output_format == "csv"
    assert options.include_header is False


def test_should_prefer_command_line_over_environment(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("LOADTESTCTL_REPORT_DURATION_SECONDS", "90")
    monkeypatch.setenv("LOADTESTCTL_REPORT_PASSWORD", "env-pass")

    options = parse_args(["c8-ck-test", "-d", "30", "-u", "cli-user", "-p", "cli-pass"])

    assert options.duration_seconds == 30
    assert options.basic_auth_password == "cli-pass"


def test_should_reject_invalid_environment_value(
    monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    monkeypatch.setenv("LOADTESTCTL_REPORT_DURATION_SECONDS", "0")

    exit_code = run(["c8-ck-test"])

    assert exit_code == 2
    assert "--duration-seconds" in capsys.readouterr().err
