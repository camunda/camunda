import re
import sys
from collections.abc import Mapping
from dataclasses import dataclass
from datetime import UTC
from datetime import datetime
from datetime import timedelta
from pathlib import Path

import click
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


class NamespaceType(click.ParamType[str]):
    name = "namespace"

    def convert(self, value: str, param: click.Parameter | None, ctx: click.Context | None) -> str:
        if len(value) > 63 or not NAMESPACE_PATTERN.fullmatch(value):
            self.fail(
                f"namespace '{value}' must be a valid Kubernetes DNS label "
                "(max 63 characters; lowercase alphanumeric or '-', and must start and end "
                "with an alphanumeric character).",
                param,
                ctx,
            )
        return value


class DurationType(click.ParamType[str]):
    name = "duration"

    def convert(self, value: str, param: click.Parameter | None, ctx: click.Context | None) -> str:
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
            self.fail(f"'{value}' must be a Prometheus duration like 30s, 5m, or 1h.", param, ctx)
        return value


class EpochType(click.ParamType[datetime]):
    name = "timestamp"

    def convert(self, value: str, param: click.Parameter | None, ctx: click.Context | None) -> datetime:
        if isinstance(value, datetime):
            return value
        try:
            return parse_epoch(value)
        except ValueError as error:
            self.fail(str(error), param, ctx)


class QueriesFileType(click.ParamType[Path]):
    name = "file"

    def convert(self, value: str | Path, param: click.Parameter | None, ctx: click.Context | None) -> Path:
        queries_file = Path(value)
        if queries_file.is_file():
            return queries_file

        packaged_queries_file = HERE / queries_file
        if not queries_file.is_absolute() and packaged_queries_file.is_file():
            return packaged_queries_file

        self.fail(f"'{value}' must be an existing YAML file.", param, ctx)


def parse_epoch(value: str) -> datetime:
    if value.isdigit():
        epoch = int(value)
        try:
            return datetime.fromtimestamp(epoch, UTC)
        except (OSError, OverflowError, ValueError) as error:
            raise ValueError(f"could not represent timestamp '{value}'") from error
    try:
        normalized = value.replace("Z", "+00:00")
        parsed = datetime.fromisoformat(normalized)
    except ValueError as error:
        raise ValueError(f"could not parse timestamp '{value}'") from error
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=UTC)
    if parsed.microsecond:
        raise ValueError(f"fractional timestamp '{value}' is not supported")
    return parsed


def format_epoch(value: datetime) -> str:
    try:
        return value.astimezone(UTC).strftime("%Y-%m-%dT%H:%M:%SZ")
    except (OSError, OverflowError, ValueError) as error:
        raise ReportError("reporting window is outside the supported timestamp range") from error


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


@click.command("report", help=REPORT_HELP, epilog=REPORT_EPILOG)
@click.argument("namespace_argument", metavar="[NAMESPACE]", required=False, type=NamespaceType())
@click.option(
    "-n",
    "--namespace",
    "--ns",
    "namespace_option",
    type=NamespaceType(),
    help="Exact load-test namespace, as alternative to the NAMESPACE argument.",
)
@click.option(
    "-d",
    "--duration-seconds",
    "--duration",
    type=click.IntRange(min=1),
    default=600,
    show_default=True,
    help="Query window duration in seconds.",
)
@click.option(
    "-r",
    "--rate-interval",
    "--rate",
    type=DurationType(),
    default="5m",
    show_default=True,
    help="Short rate interval for dashboard-style rollups.",
)
@click.option(
    "-s",
    "--sample-step",
    "--step",
    type=DurationType(),
    default="1m",
    show_default=True,
    help="Subquery sample resolution for window summaries.",
)
@click.option(
    "-q",
    "--queries",
    type=QueriesFileType(),
    default=DEFAULT_QUERIES_FILE,
    show_default=f"packaged {DEFAULT_QUERIES_FILE.name}",
    help="YAML query file path.",
)
@click.option(
    "--start",
    type=EpochType(),
    default=None,
    show_default="now minus --duration-seconds",
    help="Start of the reporting window, RFC3339 or Unix timestamp. The window ends at start plus --duration-seconds.",
)
@click.option("-e", "--endpoint", default="http://localhost:9090", show_default=True, help="Prometheus base URL.")
@click.option("-u", "--user", default="", help="Basic auth user for Prometheus.")
@click.option("-p", "--password", default="", help="Basic auth password for Prometheus.")
@click.option(
    "-f",
    "--format",
    "output_format",
    type=click.Choice(["json", "csv", "tsv"]),
    default="json",
    show_default=True,
    help="Output format.",
)
@click.option("--no-header", is_flag=True, help="Omit the CSV/TSV header row.")
@click.option("--missing-value", default="NaN", show_default=True, help="CSV/TSV placeholder for missing metrics.")
@click.option("-o", "--output", default=None, help="Write output to a file instead of stdout.")
def report(
    namespace_argument: str | None,
    namespace_option: str | None,
    duration_seconds: int,
    rate_interval: str,
    sample_step: str,
    queries: Path,
    start: datetime | None,
    endpoint: str,
    user: str,
    password: str,
    output_format: str,
    no_header: bool,
    missing_value: str,
    output: str | None,
) -> None:
    if namespace_argument and namespace_option and namespace_argument != namespace_option:
        raise click.UsageError(f"conflicting namespaces '{namespace_argument}' and '{namespace_option}'.")
    namespace = namespace_argument or namespace_option
    if namespace is None:
        raise click.MissingParameter(param_type="argument", param_hint="'NAMESPACE'")
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
                output_file=Path(output) if output else None,
            )
        )
    except ValidationError as error:
        print(f"Error: query document is invalid: {error}", file=sys.stderr)
        raise click.exceptions.Exit(1) from error
    except ReportError as error:
        print(f"Error: {error}", file=sys.stderr)
        raise click.exceptions.Exit(1) from error
