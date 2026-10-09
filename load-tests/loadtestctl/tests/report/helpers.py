from collections.abc import Sequence
from unittest import mock

from loadtestctl.cli import cli
from loadtestctl.report.cli import Options


def run(argv: Sequence[str]) -> int:
    try:
        cli(args=["report", *argv], prog_name="loadtestctl")
    except SystemExit as error:
        return int(error.code) if isinstance(error.code, int) else 1
    return 0


def parse_args(argv: Sequence[str]) -> Options:
    with mock.patch("loadtestctl.report.cli.run_report") as run_report:
        exit_code = run(argv)
    if exit_code != 0:
        raise SystemExit(exit_code)
    options: Options = run_report.call_args.args[0]
    return options
