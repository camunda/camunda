#!/bin/bash
# owner: @camunda/reliability-testing

set -o nounset

GITHUB_BASE_REF="${GITHUB_BASE_REF:-main}"
GITHUB_OUTPUT="${GITHUB_OUTPUT:-/dev/stdout}"

PARENT="origin/${GITHUB_BASE_REF}"

echo "⏳ Finding versions changed compared to ${PARENT}..."

changed="$(git diff --name-only "${PARENT}...HEAD")"

run_all=false
if echo "$changed" | grep -qE '\.github/workflows/camunda-load-test'; then
    run_all=true
fi

matrix_entries=()

if [[ "$run_all" == "true" ]] || echo "$changed" | grep -q 'load-tests/setup/main/'; then
    echo "✅ Version 'main' changed..."
    matrix_entries+=('{"version":"main","orchestration-tag":"SNAPSHOT"}')
fi

if [[ "$run_all" == "true" ]] || echo "$changed" | grep -q 'load-tests/setup/stable-89/'; then
    echo "✅ Version 8.9 changed..."
    matrix_entries+=('{"version":"stable-89","orchestration-tag":"8.9-SNAPSHOT"}')
fi
if [[ "$run_all" == "true" ]] || echo "$changed" | grep -q 'load-tests/setup/stable-88/'; then
    echo "✅ Version 8.8 changed..."
    matrix_entries+=('{"version":"stable-88","orchestration-tag":"8.8-SNAPSHOT"}')
fi
if [[ "$run_all" == "true" ]] || echo "$changed" | grep -q 'load-tests/setup/stable-87/'; then
    echo "✅ Version 8.7 changed..."
    matrix_entries+=('{"version":"stable-87","orchestration-tag":"8.7-SNAPSHOT"}')
fi

# Default: run main if nothing specific was detected
if [[ ${#matrix_entries[@]} -eq 0 ]]; then
    echo "✅ Version 'main' will be run as default because no specific version was detected..."
    matrix_entries+=('{"version":"main","orchestration-tag":"SNAPSHOT"}')
fi

joined=$(IFS=,; echo "${matrix_entries[*]}")
echo "matrix={\"include\":[${joined}]}" >> "$GITHUB_OUTPUT"
