#!/usr/bin/env python3
"""Load-test report command entrypoint."""

from __future__ import annotations

import argparse
import sys


def build_parser() -> argparse.ArgumentParser:
    return argparse.ArgumentParser(
        prog="load_test_report.py",
        description="Build a wide load-test report from Prometheus.",
    )


def run(argv: list[str]) -> int:
    build_parser().parse_args(argv)
    return 0


if __name__ == "__main__":
    sys.exit(run(sys.argv[1:]))
