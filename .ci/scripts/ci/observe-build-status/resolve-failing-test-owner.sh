#!/usr/bin/env bash
# Resolves the owning GitHub team for the failing test classes of the current job,
# using `.codeowners` as the source of truth (via codeowners-cli).
#
# This powers code-ownership-based alert attribution for auto-created CI incidents:
# instead of always attributing a red job to its static `TEST_OWNER`, we attribute it
# to the team that actually owns the failing tests whenever that is unambiguous.
#
# Strict attribution rule (see docs/monorepo-docs/ci.md, "Automatic Alert Attribution"):
#   Prints the resolved owner on stdout ONLY when there is at least one failing test
#   class AND every failing test class resolves to the same non-empty owner. In every
#   other case (no failing test class, an unresolvable test class, or mixed ownership
#   across the failing tests) it prints nothing, so the caller falls back to the job's
#   static `TEST_OWNER`. This avoids confidently misrouting an incident to the wrong team.
#
# Requires: codeowners-cli on PATH, python3, jq and git. The test reports (TEST-*.xml)
# must be present on disk. The script resolves the repository root itself and scans from
# there, so it is independent of the caller's working directory.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || echo ".")"

# Scan from the repository root regardless of the caller's CWD, so reports are not missed.
cd "${REPO_ROOT}"

# Reuse the FQCN -> source file -> codeowners team resolvers.
# shellcheck source=/dev/null
source "${REPO_ROOT}/.ci/scripts/ci/setup-medic-lookup.sh"

# Collect the distinct test classes that actually failed. The JUnit parser tags
# flaky-but-passed retries as "flaky" and drops the passing occurrence, so filtering
# on "failure"/"error" here excludes flaky, skipped and passing tests.
mapfile -t failing_classes < <(
  find . -iname 'TEST-*.xml' \
    | python3 "${SCRIPT_DIR}/junit-test-results-to-jsonl.py" \
    | jq -r 'select(.test_status == "failure" or .test_status == "error") | .test_class_name' \
    | sort -u
)

# No failing test class (e.g. compile, setup or infrastructure failure) -> no attribution.
if [[ "${#failing_classes[@]}" -eq 0 ]]; then
  exit 0
fi

resolved_owner=""
for fqcn in "${failing_classes[@]}"; do
  [[ -z "${fqcn}" ]] && continue

  source_file="$(resolve_test_source_file "${fqcn}")"
  owner="$(resolve_codeowners_team "${source_file}")"

  # Strict: any failing class we cannot attribute forces a fallback to the job owner.
  if [[ -z "${owner}" ]]; then
    exit 0
  fi

  if [[ -z "${resolved_owner}" ]]; then
    resolved_owner="${owner}"
  elif [[ "${resolved_owner}" != "${owner}" ]]; then
    # Mixed ownership across the failing tests -> fall back to the job owner.
    exit 0
  fi
done

echo "${resolved_owner}"
