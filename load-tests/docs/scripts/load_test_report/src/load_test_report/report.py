"""Report construction and rendering."""

import csv
import io
import json
from collections.abc import Mapping
from datetime import UTC
from datetime import datetime
from typing import Any

from .errors import MissingMetric
from .errors import ReportError
from .prometheus import MetricValue
from .prometheus import PrometheusClient
from .queries import QueriesDocument


def build_report(
    options: Any,
    query_document: QueriesDocument,
    client: PrometheusClient,
    generated_at: str | None = None,
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
            metrics[key] = response.extract_metric_value(query.value_label or "", key)
        except MissingMetric:
            metrics[key] = None
        except ReportError:
            metrics[key] = None

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
