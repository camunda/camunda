"""Build a wide load-test report from Prometheus.

Run `make -C load-tests/docs/scripts/load_test_report check` from the repository
root to validate local changes.
"""

from .cli import build_parser
from .cli import main
from .cli import run
from .errors import ReportError
from .queries import BUILTIN_QUERY_FILES
from .queries import DEFAULT_QUERIES
from .queries import load_query_document
from .queries import parse_query_file
from .queries import query_substitutions
from .queries import resolve_queries_file
from .queries import substitute_query_text
from .queries import validate_query_document

__all__ = [
    "BUILTIN_QUERY_FILES",
    "DEFAULT_QUERIES",
    "ReportError",
    "build_parser",
    "load_query_document",
    "main",
    "parse_query_file",
    "query_substitutions",
    "resolve_queries_file",
    "run",
    "substitute_query_text",
    "validate_query_document",
]
