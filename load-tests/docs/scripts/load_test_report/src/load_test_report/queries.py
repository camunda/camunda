"""Query-file loading, substitution, and validation."""

import json
from collections.abc import Mapping
from pathlib import Path
from typing import Any
from typing import Self

import yaml
from pydantic import BaseModel
from pydantic import ConfigDict
from pydantic import Field
from pydantic import ValidationError
from pydantic import model_validator

from .errors import ReportError

DEFAULT_QUERIES = "camunda"
BUILTIN_QUERY_FILES = {
    "camunda": "report-queries.yaml",
    "stable-87": "report-queries-stable-87.yaml",
}


class Query(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)

    key: str = Field(min_length=1)
    description: str = Field(min_length=1)
    header: str = Field(min_length=1)
    query: str = Field(min_length=1)
    value_label: str | None = Field(default=None, alias="valueLabel", min_length=1)


class QueriesDocument(BaseModel):
    model_config = ConfigDict(extra="forbid")

    queries: list[Query] = Field(min_length=1)

    @model_validator(mode="after")
    def reject_duplicate_keys(self) -> Self:
        seen_keys: set[str] = set()
        for query in self.queries:
            if query.key in seen_keys:
                raise ValueError(f"duplicate query key: {query.key}")
            seen_keys.add(query.key)
        return self


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


def load_query_document(queries_file: Path, substitutions: Mapping[str, str]) -> QueriesDocument:
    parsed = parse_query_file(queries_file, substitutions)
    return validate_query_document(parsed, f"queries file {queries_file}")


def parse_query_file(queries_file: Path, substitutions: Mapping[str, str]) -> Any:
    raw_document = substitute_query_text(queries_file.read_text(encoding="utf-8"), substitutions)
    if queries_file.suffix.lower() == ".json":
        try:
            return json.loads(raw_document)
        except json.JSONDecodeError as error:
            raise ReportError(f"Could not parse queries file {queries_file}: {error}") from error

    try:
        return yaml.safe_load(raw_document)
    except yaml.YAMLError as error:
        raise ReportError(f"Could not parse queries file {queries_file}: {error}") from error


def validate_query_document(query_document: Any, source: str = "query document") -> QueriesDocument:
    if isinstance(query_document, QueriesDocument):
        return query_document
    try:
        return QueriesDocument.model_validate(query_document)
    except ValidationError as error:
        raise ReportError(f"{source} is invalid: {error}") from error


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
