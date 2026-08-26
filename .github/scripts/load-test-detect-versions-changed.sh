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
    # version tag path
    # `path` must be able to match files both in (for "stable-810"):
    #
    #   - load-tests/setup/stable-810/**
    #   - load-tests/setup/test/golden/stable-810/**
    #
    "main SNAPSHOT load-tests/setup/.*main/"
    "stable-810 8.10-SNAPSHOT load-tests/setup/.*stable-810/"
    "stable-89 8.9-SNAPSHOT load-tests/setup/.*stable-89/"
    "stable-88 8.8-SNAPSHOT load-tests/setup/.*stable-88/"
    "stable-87 8.7-SNAPSHOT load-tests/setup/.*stable-87/"
)

echo "Finding versions changed compared to ${PARENT}..."

changed="$(git diff --name-only "${PARENT}...HEAD")"

run_all=false

# Run all versions if the load-test workflow changed. This is a precaution
# because the workflow may have changed in a way that affects all versions.
if grep -qE '\.github/workflows/camunda-load-test' <<<"$changed"; then
    echo "⇒ Load-test workflow changed, running all versions..."
    run_all=true
fi

# The load-test-setup Helm Chart is used by all the versions. It may or may not
# affect the final output but as a precaution, we run all the versions if this
# Helm Chart changed.
if grep -qF 'load-tests/setup/charts/load-test-setup' <<<"$changed"; then
    echo "⇒ load-test-setup Helm Chart changed, will run all the versions..."
    run_all=true
fi

matrix_entries=()
for entry in "${VERSIONS[@]}"; do
    read -r version tag path <<<"$entry"
    if [[ "$run_all" == "true" ]] || grep -qE "$path" <<<"$changed"; then
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
