"""Build a wide load-test report from Prometheus.

Run `make -C load-tests/docs/scripts/load_test_report check` from the repository
root to validate local changes.
"""

from .cli import build_parser
from .cli import main
from .cli import run

__all__ = [
    "build_parser",
    "main",
    "run",
]
