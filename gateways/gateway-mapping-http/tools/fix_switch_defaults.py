#!/usr/bin/env python3
"""Add default arms to switch expressions that lack them after enum->String conversion."""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SORT_MAPPER_PATH = os.path.join(
    ROOT,
    "src/main/java/io/camunda/gateway/mapping/http/search/SearchQuerySortRequestMapper.java"
)

content = open(SORT_MAPPER_PATH).read()
lines = content.split("\n")
i = 0
fixed = 0
while i < len(lines):
    if "return switch (field) {" in lines[i]:
        # Find the matching closing };
        j = i + 1
        depth = 1
        has_default = False
        while j < len(lines):
            line = lines[j].strip()
            if "default ->" in line or "default:" in line:
                has_default = True
            depth += line.count("{") - line.count("}")
            if depth == 0:
                break
            j += 1

        if not has_default:
            # Insert default arm before the closing };
            indent = "      "
            default_arm = indent + 'default -> List.of(ERROR_UNKNOWN_SORT_BY.formatted(field));'
            lines.insert(j, default_arm)
            fixed += 1
    i += 1

open(SORT_MAPPER_PATH, "w").write("\n".join(lines))
print(f"Fixed {fixed} switch expressions")
