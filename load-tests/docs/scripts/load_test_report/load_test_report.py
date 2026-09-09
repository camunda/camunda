#!/usr/bin/env python3
"""Build a wide load-test report from Prometheus."""

from __future__ import annotations

import sys

from cli import Options, build_parser, parse_args, parse_epoch, run
from errors import MissingMetric, ReportError
from prometheus import PrometheusClient, auth_headers, check_endpoint, prometheus_endpoint_help
from queries import (
    BUILTIN_QUERY_FILES,
    DEFAULT_QUERIES,
    load_query_document,
    parse_query_file,
    query_substitutions,
    resolve_queries_file,
    substitute_query_text,
    validate_query_document,
)
from report import build_report, extract_metric_value, parse_number, render_report, warn

__all__ = [
    "BUILTIN_QUERY_FILES",
    "DEFAULT_QUERIES",
    "MissingMetric",
    "Options",
    "PrometheusClient",
    "ReportError",
    "auth_headers",
    "build_parser",
    "build_report",
    "check_endpoint",
    "extract_metric_value",
    "load_query_document",
    "parse_args",
    "parse_epoch",
    "parse_number",
    "parse_query_file",
    "prometheus_endpoint_help",
    "query_substitutions",
    "render_report",
    "resolve_queries_file",
    "run",
    "substitute_query_text",
    "validate_query_document",
    "warn",
]


if __name__ == "__main__":
    sys.exit(run(sys.argv[1:]))
