import argparse
import re
import sys
from collections.abc import Mapping
from collections.abc import Sequence
from dataclasses import dataclass
from datetime import UTC
from datetime import datetime
from pathlib import Path

from pydantic import ValidationError

from .errors import ReportError
from .prometheus import PrometheusClient
from .prometheus import check_endpoint
from .queries import QueriesDocument
from .report import build_report
from .report import render_report

HERE = Path(__file__).resolve().parent
DEFAULT_QUERIES_FILE = HERE / "report-queries.yaml"
NAMESPACE_PATTERN = re.compile(r"^[a-z0-9]([-a-z0-9]*[a-z0-9])?$")
DURATION_PATTERN = re.compile(
    r"^(?=[0-9])(?:[1-9][0-9]*y)?(?:[1-9][0-9]*w)?(?:[1-9][0-9]*d)?"
    r"(?:[1-9][0-9]*h)?(?:[1-9][0-9]*ms)?(?:[1-9][0-9]*m)?(?:[1-9][0-9]*s)?$"
)


@dataclass(frozen=True)
class Options:
    namespace: str
    duration_seconds: int
    rate_interval: str
    sample_step: str
    endpoint: str
    basic_auth_user: str
    basic_auth_password: str
    time_anchor: str
    start_label: str
    end_label: str
    output_format: str
    include_header: bool
    missing_value: str
    queries_file: Path
    output_file: Path | None


def type_namespace(value: str) -> str:
    if len(value) > 63 or not NAMESPACE_PATTERN.fullmatch(value):
        raise argparse.ArgumentTypeError(
            f"namespace '{value}' must be a valid Kubernetes DNS label "
            "(max 63 characters; lowercase alphanumeric or '-', and must start and end "
            "with an alphanumeric character)."
        )
    return value


def type_positive_int(value: str) -> int:
    try:
        parsed = int(value)
    except ValueError as error:
        raise argparse.ArgumentTypeError(f"'{value}' must be an integer.") from error
    if parsed <= 0:
        raise argparse.ArgumentTypeError(f"'{value}' must be a positive integer.")
    return parsed


def type_duration(value: str) -> str:
    if not DURATION_PATTERN.fullmatch(value):
        raise argparse.ArgumentTypeError(f"'{value}' must be a Prometheus duration like 30s, 5m, or 1h.")
    return value


def parse_epoch(value: str) -> int:
    if value.isdigit():
        epoch = int(value)
        try:
            datetime.fromtimestamp(epoch, UTC)
        except (OSError, OverflowError, ValueError) as error:
            raise ValueError(f"could not represent timestamp '{value}'") from error
        return epoch
    try:
        normalized = value.replace("Z", "+00:00")
        parsed = datetime.fromisoformat(normalized)
    except ValueError as error:
        raise ValueError(f"could not parse timestamp '{value}'") from error
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=UTC)
    return int(parsed.timestamp())


def type_timestamp(value: str) -> str:
    try:
        parse_epoch(value)
    except ValueError as error:
        raise argparse.ArgumentTypeError(str(error)) from error
    return value


def existing_queries_file(value: str | Path) -> Path:
    queries_file = Path(value)
    if queries_file.is_file():
        return queries_file

    packaged_queries_file = HERE / queries_file
    if not queries_file.is_absolute() and packaged_queries_file.is_file():
        return packaged_queries_file

    raise argparse.ArgumentTypeError(f"'{value}' must be an existing YAML file.")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="load-test-report",
        description="Build a wide load-test report from Prometheus.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""Examples:
  # Port-forwarded Prometheus, JSON output:
  uv run load-test-report c8-ck-baseline-20260814 --duration-seconds 1800

  # Historical window, spreadsheet-friendly TSV:
  uv run load-test-report c8-ck-baseline-20260814 \\
    --start 2026-08-14T10:00:00Z \\
    --end 2026-08-14T10:30:00Z \\
    --format tsv --no-header

  # CI monitor ingress with basic auth:
  uv run load-test-report c8-ck-baseline-20260814 \\
    --duration-seconds 1800 \\
    --endpoint https://ci-monitor.benchmark.camunda.cloud \\
    --user "$PROM_USER" \\
    --password "$PROM_PASS" \\
    --format csv > /tmp/load-test-report.csv""",
    )
    parser.add_argument("namespace", type=type_namespace, help="Exact load-test namespace.")
    parser.add_argument(
        "--duration-seconds", default=600, type=type_positive_int, help="Query window duration. Default: 600."
    )
    parser.add_argument("--rate-interval", default="5m", type=type_duration, help="Short rate interval. Default: 5m.")
    parser.add_argument(
        "--sample-step",
        default="1m",
        type=type_duration,
        help="Subquery sample resolution for window summaries. Default: 1m.",
    )
    parser.add_argument(
        "--queries",
        default=DEFAULT_QUERIES_FILE,
        type=existing_queries_file,
        help=f"YAML query file path. Default: packaged {DEFAULT_QUERIES_FILE.name}.",
    )
    parser.add_argument(
        "--at",
        default=None,
        type=type_timestamp,
        help="Prometheus query time anchor, RFC3339 or Unix timestamp.",
    )
    parser.add_argument("--start", default=None, type=type_timestamp, help="Start of the reporting window.")
    parser.add_argument("--end", default=None, type=type_timestamp, help="End of the reporting window.")
    parser.add_argument("--endpoint", default="http://localhost:9090", help="Prometheus base URL.")
    parser.add_argument("--user", default="", help="Basic auth user for Prometheus.")
    parser.add_argument("--password", default="", help="Basic auth password for Prometheus.")
    parser.add_argument("--format", default="json", choices=("json", "csv", "tsv"), help="Output format.")
    parser.add_argument("--no-header", action="store_true", help="Omit the CSV/TSV header row.")
    parser.add_argument("--missing-value", default="NaN", help="CSV/TSV placeholder for missing metrics.")
    parser.add_argument("--output", default="", help="Write output to a file instead of stdout.")
    return parser


def parse_args(argv: Sequence[str]) -> Options:
    args = build_parser().parse_args(argv)
    duration_seconds = args.duration_seconds
    time_anchor = args.at or ""
    start_label = ""
    end_label = ""
    if args.start or args.end:
        if not args.start or not args.end:
            raise ReportError("--start and --end must be provided together.")
        if time_anchor:
            raise ReportError("--at cannot be combined with --start/--end.")

        start_epoch = parse_epoch(args.start)
        end_epoch = parse_epoch(args.end)
        if end_epoch <= start_epoch:
            raise ReportError("--end must be after --start.")

        duration_seconds = end_epoch - start_epoch
        start_label = datetime.fromtimestamp(start_epoch, UTC).strftime("%Y-%m-%dT%H:%M:%SZ")
        end_label = datetime.fromtimestamp(end_epoch, UTC).strftime("%Y-%m-%dT%H:%M:%SZ")
        time_anchor = end_label
    elif time_anchor:
        anchor_epoch = parse_epoch(time_anchor)
        start_label = datetime.fromtimestamp(anchor_epoch - duration_seconds, UTC).strftime("%Y-%m-%dT%H:%M:%SZ")
        end_label = datetime.fromtimestamp(anchor_epoch, UTC).strftime("%Y-%m-%dT%H:%M:%SZ")
        time_anchor = end_label

    return Options(
        namespace=args.namespace,
        duration_seconds=duration_seconds,
        rate_interval=args.rate_interval,
        sample_step=args.sample_step,
        endpoint=args.endpoint,
        basic_auth_user=args.user,
        basic_auth_password=args.password,
        time_anchor=time_anchor,
        start_label=start_label,
        end_label=end_label,
        output_format=args.format,
        include_header=not args.no_header,
        missing_value=args.missing_value,
        queries_file=args.queries,
        output_file=Path(args.output) if args.output else None,
    )


def query_substitutions(options: Options) -> Mapping[str, str]:
    return {
        "$NAMESPACE": options.namespace,
        "$DURATION_S": f"{options.duration_seconds}s",
        "$RATE_INTERVAL": options.rate_interval,
        "$SAMPLE_STEP": options.sample_step,
    }


def run(argv: Sequence[str]) -> int:
    try:
        options = parse_args(argv)
        client = PrometheusClient(
            options.endpoint,
            options.basic_auth_user,
            options.basic_auth_password,
            options.time_anchor,
        )
        check_endpoint(client, options.endpoint)
        query_document = QueriesDocument.from_file(options.queries_file, query_substitutions(options))
        report = build_report(options, query_document, client)
        rendered = render_report(report, options.output_format, options.include_header, options.missing_value)
        if options.output_file:
            try:
                options.output_file.write_text(f"{rendered}\n", encoding="utf-8")
            except OSError as error:
                raise ReportError(f"Could not write output file '{options.output_file}': {error}") from error
        else:
            print(rendered)
        return 0
    except SystemExit as error:
        return int(error.code) if isinstance(error.code, int) else 1
    except ValidationError as error:
        print(f"Error: query document is invalid: {error}", file=sys.stderr)
        return 1
    except ReportError as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1


def main() -> None:
    sys.exit(run(sys.argv[1:]))
