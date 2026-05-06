#!/usr/bin/env python3
# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
# one or more contributor license agreements. See the NOTICE file distributed
# with this work for additional information regarding copyright ownership.
# Camunda licenses this file to you under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with the License.
# You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed
# under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
# CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
# language governing permissions and limitations under the License.

"""
OpenAPI spec validator for cross-referencing test failures against the API spec.

Called from analyze-playwright-results.py to add spec-based evidence to the
root-cause classification.

Two kinds of validation:
  1. Status-code validation (http_error / assertion on res.status()):
       Extract the expected and actual status codes, look up the endpoint in
       the spec, and decide whether the product or the test is wrong.

  2. Property-constraint validation (assertion on response body):
       Extract the property path being asserted (e.g. "brokers"), look up
       its schema in the spec, and flag when the assertion contradicts the
       schema (e.g. asserting an exact length on an unconstrained array).
"""

import re
from pathlib import Path
from typing import Optional

try:
    import yaml as _yaml
    _YAML_AVAILABLE = True
except ImportError:
    _YAML_AVAILABLE = False


# ---------------------------------------------------------------------------
# SpecValidator
# ---------------------------------------------------------------------------

class SpecValidator:
    """Wraps an OpenAPI 3.x spec loaded from YAML for validation lookups."""

    def __init__(self, spec_path: Path):
        if not _YAML_AVAILABLE:
            raise ImportError("pyyaml is required for spec validation (pip install pyyaml)")
        with spec_path.open(encoding="utf-8") as f:
            self._spec = _yaml.safe_load(f)
        self._ref_cache: dict[str, dict] = {}

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------

    def _resolve_ref(self, ref: str) -> dict:
        if ref not in self._ref_cache:
            parts = ref.lstrip("#/").split("/")
            node = self._spec
            for p in parts:
                node = node[p]
            self._ref_cache[ref] = node
        return self._ref_cache[ref]

    def _resolve_schema(self, schema: dict, depth: int = 0) -> dict:
        """Recursively resolve $ref links (capped at 6 levels to avoid cycles)."""
        if depth > 6 or not isinstance(schema, dict):
            return schema
        if "$ref" in schema:
            return self._resolve_schema(self._resolve_ref(schema["$ref"]), depth + 1)
        return schema

    def _match_spec_path(self, request_path: str) -> Optional[str]:
        """Map a concrete URL path to its OpenAPI path template."""
        paths = self._spec.get("paths", {})
        # Exact match
        if request_path in paths:
            return request_path
        # Template match: /processes/{processDefinitionKey} → /processes/123
        for spec_path in paths:
            pattern = re.sub(r"\{[^}]+\}", "[^/]+", re.escape(spec_path)) + "$"
            if re.fullmatch(pattern, request_path):
                return spec_path
        return None

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def get_operation(self, path: str, method: str) -> Optional[dict]:
        """Return the OpenAPI operation object for a path/method pair, or None."""
        spec_path = self._match_spec_path(path)
        if spec_path is None:
            return None
        return self._spec["paths"][spec_path].get(method.lower())

    def get_valid_status_codes(self, path: str, method: str) -> set[str]:
        """Return all status codes defined in the spec for this operation."""
        op = self.get_operation(path, method)
        if not op:
            return set()
        return set(op.get("responses", {}).keys())

    def get_response_schema(self, path: str, method: str, status: str) -> Optional[dict]:
        """Return the resolved JSON schema for a response body."""
        op = self.get_operation(path, method)
        if not op:
            return None
        resp = op.get("responses", {}).get(str(status))
        if not resp:
            return None
        json_schema = resp.get("content", {}).get("application/json", {}).get("schema")
        if json_schema:
            return self._resolve_schema(json_schema)
        return None

    def validate_status_expectation(
        self,
        path: str,
        method: str,
        expected: int,
        actual: int,
    ) -> Optional[tuple[str, str]]:
        """
        Check whether an expected-vs-actual status mismatch is a test or product issue.

        Returns (verdict, reason) or None when the spec has no relevant info.
        verdict: "test_code" | "product_regression"
        """
        valid = self.get_valid_status_codes(path, method)
        if not valid:
            return None

        expected_s, actual_s = str(expected), str(actual)
        expected_in = expected_s in valid
        actual_in = actual_s in valid
        op_label = f"{method.upper()} {path}"

        if expected_in and not actual_in:
            return (
                "product_regression",
                f"spec defines {expected} as a valid response for {op_label} "
                f"but product returned {actual}, which is not in the spec",
            )
        if actual_in and not expected_in:
            return (
                "test_code",
                f"spec defines {actual} as a valid response for {op_label} "
                f"but the test expected {expected}, which is not in the spec",
            )
        return None

    def check_length_assertion(
        self,
        path: str,
        method: str,
        status: int,
        prop_dotpath: str,
        asserted_length: int,
    ) -> Optional[tuple[str, str]]:
        """
        Check whether asserting a fixed length on a property contradicts the spec.

        Returns (verdict, reason) or None when the spec has no relevant info.
        """
        schema = self.get_response_schema(path, method, str(status))
        if not schema:
            return None

        # Walk the dot-path into the schema
        parts = prop_dotpath.split(".")
        node = schema
        for part in parts:
            if not isinstance(node, dict):
                return None
            props = node.get("properties", {})
            if part in props:
                node = self._resolve_schema(props[part])
            else:
                return None

        if not isinstance(node, dict) or node.get("type") != "array":
            return None

        min_items = node.get("minItems")
        max_items = node.get("maxItems")
        op_label = f"{method.upper()} {path}"

        if min_items is None and max_items is None:
            return (
                "test_code",
                f"spec does not constrain the length of '{prop_dotpath}' "
                f"in the {op_label} 200 response — asserting exact length "
                f"{asserted_length} is a test bug",
            )
        if max_items is not None and asserted_length > max_items:
            return (
                "test_code",
                f"spec sets maxItems={max_items} for '{prop_dotpath}' in "
                f"{op_label} response but test asserted length {asserted_length}",
            )
        if min_items is not None and asserted_length < min_items:
            return (
                "test_code",
                f"spec sets minItems={min_items} for '{prop_dotpath}' in "
                f"{op_label} response but test asserted length {asserted_length}",
            )
        return None


# ---------------------------------------------------------------------------
# Test source-file parser
# ---------------------------------------------------------------------------

def _find_test_body(content: str, leaf_title: str) -> Optional[str]:
    """Extract the arrow-function body of the test with the given title."""
    escaped = re.escape(leaf_title)
    open_re = re.compile(
        r"""test(?:\.only)?\s*\(\s*['"]""" + escaped + r"""['"]\s*,\s*async\s*\([^)]*\)\s*=>\s*\{""",
        re.DOTALL,
    )
    m = open_re.search(content)
    if not m:
        return None

    body_start = m.end()
    depth = 1
    i = body_start
    while i < len(content) and depth > 0:
        c = content[i]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
        i += 1
    return content[body_start : i - 1]


def extract_api_calls(file_path: Path, leaf_title: str) -> dict:
    """
    Parse a Playwright test source file and return info about the API calls in
    the named test.

    Returns a dict with:
      endpoints        – list of path strings, e.g. ['/topology']
      methods          – list of HTTP methods, e.g. ['get']
      expected_statuses – list of ints, e.g. [200]
    """
    try:
        content = file_path.read_text(encoding="utf-8")
    except OSError:
        return {}

    body = _find_test_body(content, leaf_title)
    if body is None:
        return {}

    endpoints = list(dict.fromkeys(
        re.findall(r"""buildUrl\s*\(\s*['"]([^'"]+)['"]""", body)
    ))
    methods = list(dict.fromkeys(
        re.findall(r"""request\.(get|post|put|delete|patch)\s*\(""", body)
    ))

    # assertStatusCode(res, 200)  OR  expect(res.status()).toBe(200)
    raw_statuses = re.findall(
        r"""assertStatusCode\s*\(\s*\w+\s*,\s*(\d{3})"""
        r"""|\.status\s*\(\s*\)\s*[^;]*?\.toBe\s*\(\s*(\d{3})\s*\)""",
        body,
    )
    expected_statuses = list(dict.fromkeys(
        int(a or b) for a, b in raw_statuses if a or b
    ))

    return {
        "endpoints": endpoints,
        "methods": methods,
        "expected_statuses": expected_statuses,
    }


# ---------------------------------------------------------------------------
# Error-message parsers
# ---------------------------------------------------------------------------

_STATUS_FROM_ERROR_RE = re.compile(
    r"""(?:status\s*(?:code\s*)?|got\s+|[Rr]eceived[:\s]+)(\d{3})\b""",
)


def extract_status_from_error(error_msg: str) -> Optional[int]:
    """Try to extract the actual HTTP status code from a Playwright error string."""
    m = _STATUS_FROM_ERROR_RE.search(error_msg)
    if m:
        return int(m.group(1))
    return None


_LENGTH_ASSERT_RE = re.compile(
    r"""toHaveLength\s*\(\s*(\d+)\s*\)|toHaveCount\s*\(\s*(\d+)\s*\)"""
    r"""|Expected length[:\s]+(\d+)|Expected count[:\s]+(\d+)""",
    re.IGNORECASE,
)


def extract_asserted_length(error_msg: str) -> Optional[int]:
    """Try to extract the length value from a toHaveLength/toHaveCount error."""
    m = _LENGTH_ASSERT_RE.search(error_msg)
    if m:
        val = m.group(1) or m.group(2) or m.group(3) or m.group(4)
        if val:
            return int(val)
    return None
