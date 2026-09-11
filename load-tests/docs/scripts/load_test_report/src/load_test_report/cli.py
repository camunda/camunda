"""Command-line parsing for the load-test report command."""

from __future__ import annotations

import argparse
import sys
from collections.abc import Sequence


def build_parser() -> argparse.ArgumentParser:
    return argparse.ArgumentParser(
        prog="load-test-report",
        description="Build a wide load-test report from Prometheus.",
    )


def run(argv: Sequence[str]) -> int:
    try:
        build_parser().parse_args(argv)
    except SystemExit as error:
        return int(error.code) if isinstance(error.code, int) else 1
    return 0


def main() -> None:
    sys.exit(run(sys.argv[1:]))
