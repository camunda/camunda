import argparse
import sys
from collections.abc import Sequence
from pathlib import Path

from .report import cli as report_cli

HERE = Path(__file__).resolve().parent


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="loadtestctl",
        description="A helper CLI for controlling and operating on load tests",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""Examples: 
        loadtestctl report c8-ck-baseline-20260814 --duration-seconds 1800
        """,
    )
    subparsers = parser.add_subparsers(dest="command", required=True, metavar="<command>")
    report_cli.add_parser(subparsers)
    return parser


def run(argv: Sequence[str]) -> int:
    args = build_parser().parse_args(argv)
    return int(args.handler(args))


def main() -> None:
    sys.exit(run(sys.argv[1:]))
