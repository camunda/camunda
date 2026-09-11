"""Query-file loading, substitution, and validation."""

from __future__ import annotations

import json
from collections.abc import Mapping
from pathlib import Path
from typing import Any

from .errors import ReportError

DEFAULT_QUERIES = "camunda"
BUILTIN_QUERY_FILES = {
    "camunda": "report-queries.yaml",
    "stable-87": "report-queries-stable-87.yaml",
}


def resolve_queries_file(script_dir: Path, queries: str) -> Path:
    if queries in BUILTIN_QUERY_FILES:
        return script_dir / BUILTIN_QUERY_FILES[queries]

    queries_file = Path(queries)
    if queries_file.is_file():
        return queries_file

    if queries_file.exists():
        raise ReportError(f"--queries '{queries}' must be a file.")

    supported_queries = ", ".join(BUILTIN_QUERY_FILES)
    raise ReportError(
        f"Unsupported --queries '{queries}'. Expected a built-in name ({supported_queries}) or an existing file."
    )


def load_query_document(queries_file: Path, substitutions: Mapping[str, str]) -> Mapping[str, Any]:
    parsed = parse_query_file(queries_file, substitutions)
    if not isinstance(parsed, Mapping):
        raise ReportError(f"queries file {queries_file} must contain a JSON/YAML object.")
    validate_query_document(parsed, f"queries file {queries_file}")
    return parsed


def parse_query_file(queries_file: Path, substitutions: Mapping[str, str]) -> Any:
    raw_document = substitute_query_text(queries_file.read_text(encoding="utf-8"), substitutions)
    if queries_file.suffix.lower() == ".json":
        try:
            return json.loads(raw_document)
        except json.JSONDecodeError as error:
            raise ReportError(f"Could not parse queries file {queries_file}: {error}") from error

    try:
        import yaml
    except ImportError as error:
        raise ReportError("PyYAML is required to read YAML query files. Install it with uv.") from error
    try:
        return yaml.safe_load(raw_document)
    except yaml.YAMLError as error:
        raise ReportError(f"Could not parse queries file {queries_file}: {error}") from error


def validate_query_document(
    query_document: Mapping[str, Any], source: str = "query document"
) -> list[Mapping[str, Any]]:
    queries = query_document.get("queries")
    if not isinstance(queries, list) or not queries:
        raise ReportError(f"{source} must contain a non-empty 'queries' list.")

    validated_queries: list[Mapping[str, Any]] = []
    seen_keys: set[str] = set()
    for index, query in enumerate(queries):
        if not isinstance(query, Mapping):
            raise ReportError(f"query entry {index} must be an object.")

        key = query.get("key")
        if not isinstance(key, str) or not key:
            raise ReportError("each query entry must have a non-empty string key.")
        if key in seen_keys:
            raise ReportError(f"duplicate query key: {key}")
        seen_keys.add(key)

        has_query = is_non_empty_string(query.get("query"))
        has_value = is_non_empty_string(query.get("value"))
        if has_query == has_value:
            raise ReportError(f"query entry {key} must set exactly one of query or value.")

        value_label = query.get("valueLabel")
        if value_label is not None:
            if not is_non_empty_string(value_label):
                raise ReportError(f"query entry {key} has an invalid valueLabel.")
            if not has_query:
                raise ReportError(f"query entry {key} sets valueLabel without query.")

        validated_queries.append(query)

    return validated_queries


def is_non_empty_string(value: Any) -> bool:
    return isinstance(value, str) and len(value) > 0


def query_substitutions(options: Any) -> Mapping[str, str]:
    return {
        "$NAMESPACE": options.namespace,
        "$DURATION_S": f"{options.duration_seconds}s",
        "$RATE_INTERVAL": options.rate_interval,
        "$SAMPLE_STEP": options.sample_step,
    }


def substitute_query_text(value: str, substitutions: Mapping[str, str]) -> str:
    rendered = value
    for placeholder, replacement in substitutions.items():
        rendered = rendered.replace(placeholder, replacement)
    return rendered
