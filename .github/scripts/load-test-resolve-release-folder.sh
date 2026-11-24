#!/usr/bin/env bash
# owner: @camunda/reliability-testing
#
# Prints the load-tests/setup folder that holds the versions.yaml file for a release tag.
#
# Usage: load-test-resolve-release-folder.sh <tag> [setup-dir]
#   tag         Release tag, for example 8.8.47, 8.10, 8.11.0-alpha1 or 8.11.0-rc2
#   setup-dir   Directory that holds the folders (default: load-tests/setup)
#
# A tag 8.<minor> or 8.<minor>.<patch> uses stable-8<minor> when that folder exists.
# A tag with a pre-release suffix, or without a folder, uses main.

set -euo pipefail

tag="${1:-}"
setup_dir="${2:-load-tests/setup}"

if [[ ! "${tag}" =~ ^8\.([0-9]+)(\.[0-9]+)?(-[0-9A-Za-z.-]+)?$ ]]; then
  echo "::error::Malformed tag '${tag}'. Expected 8.<minor>[.<patch>][-<suffix>]." >&2
  exit 1
fi

minor="${BASH_REMATCH[1]}"
suffix="${BASH_REMATCH[3]}"

if [[ -z "${suffix}" && -d "${setup_dir}/stable-8${minor}" ]]; then
  echo "stable-8${minor}"
else
  echo "main"
fi
