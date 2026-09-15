"""Report construction and rendering."""

import csv
import io
import json
import re
import sys
from collections.abc import Callable
from collections.abc import Mapping
from datetime import UTC
from datetime import datetime
from typing import Any

from pydantic import ValidationError

from .errors import MissingMetric
from .errors import ReportError
from .prometheus import PrometheusResponse
from .queries import QueriesDocument

NUMBER_PATTERN = re.compile(r"^-?([0-9]+([.][0-9]+)?|[.][0-9]+)([eE][-+]?[0-9]+)?$")
MetricValue = str | int | float
WarningSink = Callable[[str], None]


def warn(message: str) -> None:
    print(f"Warning: {message}", file=sys.stderr)


def missing_metric(key: str, reason: str, warning_sink: WarningSink) -> None:
    warning_sink(f"{key}: {reason}")
    raise MissingMetric(reason)


def extract_metric_value(
    response: PrometheusResponse | Mapping[str, Any],
    value_label: str,
    key: str,
    warning_sink: WarningSink = warn,
) -> MetricValue:
    try:
        parsed_response = (
            response if isinstance(response, PrometheusResponse) else PrometheusResponse.model_validate(response)
        )
    except ValidationError as error:
        missing_metric(key, f"invalid Prometheus response: {error}", warning_sink)

    if parsed_response.status != "success":
        error_type = parsed_response.error_type
        error_message = parsed_response.error
        reason = "Prometheus returned non-success status"
        if error_type or error_message:
            reason += f": {error_type or 'error'}: {error_message or 'unknown error'}"
        missing_metric(key, reason, warning_sink)

    if parsed_response.data is None:
        missing_metric(key, "no result data", warning_sink)
    data = parsed_response.data
    result = data.result

    if value_label:
        if not isinstance(result, list):
            missing_metric(key, "no label sample", warning_sink)
        values = sorted({str(series.metric[value_label]) for series in result if value_label in series.metric})
        if not values:
            missing_metric(key, "no label sample", warning_sink)
        return ", ".join(values)

    raw_value = ""
    if data.result_type in ("scalar", "string") and isinstance(result, tuple):
        raw_value = str(result[1])
    elif isinstance(result, list) and result:
        raw_value = result[0].value[1]

    if not NUMBER_PATTERN.fullmatch(raw_value):
        missing_metric(key, "no numeric sample", warning_sink)
    return parse_number(raw_value)


def parse_number(raw_value: str) -> int | float:
    if re.fullmatch(r"-?[0-9]+", raw_value):
        return int(raw_value)
    return float(raw_value)


def build_report(
    options: Any,
    query_document: QueriesDocument,
    client: Any,
    generated_at: str | None = None,
    warning_sink: WarningSink = warn,
) -> Mapping[str, Any]:
    columns: list[str] = []
    headers: list[str] = []
    metrics: dict[str, MetricValue | None] = {}

    for query in query_document.queries:
        key = query.key
        columns.append(key)
        headers.append(query.header)

        try:
            response = client.query(query.query)
            metrics[key] = extract_metric_value(response, query.value_label or "", key, warning_sink)
        except MissingMetric:
            metrics[key] = None
        except ReportError as error:
            metrics[key] = None
            warning_sink(f"{key}: {error}")

    return {
        "namespace": options.namespace,
        "durationSeconds": options.duration_seconds,
        "start": options.start_label or None,
        "end": options.end_label or None,
        "endpoint": options.endpoint,
        "generatedAt": generated_at or datetime.now(UTC).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "columns": columns,
        "headers": headers,
        "metrics": metrics,
    }


def render_report(report: Mapping[str, Any], output_format: str, include_header: bool, missing_value: str) -> str:
    if output_format == "json":
        return json.dumps(report, indent=2)

    delimiter = "," if output_format == "csv" else "\t"
    output = io.StringIO()
    writer = csv.writer(output, delimiter=delimiter, lineterminator="\n")

    if include_header:
        writer.writerow(report["headers"])

    metrics = report["metrics"]
    row = [missing_value if metrics[column] is None else metrics[column] for column in report["columns"]]
    writer.writerow(row)
    return output.getvalue().rstrip("\n")
