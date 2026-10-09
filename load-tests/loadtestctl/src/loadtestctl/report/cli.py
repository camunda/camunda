import re
import sys
from collections.abc import Mapping
from dataclasses import dataclass
from datetime import UTC
from datetime import datetime
from datetime import timedelta
from pathlib import Path
from typing import Annotated
from typing import Literal

import typer
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
DURATION_COMPONENT_PATTERN = re.compile(r"[1-9][0-9]*(ms|[ywdhms])")
DURATION_UNIT_ORDER = ("y", "w", "d", "h", "m", "s", "ms")


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
        raise typer.BadParameter(
            f"namespace '{value}' must be a valid Kubernetes DNS label "
            "(max 63 characters; lowercase alphanumeric or '-', and must start and end "
            "with an alphanumeric character)."
        )
    return value


def type_duration(value: str) -> str:
    position = 0
    previous_unit = -1
    for component in DURATION_COMPONENT_PATTERN.finditer(value):
        unit = component.group(1)
        unit_order = DURATION_UNIT_ORDER.index(unit)
        if component.start() != position or unit_order <= previous_unit:
            break
        position = component.end()
        previous_unit = unit_order

    if not value or position != len(value):
        raise typer.BadParameter(f"'{value}' must be a Prometheus duration like 30s, 5m, or 1h.")
    return value


def parse_epoch(value: str) -> datetime:
    if value.isdigit():
        epoch = int(value)
        try:
            return datetime.fromtimestamp(epoch, UTC)
        except (OSError, OverflowError, ValueError) as error:
            raise typer.BadParameter(f"could not represent timestamp '{value}'") from error
    try:
        normalized = value.replace("Z", "+00:00")
        parsed = datetime.fromisoformat(normalized)
    except ValueError as error:
        raise typer.BadParameter(f"could not parse timestamp '{value}'") from error
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=UTC)
    if parsed.microsecond:
        raise typer.BadParameter(f"fractional timestamp '{value}' is not supported")
    return parsed


def format_epoch(value: datetime) -> str:
    try:
        return value.astimezone(UTC).strftime("%Y-%m-%dT%H:%M:%SZ")
    except (OSError, OverflowError, ValueError) as error:
        raise ReportError("reporting window is outside the supported timestamp range") from error


def existing_queries_file(value: str | Path) -> Path:
    queries_file = Path(value)
    if queries_file.is_file():
        return queries_file

    packaged_queries_file = HERE / queries_file
    if not queries_file.is_absolute() and packaged_queries_file.is_file():
        return packaged_queries_file

    raise typer.BadParameter(f"'{value}' must be an existing YAML file.")


REPORT_HELP = "Build a wide load-test report from Prometheus."

REPORT_EPILOG = """\b
Examples:

\b
  # Port-forwarded Prometheus, JSON output:
  loadtestctl report c8-ck-baseline-20260814 --duration-seconds 1800

\b
  # Historical window, spreadsheet-friendly TSV:
  loadtestctl report c8-ck-baseline-20260814 \\
    --start 2026-08-14T10:00:00Z \\
    --duration-seconds 1800 \\
    --format tsv --no-header

\b
  # CI monitor ingress with basic auth:
  loadtestctl report c8-ck-baseline-20260814 \\
    --duration-seconds 1800 \\
    --endpoint https://ci-monitor.benchmark.camunda.cloud \\
    --user "$PROM_USER" \\
    --password "$PROM_PASS" \\
    --format csv > /tmp/load-test-report.csv"""


def resolve_window(start: datetime | None, duration_seconds: int) -> tuple[str, str]:
    now = datetime.now(UTC).replace(microsecond=0)
    try:
        if start is None:
            start = now - timedelta(seconds=duration_seconds)
        end = start + timedelta(seconds=duration_seconds)
    except OverflowError as error:
        raise ReportError("reporting window is outside the supported timestamp range") from error
    if end > now:
        raise ReportError("reporting window must not end in the future; use an earlier --start or a shorter duration.")
    return format_epoch(start), format_epoch(end)


def query_substitutions(options: Options) -> Mapping[str, str]:
    return {
        "$NAMESPACE": options.namespace,
        "$DURATION_S": f"{options.duration_seconds}s",
        "$RATE_INTERVAL": options.rate_interval,
        "$SAMPLE_STEP": options.sample_step,
    }


def run_report(options: Options) -> None:
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


def report(
    namespace: Annotated[
        str, typer.Argument(parser=type_namespace, metavar="NAMESPACE", help="Exact load-test namespace.")
    ],
    duration_seconds: Annotated[int, typer.Option(min=1, help="Query window duration in seconds.")] = 600,
    rate_interval: Annotated[
        str,
        typer.Option(parser=type_duration, metavar="DURATION", help="Short rate interval for dashboard-style rollups."),
    ] = "5m",
    sample_step: Annotated[
        str,
        typer.Option(parser=type_duration, metavar="DURATION", help="Subquery sample resolution for window summaries."),
    ] = "1m",
    queries: Annotated[
        Path,
        typer.Option(
            parser=existing_queries_file,
            metavar="FILE",
            show_default=f"packaged {DEFAULT_QUERIES_FILE.name}",
            help="YAML query file path.",
        ),
    ] = DEFAULT_QUERIES_FILE,
    start: Annotated[
        datetime | None,
        typer.Option(
            parser=parse_epoch,
            metavar="TIMESTAMP",
            show_default="now minus --duration-seconds",
            help="Start of the reporting window, RFC3339 or Unix timestamp. "
            "The window ends at start plus --duration-seconds.",
        ),
    ] = None,
    endpoint: Annotated[str, typer.Option(help="Prometheus base URL.")] = "http://localhost:9090",
    user: Annotated[str, typer.Option(help="Basic auth user for Prometheus.")] = "",
    password: Annotated[str, typer.Option(help="Basic auth password for Prometheus.")] = "",
    output_format: Annotated[Literal["json", "csv", "tsv"], typer.Option("--format", help="Output format.")] = "json",
    no_header: Annotated[bool, typer.Option("--no-header", help="Omit the CSV/TSV header row.")] = False,
    missing_value: Annotated[str, typer.Option(help="CSV/TSV placeholder for missing metrics.")] = "NaN",
    output: Annotated[Path | None, typer.Option(help="Write output to a file instead of stdout.")] = None,
) -> None:
    try:
        start_label, end_label = resolve_window(start, duration_seconds)
        run_report(
            Options(
                namespace=namespace,
                duration_seconds=duration_seconds,
                rate_interval=rate_interval,
                sample_step=sample_step,
                endpoint=endpoint,
                basic_auth_user=user,
                basic_auth_password=password,
                time_anchor=end_label,
                start_label=start_label,
                end_label=end_label,
                output_format=output_format,
                include_header=not no_header,
                missing_value=missing_value,
                queries_file=queries,
                output_file=output,
            )
        )
    except ValidationError as error:
        print(f"Error: query document is invalid: {error}", file=sys.stderr)
        raise typer.Exit(1) from error
    except ReportError as error:
        print(f"Error: {error}", file=sys.stderr)
        raise typer.Exit(1) from error


def register(app: typer.Typer) -> None:
    app.command("report", help=REPORT_HELP, epilog=REPORT_EPILOG)(report)
