#!/usr/bin/env bash
# owner: @camunda/qa-engineering
#
# Decide a suite test job's exit code after Playwright has run.
#
# Shared by the three run steps of
# .github/workflows/ci-c8-orchestration-cluster-e2e-suite-reusable.yml so the
# changed-aware gating lives in one place — see camunda/team-test-automation#188.
#
# Contract:
#   $1              Playwright's own exit code from the run just completed.
#   $GATING         "full" (default) or "changed".
#   $CHANGED_SPECS  newline-separated spec paths (relative to the suite root) to
#                   gate on in "changed" mode. May be empty.
#
# "full"    — the job gates on the whole suite, so Playwright's exit code decides.
#             This is the on-demand behaviour.
# "changed" — a PR check: block only on the PR's own added/modified tests. An
#             empty CHANGED_SPECS therefore blocks on nothing (a PR that touched
#             no spec file), which the verdict script reports and exits 0 for.
#
# Must be invoked from the suite directory, where json-report/results.json is
# written.
set -euo pipefail

pw_exit="${1:?usage: apply-changed-spec-verdict.sh <playwright-exit-code>}"
gating="${GATING:-full}"

if [[ "${gating}" != "changed" ]]; then
  echo "Gating on the whole suite (Playwright exit ${pw_exit})."
  exit "${pw_exit}"
fi

changed_file="${RUNNER_TEMP:-/tmp}/changed-specs.txt"
printf '%s\n' "${CHANGED_SPECS:-}" > "${changed_file}"

node "${GITHUB_WORKSPACE}/.github/scripts/e2e-changed-spec-verdict.mjs" \
  json-report/results.json "${changed_file}"
