import pytest

from loadtestctl.cli import cli


def test_should_accept_help_and_exit_with_zero() -> None:
    with pytest.raises(SystemExit) as exec_info:
        cli(args=["--help"], prog_name="loadtestctl")

    assert exec_info.value.code == 0


def test_should_require_a_subcommand() -> None:
    with pytest.raises(SystemExit) as exec_info:
        cli(args=[], prog_name="loadtestctl")

    assert exec_info.value.code == 2


def test_should_list_report_subcommand_in_help(capsys: pytest.CaptureFixture[str]) -> None:
    with pytest.raises(SystemExit):
        cli(args=["--help"], prog_name="loadtestctl")

    assert "Commands:\n  report  Build a wide load-test report" in capsys.readouterr().out
