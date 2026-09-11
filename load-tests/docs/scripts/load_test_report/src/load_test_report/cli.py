import argparse
import sys
from collections.abc import Sequence
from typing import Any, Mapping


def build_parser() -> argparse.ArgumentParser:
    return argparse.ArgumentParser(
        prog="load-test-report",
        description="Build a wide load-test report from Prometheus.",
    )


def query_substitutions(options: Any) -> Mapping[str, str]:
    return {
        "$NAMESPACE": options.namespace,
        "$DURATION_S": f"{options.duration_seconds}s",
        "$RATE_INTERVAL": options.rate_interval,
        "$SAMPLE_STEP": options.sample_step,
    }


def run(argv: Sequence[str]) -> int:
    try:
        build_parser().parse_args(argv)
    except SystemExit as error:
        return int(error.code) if isinstance(error.code, int) else 1
    return 0


def main() -> None:
    sys.exit(run(sys.argv[1:]))
