#!/bin/bash
# owner: @camunda/reliability-testing
#
# Detects which load-tests/setup/<version> folders changed compared to the PR's base branch
# and emits a GitHub Actions matrix (as the `matrix` step output) listing the versions to test.
# If the load-test workflows themselves changed, all versions are included.

set -o nounset
set -o errexit
set -o pipefail

GITHUB_BASE_REF="${GITHUB_BASE_REF:-main}"
GITHUB_OUTPUT="${GITHUB_OUTPUT:-/dev/stdout}"

PARENT="origin/${GITHUB_BASE_REF}"

# version orchestration-tag setup-path
VERSIONS=(
    "main SNAPSHOT load-tests/setup/main/"
    "stable-89 8.9-SNAPSHOT load-tests/setup/stable-89/"
    "stable-88 8.8-SNAPSHOT load-tests/setup/stable-88/"
    "stable-87 8.7-SNAPSHOT load-tests/setup/stable-87/"
)

echo "Finding versions changed compared to ${PARENT}..."

changed="$(git diff --name-only "${PARENT}...HEAD")"

run_all=false
if grep -qE '\.github/workflows/camunda-load-test' <<<"$changed"; then
    echo "⇒ Load-test workflow changed, running all versions..."
    run_all=true
fi

matrix_entries=()
for entry in "${VERSIONS[@]}"; do
    read -r version tag path <<<"$entry"
    if [[ "$run_all" == "true" ]] || grep -qF "$path" <<<"$changed"; then
        echo "⇒ Version '${version}' changed..."
        matrix_entries+=("{\"version\":\"${version}\",\"orchestration-tag\":\"${tag}\"}")
    fi
done

# Default: run main if nothing specific was detected
if [[ ${#matrix_entries[@]} -eq 0 ]]; then
    echo "⇒ Version 'main' will be run as default because no specific version was detected..."
    matrix_entries+=('{"version":"main","orchestration-tag":"SNAPSHOT"}')
fi

joined=$(IFS=,; echo "${matrix_entries[*]}")
echo "matrix={\"include\":[${joined}]}" >> "$GITHUB_OUTPUT"
