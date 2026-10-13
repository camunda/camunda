from collections.abc import Sequence
from pathlib import Path
from unittest import mock

import pytest

from loadtestctl.cli import cli
from loadtestctl.verify.context import VerifyError

from .helpers import completed

CLI = "loadtestctl.verify.cli"


@pytest.fixture(autouse=True)
def clean_environment(monkeypatch: pytest.MonkeyPatch) -> None:
    for name in (
        "LOADTESTCTL_VERIFY_RESULT_FILE",
        "PROMETHEUS_URL",
        "PROMETHEUS_USER",
        "PROMETHEUS_PASSWORD",
        "K6_METRICS_TIMEOUT",
    ):
        monkeypatch.delenv(name, raising=False)


def run(argv: Sequence[str]) -> int:
    try:
        cli(args=["verify", *argv], prog_name="loadtestctl")
    except SystemExit as error:
        return int(error.code) if isinstance(error.code, int) else 1
    return 0


def run_verify_options(argv: Sequence[str]) -> mock.MagicMock:
    with mock.patch(f"{CLI}.run_verify") as run_verify:
        assert run(argv) == 0
    return run_verify


def test_should_list_verify_subcommand_in_help(capsys: pytest.CaptureFixture[str]) -> None:
    with pytest.raises(SystemExit):
        cli(args=["--help"], prog_name="loadtestctl")

    assert "verify" in capsys.readouterr().out


def test_should_use_the_default_options() -> None:
    run_verify = run_verify_options(["c8-test"])

    ctx, metrics_port, connectivity_timeout, k6_metrics_timeout, *_ = run_verify.call_args.args
    assert ctx.namespace == "c8-test"
    assert ctx.wait_timeout == 30
    assert ctx.wait_retries == 30
    assert metrics_port == 9600
    assert connectivity_timeout == 900
    assert k6_metrics_timeout == 300


def test_should_query_a_local_prometheus_without_authentication_by_default() -> None:
    run_verify = run_verify_options(["c8-test"])

    _, _, _, _, prometheus_url, prometheus_user, prometheus_password = run_verify.call_args.args
    assert (prometheus_url, prometheus_user, prometheus_password) == ("http://localhost:9090", "", "")


def test_should_read_prometheus_options_from_the_environment(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("PROMETHEUS_URL", "http://prom")
    monkeypatch.setenv("PROMETHEUS_USER", "u")
    monkeypatch.setenv("K6_METRICS_TIMEOUT", "120")

    run_verify = run_verify_options(["c8-test"])

    _, _, _, k6_metrics_timeout, prometheus_url, prometheus_user, _ = run_verify.call_args.args
    assert (k6_metrics_timeout, prometheus_url, prometheus_user) == (120, "http://prom", "u")


def test_should_use_the_defaults_for_empty_environment_variables(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("K6_METRICS_TIMEOUT", "")
    monkeypatch.setenv("PROMETHEUS_URL", "")

    run_verify = run_verify_options(["c8-test"])

    _, _, _, k6_metrics_timeout, prometheus_url, *_ = run_verify.call_args.args
    assert (k6_metrics_timeout, prometheus_url) == (300, "http://localhost:9090")


def test_should_validate_the_k6_metrics_timeout_of_the_environment(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("K6_METRICS_TIMEOUT", "-1")

    assert run(["c8-test"]) == 2


def test_should_print_success_status_only_on_stdout(capsys: pytest.CaptureFixture[str]) -> None:
    with (
        mock.patch(f"{CLI}.kubectl", return_value=completed()),
        mock.patch(f"{CLI}.wait_for_pods"),
        mock.patch(f"{CLI}.wait_for_testruns"),
        mock.patch(f"{CLI}.verify_connected"),
        mock.patch(f"{CLI}.verify_metrics"),
    ):
        exit_code = run(["c8-test"])

    assert exit_code == 0
    assert capsys.readouterr().out == "status=success\n"


def test_should_append_the_status_to_the_result_file(tmp_path: Path, capsys: pytest.CaptureFixture[str]) -> None:
    result_file = tmp_path / "result"
    result_file.write_text("existing=1\n")

    with (
        mock.patch(f"{CLI}.kubectl", return_value=completed()),
        mock.patch(f"{CLI}.wait_for_pods", side_effect=VerifyError("boom")),
    ):
        exit_code = run(["c8-test", "--result-file", str(result_file)])

    assert exit_code == 1
    assert result_file.read_text() == "existing=1\nstatus=failure\n"
    assert capsys.readouterr().out == ""


def test_should_print_the_status_when_the_result_file_is_not_writable(
    tmp_path: Path, capsys: pytest.CaptureFixture[str]
) -> None:
    with (
        mock.patch(f"{CLI}.kubectl", return_value=completed()),
        mock.patch(f"{CLI}.wait_for_pods"),
        mock.patch(f"{CLI}.wait_for_testruns"),
        mock.patch(f"{CLI}.verify_connected"),
        mock.patch(f"{CLI}.verify_metrics"),
    ):
        exit_code = run(["c8-test", "--result-file", str(tmp_path / "missing" / "result")])

    captured = capsys.readouterr()
    assert exit_code == 0
    assert captured.out == "status=success\n"
    assert "Could not write result file" in captured.err


def test_should_print_failure_status_when_a_check_fails(capsys: pytest.CaptureFixture[str]) -> None:
    with (
        mock.patch(f"{CLI}.kubectl", return_value=completed()),
        mock.patch(f"{CLI}.wait_for_pods", side_effect=VerifyError("boom")),
    ):
        exit_code = run(["c8-test"])

    captured = capsys.readouterr()
    assert exit_code == 1
    assert captured.out == "status=failure\n"
    assert "boom" in captured.err


def test_should_print_failure_status_on_unexpected_errors(capsys: pytest.CaptureFixture[str]) -> None:
    with mock.patch(f"{CLI}.kubectl", side_effect=FileNotFoundError("kubectl")):
        exit_code = run(["c8-test"])

    assert exit_code == 1
    assert capsys.readouterr().out == "status=failure\n"


def test_should_fail_when_namespace_does_not_exist(capsys: pytest.CaptureFixture[str]) -> None:
    with mock.patch(f"{CLI}.kubectl", return_value=completed("", 1)):
        exit_code = run(["c8-test"])

    assert exit_code == 1
    assert "does not exist" in capsys.readouterr().err
