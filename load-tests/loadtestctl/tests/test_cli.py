from pathlib import Path

import pytest

import loadtestctl
from loadtestctl.cli import build_parser
from loadtestctl.cli import run

PROJECT_DIR = Path(loadtestctl.__file__).resolve().parent


def test_should_build_parser() -> None:
    parser = build_parser()

    assert parser.prog == "loadtestctl"


def test_should_accept_help_and_exit_with_zero() -> None:
    with pytest.raises(SystemExit) as exec_info:
        run(["--help"])

    assert exec_info.value.code == 0
