"""Command-line parsing and orchestration."""

from __future__ import annotations

import argparse
import re
import sys
from collections.abc import Sequence
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

from errors import ReportError
from prometheus import PrometheusClient, check_endpoint
from queries import DEFAULT_QUERIES, load_query_document, query_substitutions, resolve_queries_file
from report import build_report, render_report

NAMESPACE_PATTERN = re.compile(r"^[a-z0-9]([-a-z0-9]*[a-z0-9])?$")
DURATION_PATTERN = re.compile(r"^[1-9][0-9]*(ms|s|m|h|d|w|y)$")


@dataclass(frozen=True)
class Options:
    namespace: str
    duration_seconds: int
    rate_interval: str
    sample_step: str
    endpoint: str
    bearer_token: str
    basic_auth_user: str
    basic_auth_password: str
    time_anchor: str
    start_label: str
    end_label: str
    output_format: str
    include_header: bool
    missing_value: str
    queries_source: str
    queries_file: Path
    output_file: Optional[Path]


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="load_test_report.py",
        description="Build a wide load-test report from Prometheus.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""Examples:
  # Port-forwarded Prometheus, JSON:
  uv run ./load_test_report.py c8-ck-baseline-20260814 --duration-seconds 1800

  # Historical window, spreadsheet-friendly TSV:
  uv run ./load_test_report.py c8-ck-baseline-20260814 \\
    --start 2026-08-14T10:00:00Z \\
    --end 2026-08-14T10:30:00Z \\
    --format tsv --no-header

  # CI monitor ingress with basic auth:
  uv run ./load_test_report.py c8-ck-baseline-20260814 \\
    --duration-seconds 1800 \\
    --endpoint https://ci-monitor.benchmark.camunda.cloud \\
    --user "$PROM_USER" \\
    --password "$PROM_PASS" \\
    --format csv > /tmp/load-test-report.csv""",
    )
    parser.add_argument("namespace_arg", nargs="?", help="Exact load-test namespace.")
    parser.add_argument("--namespace", dest="namespace_flag", help="Exact load-test namespace.")
    parser.add_argument("--duration-seconds", default="600", help="Query window duration. Default: 600.")
    parser.add_argument("--rate-interval", default="5m", help="Short rate interval. Default: 5m.")
    parser.add_argument(
        "--sample-step",
        default="1m",
        help="Subquery sample resolution for window summaries. Default: 1m.",
    )
    parser.add_argument(
        "--queries",
        default=DEFAULT_QUERIES,
        help=("Built-in query set name or custom YAML/JSON query file path. Default: camunda."),
    )
    parser.add_argument("--at", default="", help="Prometheus query time anchor, RFC3339 or Unix timestamp.")
    parser.add_argument("--start", default="", help="Start of the reporting window.")
    parser.add_argument("--end", default="", help="End of the reporting window.")
    parser.add_argument("--endpoint", default="http://localhost:9090", help="Prometheus base URL.")
    parser.add_argument("--token", default="", help="Bearer token value for Prometheus.")
    parser.add_argument("--user", default="", help="Basic auth user for Prometheus.")
    parser.add_argument("--password", default="", help="Basic auth password for Prometheus.")
    parser.add_argument("--format", default="json", choices=("json", "csv", "tsv"), help="Output format.")
    parser.add_argument("--no-header", action="store_true", help="Omit the CSV/TSV header row.")
    parser.add_argument("--missing-value", default="NaN", help="CSV/TSV placeholder for missing metrics.")
    parser.add_argument("--output", default="", help="Write output to a file instead of stdout.")
    return parser


def parse_args(argv: Sequence[str], script_dir: Path) -> Options:
    args = build_parser().parse_args(argv)
    namespace = args.namespace_flag or args.namespace_arg or ""
    if not namespace:
        raise ReportError("Missing <namespace>.")
    if len(namespace) > 63 or not NAMESPACE_PATTERN.fullmatch(namespace):
        raise ReportError(
            f"namespace '{namespace}' must be a valid Kubernetes DNS label "
            "(max 63 characters; lowercase alphanumeric or '-', and must start and end "
            "with an alphanumeric character)."
        )

    if not args.duration_seconds.isdigit() or args.duration_seconds.startswith("0"):
        raise ReportError(f"duration-seconds '{args.duration_seconds}' must be a positive integer.")
    duration_seconds = int(args.duration_seconds)

    if not DURATION_PATTERN.fullmatch(args.rate_interval):
        raise ReportError(f"rate-interval '{args.rate_interval}' must be a Prometheus duration like 30s, 5m, or 1h.")
    if not DURATION_PATTERN.fullmatch(args.sample_step):
        raise ReportError(f"sample-step '{args.sample_step}' must be a Prometheus duration like 30s, 1m, or 5m.")

    time_anchor = args.at
    start_label = ""
    end_label = ""
    if args.start or args.end:
        if not args.start or not args.end:
            raise ReportError("--start and --end must be provided together.")
        if time_anchor:
            raise ReportError("--at cannot be combined with --start/--end.")

        start_epoch = parse_epoch(args.start, "--start")
        end_epoch = parse_epoch(args.end, "--end")
        if end_epoch <= start_epoch:
            raise ReportError("--end must be after --start.")

        duration_seconds = end_epoch - start_epoch
        time_anchor = args.end
        start_label = datetime.fromtimestamp(start_epoch, timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        end_label = datetime.fromtimestamp(end_epoch, timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    elif time_anchor:
        anchor_epoch = parse_epoch(time_anchor, "--at")
        start_label = datetime.fromtimestamp(anchor_epoch - duration_seconds, timezone.utc).strftime(
            "%Y-%m-%dT%H:%M:%SZ"
        )
        end_label = datetime.fromtimestamp(anchor_epoch, timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    queries_file = resolve_queries_file(script_dir, args.queries)

    return Options(
        namespace=namespace,
        duration_seconds=duration_seconds,
        rate_interval=args.rate_interval,
        sample_step=args.sample_step,
        endpoint=args.endpoint,
        bearer_token=args.token,
        basic_auth_user=args.user,
        basic_auth_password=args.password,
        time_anchor=time_anchor,
        start_label=start_label,
        end_label=end_label,
        output_format=args.format,
        include_header=not args.no_header,
        missing_value=args.missing_value,
        queries_source=args.queries,
        queries_file=queries_file,
        output_file=Path(args.output) if args.output else None,
    )


def parse_epoch(value: str, flag: str) -> int:
    if value.isdigit():
        return int(value)
    try:
        normalized = value.replace("Z", "+00:00")
        parsed = datetime.fromisoformat(normalized)
    except ValueError as error:
        raise ReportError(f"Could not parse {flag} '{value}'.") from error
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return int(parsed.timestamp())


def run(argv: Sequence[str]) -> int:
    script_dir = Path(__file__).resolve().parent
    try:
        options = parse_args(argv, script_dir)
        client = PrometheusClient(
            options.endpoint,
            options.bearer_token,
            options.basic_auth_user,
            options.basic_auth_password,
            options.time_anchor,
        )
        check_endpoint(client, options.endpoint)
        query_document = load_query_document(options.queries_file, query_substitutions(options))
        report = build_report(options, query_document, client)
        rendered = render_report(report, options.output_format, options.include_header, options.missing_value)
        if options.output_file:
            options.output_file.write_text(f"{rendered}\n", encoding="utf-8")
        else:
            print(rendered)
        return 0
    except ReportError as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1
