#!/usr/bin/env bash
# Discovers the maintained branches (main plus every stable/X.Y at or above
# MIN_VERSION) from the git remote and writes two JSON arrays to $GITHUB_OUTPUT
# for use with strategy.matrix via fromJSON().
#
# Prerequisites:
#   - The repository is checked out (e.g. actions/checkout) so the `origin`
#     remote is configured; this reads refs via `git ls-remote origin`.
#   - `jq` is available on PATH (present on GitHub-hosted runners).
#
# Inputs (environment):
#   MIN_VERSION  Minimum stable version to include, as MAJOR.MINOR (e.g. 8.8).
#
# The order of the emitted branches is intentionally NOT guaranteed. Callers that
# need a specific order must sort the result themselves.
set -euo pipefail

if ! command -v jq >/dev/null 2>&1; then
  echo "::error::list-maintained-branches requires 'jq', which was not found on PATH."
  exit 1
fi

if [[ ! "${MIN_VERSION:-}" =~ ^[0-9]+\.[0-9]+$ ]]; then
  echo "::error::Invalid min-version '${MIN_VERSION:-}'; expected MAJOR.MINOR (e.g. 8.8)."
  exit 1
fi

min_major="${MIN_VERSION%%.*}"
min_minor="${MIN_VERSION#*.}"

# List remote stable/X.Y branches and keep only those at or above MIN_VERSION.
# awk compares major/minor numerically, so 8.10 is correctly kept over 8.9.
stable_branches=$(
  git ls-remote --heads origin 'refs/heads/stable/*' \
    | sed -nE 's|.*refs/heads/(stable/[0-9]+\.[0-9]+)$|\1|p' \
    | awk -F/ -v maj="$min_major" -v min="$min_minor" \
        '{ split($2, v, "."); if (v[1] > maj || (v[1] == maj && v[2] >= min)) print }'
)

if [[ -z "$stable_branches" ]]; then
  echo "::error::No stable branch matched stable/X.Y >= ${MIN_VERSION}."
  exit 1
fi

to_json() {
  printf '%s\n' "$1" | jq -Rsc 'split("\n") | map(select(length > 0))'
}

stable_json=$(to_json "$stable_branches")
all_json=$(to_json "$(printf 'main\n%s\n' "$stable_branches")")

echo "Discovered branches: ${all_json}"
{
  echo "branches=${all_json}"
  echo "stable-branches=${stable_json}"
} >> "$GITHUB_OUTPUT"
