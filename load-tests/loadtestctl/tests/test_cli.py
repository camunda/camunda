import pytest

from loadtestctl.cli import app


def test_should_accept_help_and_exit_with_zero() -> None:
    with pytest.raises(SystemExit) as exec_info:
        app(args=["--help"], prog_name="loadtestctl")

    assert exec_info.value.code == 0


def test_should_require_a_subcommand() -> None:
    with pytest.raises(SystemExit) as exec_info:
        app(args=[], prog_name="loadtestctl")

    assert exec_info.value.code == 2


def test_should_list_report_subcommand_in_help(capsys: pytest.CaptureFixture[str]) -> None:
    with pytest.raises(SystemExit):
        app(args=["--help"], prog_name="loadtestctl")

    assert "report" in capsys.readouterr().out
