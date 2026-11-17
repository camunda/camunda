#!/usr/bin/env bash
#
# Post the failure-triage comment on an orchestration-cluster suite PR.
#
# Called by c8-e2e-suite-report.sh, from the triage job of the suite PR check
# (.github/workflows/c8-orchestration-cluster-e2e-tests-pr.yml).
# It feeds c8-e2e-suite-triage.mjs this run's per-leg Playwright reports and
# base re-run results (already downloaded to $PR_REPORTS_DIR) and the jobs that
# ran, and posts a NEW comment mentioning the author on every failing run, so a
# failure on a later push is not missed in an edited comment. A passing run
# posts one "passing" comment only when the PR's latest triage comment
# reported failures, and nothing otherwise, so green runs stay quiet.
#
# Required env: GH_TOKEN, REPO, PR_NUMBER, PR_AUTHOR, PR_AUTHOR_TYPE, BASE_REF,
#               RUN_ID, RUN_URL, PR_REPORTS_DIR
set -euo pipefail

: "${GH_TOKEN:?}" "${REPO:?}" "${PR_NUMBER:?}" "${PR_AUTHOR:?}" "${PR_AUTHOR_TYPE:?}" "${BASE_REF:?}"
: "${RUN_ID:?}" "${RUN_URL:?}" "${PR_REPORTS_DIR:?}"

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
marker='<!-- c8-e2e-suite-triage -->'
# Must match the passing heading rendered by c8-e2e-suite-triage.mjs.
passing_heading='C8 Orchestration Cluster E2E suite: passing'

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

# 1. Legs that were expected to produce a report: this run's suite jobs that
#    actually ran (the calling job's id is "tests", so their names start with
#    "tests / ", behind the outer caller's own prefix, e.g. "suite / tests / "),
#    as "<leg>\t<attempt>". The leg label is the job name without those
#    prefixes and with "/" replaced by "-", matching the leg.txt each job
#    uploads with its report; the attempt is the one the job last ran in, so
#    a report left over from an earlier attempt can be rejected.
retry gh api --paginate "repos/${REPO}/actions/runs/${RUN_ID}/jobs?filter=latest&per_page=100" \
  --jq '.jobs[] | select(.name | test("^(.+? / )?tests / ")) | select(.conclusion != "skipped") | "\(.name | sub("^(.+? / )?tests / "; "") | gsub("/"; "-"))\t\(.run_attempt)"' \
  > "$work/expected-legs.txt"
EXPECTED_LEGS="$(cat "$work/expected-legs.txt")"
export EXPECTED_LEGS

# 2. Whether the PR's latest triage comment reported failures: decides if a
#    passing run has something to resolve. Comments come oldest first.
last_state="$(retry gh api --paginate "repos/${REPO}/issues/${PR_NUMBER}/comments?per_page=100" \
  --jq ".[] | select(.body | startswith(\"${marker}\")) | if (.body | contains(\"${passing_heading}\")) then \"passing\" else \"failing\" end" \
  | tail -n1)"

# Who to tag: the author, or for a bot PR (Renovate, the nightly fix agent),
# its assignees instead, since a mention of a bot reaches no one. A bot PR
# without assignees is commented on without a mention.
if [[ "$PR_AUTHOR_TYPE" == "Bot" ]]; then
  MENTIONS="$(retry gh api "repos/${REPO}/pulls/${PR_NUMBER}" --jq '[.assignees[].login] | join(" ")' || true)"
  echo "Bot PR by ${PR_AUTHOR}; mentioning assignees: ${MENTIONS:-<none>}"
else
  MENTIONS="$PR_AUTHOR"
fi
export MENTIONS BASE_REF RUN_URL

summary="$(node "${script_dir}/c8-e2e-suite-triage.mjs" "$PR_REPORTS_DIR" "$work/body.md")"
echo "Triage: ${summary}"
passing="$(jq -r '.passing' <<<"$summary")"

{
  echo "### Suite failure triage"
  echo
  tail -n +2 "$work/body.md"
} >> "${GITHUB_STEP_SUMMARY:-/dev/null}"

# 3. Post, or stay quiet on a green run with nothing to resolve.
if [[ "$passing" == "true" && "$last_state" != "failing" ]]; then
  echo "Suite passed and no earlier failure is open; nothing to post."
else
  retry gh api "repos/${REPO}/issues/${PR_NUMBER}/comments" -F "body=@$work/body.md" >/dev/null
  echo "Posted triage comment."
fi
