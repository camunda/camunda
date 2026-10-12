from collections.abc import Sequence
from unittest import mock

import pytest

from loadtestctl.cli import cli
from loadtestctl.verify.context import VerifyError

from .helpers import completed

CLI = "loadtestctl.verify.cli"


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
    assert (k6_metrics_timeout, prometheus_url) == (300, "https://ci-monitor.benchmark.camunda.cloud")


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
