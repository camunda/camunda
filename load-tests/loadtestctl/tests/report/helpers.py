from collections.abc import Sequence

from loadtestctl.cli import build_parser
from loadtestctl.cli import run as run_ctl
from loadtestctl.report.cli import Options
from loadtestctl.report.cli import build_options


def parse_args(argv: Sequence[str]) -> Options:
    return build_options(build_parser().parse_args(["report", *argv]))


def run(argv: Sequence[str]) -> int:
    try:
        return run_ctl(["report", *argv])
    except SystemExit as error:
        return int(error.code) if isinstance(error.code, int) else 1
