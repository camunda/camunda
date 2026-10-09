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
# Requires: codeowners-cli on PATH, python3, jq and git. The test reports (TEST-*.xml,
# and the Playwright report of the Orchestration Cluster E2E suite) must be present on disk. The script resolves the repository root itself and scans from
# there, so it is independent of the caller's working directory.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || echo ".")"

# Scan from the repository root regardless of the caller's CWD, so reports are not missed.
cd "${REPO_ROOT}"

# Reuse the FQCN -> source file -> codeowners team resolvers.
# shellcheck source=/dev/null
source "${REPO_ROOT}/.ci/scripts/ci/setup-medic-lookup.sh"

# The Playwright JUnit report of the Orchestration Cluster E2E suite. Its test class
# names are spec paths relative to the suite root, which resolve_test_source_file maps.
PLAYWRIGHT_JUNIT_REPORT="qa/c8-orchestration-cluster-e2e-test-suite/test-results/junit-report.xml"

# Collect the distinct test classes that actually failed. The JUnit parser tags
# flaky-but-passed retries as "flaky" and drops the passing occurrence, so filtering
# on "failure"/"error" here excludes flaky, skipped and passing tests. Playwright writes
# no failure for a test that passed on retry.
mapfile -t failing_classes < <(
  {
    find . -iname 'TEST-*.xml'
    if [[ -f "${PLAYWRIGHT_JUNIT_REPORT}" ]]; then echo "${PLAYWRIGHT_JUNIT_REPORT}"; fi
  } \
    | python3 "${SCRIPT_DIR}/junit-test-results-to-jsonl.py" \
    | jq -r 'select(.test_status == "failure" or .test_status == "error") | .test_class_name' \
    | sort -u
)

# No failing test class (e.g. compile, setup or infrastructure failure) -> no attribution.
if [[ "${#failing_classes[@]}" -eq 0 ]]; then
  exit 0
fi

declare -A owners=()
unattributed=()
for fqcn in "${failing_classes[@]}"; do
  [[ -z "${fqcn}" ]] && continue

  source_file="$(resolve_test_source_file "${fqcn}")"
  owner="$(resolve_codeowners_team "${source_file}")"

  if [[ -z "${owner}" ]]; then
    unattributed+=("${fqcn}")
  else
    owners["${owner}"]=1
  fi
done

# Strict: one owner for every failing class, or fall back to the job owner.
if [[ "${#unattributed[@]}" -eq 0 && "${#owners[@]}" -eq 1 ]]; then
  echo "${!owners[@]}"
  exit 0
fi

# On stderr, so the candidates show in the job log without becoming the owner.
candidates="$(printf '%s\n' "${!owners[@]}" | sort | paste -sd ' ' -)"
echo "No single owner for the failing tests. Candidates: ${candidates:-none}." >&2
if [[ "${#unattributed[@]}" -gt 0 ]]; then
  echo "Failing tests without an owner: ${unattributed[*]}" >&2
fi
