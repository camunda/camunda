from collections.abc import Mapping
from pathlib import Path
from typing import Self

import yaml
from pydantic import BaseModel
from pydantic import ConfigDict
from pydantic import Field
from pydantic import model_validator


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

    @classmethod
    def from_file(cls, queries_file: Path, substitutions: Mapping[str, str]) -> Self:
        raw_document = substitute_query_text(queries_file.read_text(encoding="utf-8"), substitutions)
        parsed = yaml.safe_load(raw_document)
        return cls.model_validate(parsed)


def substitute_query_text(value: str, substitutions: Mapping[str, str]) -> str:
    rendered = value
    for placeholder, replacement in substitutions.items():
        rendered = rendered.replace(placeholder, replacement)
    return rendered
