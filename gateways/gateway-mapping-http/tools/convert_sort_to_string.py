#!/usr/bin/env python3
"""
Convert SearchQuerySortRequestMapper from FieldEnum-based to String-based.

This script:
1. Reads all protocol model *SortRequest.java files to extract FieldEnum constant -> JSON value mappings
2. Reads the SearchQuerySortRequestMapper.java file
3. Rewrites it to use String field values in switch statements instead of FieldEnum constants
4. Changes method parameters from FieldEnum to String
5. Changes SearchQuerySortRequest to be non-generic with String field values
6. Changes applySortOrder to use String instead of SortOrderEnum
"""
import re
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CAMUNDA_ROOT = os.path.dirname(os.path.dirname(ROOT))
PROTO_MODEL_DIR = os.path.join(
    CAMUNDA_ROOT,
    "gateways/gateway-model/target/generated-sources/openapi/src/main/io/camunda/gateway/protocol/model"
)
SORT_MAPPER_PATH = os.path.join(
    ROOT,
    "src/main/java/io/camunda/gateway/mapping/http/search/SearchQuerySortRequestMapper.java"
)
SORT_REQUEST_PATH = os.path.join(
    ROOT,
    "src/main/java/io/camunda/gateway/mapping/http/search/SearchQuerySortRequest.java"
)

def extract_enum_maps():
    """Extract FieldEnum constant -> JSON value mappings from protocol model."""
    enum_maps = {}
    for f in sorted(os.listdir(PROTO_MODEL_DIR)):
        if "SortRequest" in f and f.endswith(".java"):
            path = os.path.join(PROTO_MODEL_DIR, f)
            content = open(path).read()
            match = re.search(r"public enum FieldEnum \{(.*?)\n\s+private", content, re.DOTALL)
            if match:
                pairs = re.findall(r'(\w+)\("([^"]+)"\)', match.group(1))
                class_name = f.replace(".java", "")
                enum_maps[class_name] = dict(pairs)

    # Also extract SortOrderEnum
    sort_order_path = os.path.join(PROTO_MODEL_DIR, "SortOrderEnum.java")
    if os.path.exists(sort_order_path):
        content = open(sort_order_path).read()
        match = re.search(r"public enum SortOrderEnum \{(.*?)\n\s+private", content, re.DOTALL)
        if match:
            pairs = re.findall(r'(\w+)\("([^"]+)"\)', match.group(1))
            enum_maps["SortOrderEnum"] = dict(pairs)

    return enum_maps


def convert_sort_request_record():
    """Convert SearchQuerySortRequest from generic to String-based."""
    new_content = """/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search;

public record SearchQuerySortRequest(String field, String order) {}
"""
    with open(SORT_REQUEST_PATH, "w") as f:
        f.write(new_content)
    print(f"Updated: {SORT_REQUEST_PATH}")


def convert_sort_mapper(enum_maps):
    """Convert SearchQuerySortRequestMapper to use String-based field matching."""
    content = open(SORT_MAPPER_PATH).read()
    original_content = content

    # ---- Pass 1: Collect all method info BEFORE modifying ----
    apply_pattern = re.compile(
        r"static List<String> (apply\w+SortField)\(\s*\n?\s*"
        r"final (\w+(?:\.\w+)*) field,",
        re.MULTILINE
    )

    method_info = []  # list of (method_name, field_type, sort_class, mapping)
    for match in apply_pattern.finditer(content):
        method_name = match.group(1)
        field_type = match.group(2)
        sort_class = field_type.replace(".FieldEnum", "") if ".FieldEnum" in field_type else field_type
        mapping = enum_maps.get(sort_class, {})
        if not mapping:
            print(f"  WARNING: No enum mapping found for {sort_class} in method {method_name}")
        method_info.append((method_name, field_type, sort_class, mapping))

    print(f"  Found {len(method_info)} applyXxxSortField methods to convert")

    # ---- Pass 2: Apply all changes ----

    # Step 1: Remove protocol model imports for SortRequest types and SortOrderEnum
    lines = content.split("\n")
    new_lines = []
    for line in lines:
        if "import io.camunda.gateway.protocol.model." in line and "SortRequest" in line:
            continue
        if "import io.camunda.gateway.protocol.model.SortOrderEnum" in line:
            continue
        if "import static" in line and "FieldEnum" in line:
            continue
        new_lines.append(line)
    content = "\n".join(new_lines)

    # Step 2: Convert each applyXxxSortField — change parameter type + switch cases
    for method_name, field_type, sort_class, mapping in method_info:
        # Replace parameter type
        old_param = f"final {field_type} field"
        content = content.replace(old_param, "final String field", 1)

        # Replace enum case constants with string literals
        for enum_const, json_value in mapping.items():
            old_case = f"case {enum_const} ->"
            new_case = f'case "{json_value}" ->'
            content = content.replace(old_case, new_case)

    # Step 3: Convert fromXxx methods — extract string values from protocol model getters
    content = content.replace(
        "r -> createFrom(r.getField(), r.getOrder())",
        "r -> createFrom(\n            r.getField() != null ? r.getField().getValue() : null,\n            r.getOrder() != null ? r.getOrder().getValue() : null)"
    )

    # Step 4: Change createFrom signature
    content = content.replace(
        "private static <T> SearchQuerySortRequest<T> createFrom(\n      final T field, final SortOrderEnum order) {\n    return new SearchQuerySortRequest<T>(field, order);",
        'private static SearchQuerySortRequest createFrom(\n      final String field, final String order) {\n    return new SearchQuerySortRequest(field, order);'
    )

    # Step 5: Change toSearchQuerySort signature — remove F type parameter
    content = content.replace(
        "static <T, B extends SortOption.AbstractBuilder<B> & ObjectBuilder<T>, F>\n      Either<List<String>, T> toSearchQuerySort(\n          final List<SearchQuerySortRequest<F>> sorting,\n          final Supplier<B> builderSupplier,\n          final BiFunction<F, B, List<String>> sortFieldMapper)",
        "static <T, B extends SortOption.AbstractBuilder<B> & ObjectBuilder<T>>\n      Either<List<String>, T> toSearchQuerySort(\n          final List<SearchQuerySortRequest> sorting,\n          final Supplier<B> builderSupplier,\n          final BiFunction<String, B, List<String>> sortFieldMapper)"
    )
    content = content.replace(
        "for (final SearchQuerySortRequest<F> sort : sorting)",
        "for (final SearchQuerySortRequest sort : sorting)"
    )

    # Step 6: Change applySortOrder from SortOrderEnum to String
    content = content.replace(
        "private static void applySortOrder(\n      final SortOrderEnum order, final SortOption.AbstractBuilder<?> builder) {\n    if (order == SortOrderEnum.DESC) {",
        'private static void applySortOrder(\n      final String order, final SortOption.AbstractBuilder<?> builder) {\n    if ("desc".equals(order)) {'
    )

    # Step 7: Change all fromXxx return types from generic to non-generic
    content = re.sub(
        r"List<SearchQuerySortRequest<[\w.]+>>",
        "List<SearchQuerySortRequest>",
        content
    )
    # Also handle multi-line return type patterns
    content = re.sub(
        r"List<\s*\n\s*SearchQuerySortRequest<\s*\n?\s*[\w.]+>>",
        "List<SearchQuerySortRequest>",
        content
    )

    # Step 8: Handle GlobalTaskListener special case
    # This method constructs protocol model objects with enum references
    # Replace FieldEnum references with string literals
    content = content.replace("FieldEnum.AFTER_NON_GLOBAL", '"afterNonGlobal"')
    content = content.replace("FieldEnum.PRIORITY", '"priority"')
    content = content.replace("FieldEnum.ID", '"id"')
    content = content.replace("SortOrderEnum.DESC", '"desc"')

    # Replace new GlobalTaskListenerSearchQuerySortRequest(...) with SearchQuerySortRequest
    content = re.sub(
        r'new GlobalTaskListenerSearchQuerySortRequest\("afterNonGlobal"\)',
        'new SearchQuerySortRequest("afterNonGlobal", null)',
        content
    )
    content = re.sub(
        r'new GlobalTaskListenerSearchQuerySortRequest\("priority"\)\s*\n?\s*\.order\("desc"\)',
        'new SearchQuerySortRequest("priority", "desc")',
        content
    )
    content = re.sub(
        r'new GlobalTaskListenerSearchQuerySortRequest\("id"\)',
        'new SearchQuerySortRequest("id", null)',
        content
    )

    with open(SORT_MAPPER_PATH, "w") as f:
        f.write(content)

    # Report changes
    old_lines = original_content.split("\n")
    new_lines_list = content.split("\n")
    changes = sum(1 for a, b in zip(old_lines, new_lines_list) if a != b)
    print(f"Updated: {SORT_MAPPER_PATH}")
    print(f"  Changed ~{changes} lines, {len(new_lines_list) - len(old_lines)} net new lines")


def main():
    print("Extracting enum mappings from protocol model...")
    enum_maps = extract_enum_maps()
    print(f"  Found {len(enum_maps)} sort request types with FieldEnum mappings")
    for name, mapping in sorted(enum_maps.items()):
        if name != "SortOrderEnum":
            print(f"    {name}: {len(mapping)} values")

    print("\nConverting SearchQuerySortRequest record...")
    convert_sort_request_record()

    print("\nConverting SearchQuerySortRequestMapper...")
    convert_sort_mapper(enum_maps)

    print("\nDone!")


if __name__ == "__main__":
    main()
