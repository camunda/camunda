"""Report construction and rendering."""

from __future__ import annotations

import csv
import io
import json
import re
import sys
from collections.abc import Callable, Mapping
from datetime import datetime, timezone
from typing import Any, Optional, Union

from errors import MissingMetric, ReportError
from queries import validate_query_document

NUMBER_PATTERN = re.compile(r"^-?([0-9]+([.][0-9]+)?|[.][0-9]+)([eE][-+]?[0-9]+)?$")
MetricValue = Union[str, int, float]
WarningSink = Callable[[str], None]


def warn(message: str) -> None:
    print(f"Warning: {message}", file=sys.stderr)


def missing_metric(key: str, reason: str, warning_sink: WarningSink) -> None:
    warning_sink(f"{key}: {reason}")
    raise MissingMetric(reason)


def extract_metric_value(
    response: Mapping[str, Any],
    value_label: str,
    key: str,
    warning_sink: WarningSink = warn,
) -> MetricValue:
    if response.get("status") != "success":
        missing_metric(key, "Prometheus returned non-success status", warning_sink)

    data = response.get("data", {})
    result = data.get("result", []) if isinstance(data, Mapping) else []
    if not isinstance(result, list):
        result = []

    if value_label:
        values = sorted(
            {
                str(series.get("metric", {}).get(value_label))
                for series in result
                if isinstance(series, Mapping)
                and isinstance(series.get("metric"), Mapping)
                and series["metric"].get(value_label) is not None
            }
        )
        if not values:
            missing_metric(key, "no label sample", warning_sink)
        return ", ".join(values)

    raw_value = ""
    if result and isinstance(result[0], Mapping):
        sample = result[0].get("value", [])
        if isinstance(sample, list) and len(sample) > 1:
            raw_value = str(sample[1])

    if not NUMBER_PATTERN.fullmatch(raw_value):
        missing_metric(key, "no numeric sample", warning_sink)
    return parse_number(raw_value)


def parse_number(raw_value: str) -> Union[int, float]:
    if re.fullmatch(r"-?[0-9]+", raw_value):
        return int(raw_value)
    return float(raw_value)


def build_report(
    options: Any,
    query_document: Mapping[str, Any],
    client: Any,
    generated_at: Optional[str] = None,
    warning_sink: WarningSink = warn,
) -> Mapping[str, Any]:
    queries = validate_query_document(query_document)

    columns: list[str] = []
    headers: list[str] = []
    metrics: dict[str, Optional[MetricValue]] = {}

    for query in queries:
        key = query["key"]
        columns.append(key)
        headers.append(str(query.get("header", key)))

        if "value" in query and query["value"] is not None:
            metrics[key] = str(query["value"])
            continue

        try:
            response = client.query(str(query["query"]))
            metrics[key] = extract_metric_value(response, str(query.get("valueLabel", "")), key, warning_sink)
        except MissingMetric:
            metrics[key] = None
        except ReportError:
            metrics[key] = None
            warning_sink(f"{key}: query failed")

    return {
        "namespace": options.namespace,
        "durationSeconds": options.duration_seconds,
        "start": options.start_label or None,
        "end": options.end_label or None,
        "endpoint": options.endpoint,
        "generatedAt": generated_at or datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
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
