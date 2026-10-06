import argparse
import sys
from collections.abc import Sequence
from pathlib import Path

HERE = Path(__file__).resolve().parent


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="loadtestctl",
        description="A helper CLI for controlling and operating on load tests",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""Examples: 
        tbd.
        """,
    )
    return parser


def run(argv: Sequence[str]) -> int:
    args = build_parser().parse_args(argv)
    print(f"Running with arguments: {args}", file=sys.stderr)
    return 0


def main() -> None:
    sys.exit(run(sys.argv[1:]))
