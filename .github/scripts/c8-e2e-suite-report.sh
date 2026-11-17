#!/usr/bin/env bash
#
# Post the failure-triage comment (c8-e2e-suite-triage.sh) for a suite PR run
# on its PR. The suite jobs show the result; this adds what the failures mean.
#
# Called by the triage job of the suite PR check
# (.github/workflows/c8-orchestration-cluster-e2e-tests-pr.yml), after its
# suite jobs, with their reports already downloaded to $PR_REPORTS_DIR.
#
# Required env: GH_TOKEN, REPO, PR_NUMBER, TEST_SHA, RUN_ID, RUN_URL,
#               RUN_CONCLUSION, PR_REPORTS_DIR
set -euo pipefail

: "${GH_TOKEN:?}" "${REPO:?}" "${PR_NUMBER:?}" "${TEST_SHA:?}" "${RUN_ID:?}" "${RUN_URL:?}"
: "${RUN_CONCLUSION:?}" "${PR_REPORTS_DIR:?}"

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Retry a command on failure: a single failed GitHub API call must not lose
# the comment. 4 attempts, 2s/4s/8s back-off.
retry() {
  local attempt=1 delay=2
  until "$@"; do
    if (( attempt >= 4 )); then
      echo "::warning::'$1 ${2:-}' failed after ${attempt} attempts" >&2
      return 1
    fi
    sleep "$delay"
    attempt=$((attempt + 1))
    delay=$((delay * 2))
  done
}

# Comment only while this commit is still the PR's open head: a comment about
# a commit a newer push replaced would mislead.
pr="$(retry gh api "repos/${REPO}/pulls/${PR_NUMBER}" \
  --jq '{state, head: .head.sha, author: .user.login, author_type: .user.type, base: .base.ref}')"
if [[ "$(jq -r '.state' <<<"$pr")" != "open" ]]; then
  echo "PR #${PR_NUMBER} is no longer open; no comment."
  exit 0
fi
if [[ "$(jq -r '.head' <<<"$pr")" != "$TEST_SHA" ]]; then
  echo "PR #${PR_NUMBER} has moved on from ${TEST_SHA:0:12}; no comment."
  exit 0
fi
PR_AUTHOR="$(jq -r '.author' <<<"$pr")"
PR_AUTHOR_TYPE="$(jq -r '.author_type' <<<"$pr")"
BASE_REF="$(jq -r '.base' <<<"$pr")"
export PR_AUTHOR PR_AUTHOR_TYPE BASE_REF
mkdir -p "$PR_REPORTS_DIR"
"${script_dir}/c8-e2e-suite-triage.sh"
