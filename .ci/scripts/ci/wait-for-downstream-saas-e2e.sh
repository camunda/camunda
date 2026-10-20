#!/usr/bin/env bash
# Waits for the SaaS E2E run that trigger-saas-e2e dispatched to c8-cross-component-e2e-tests.
# Inputs (env): GH_TOKEN, CORRELATION_ID, WAIT_DEADLINE, optional DOWNSTREAM_RUN_ID, PHASE_ONLY.
# Outputs ($GITHUB_OUTPUT): downstream_run_url, downstream_conclusion, wait_incomplete, downstream_run_id.
set -euo pipefail
echo "Waiting for downstream E2E workflow run in camunda/c8-cross-component-e2e-tests for correlation id: $CORRELATION_ID"

TARGET_REPO="camunda/c8-cross-component-e2e-tests"
WORKFLOW_FILE="playwright_saas_pr_trigger_monorepo.yml"

# The caller passes the absolute deadline (epoch seconds) as WAIT_DEADLINE.
# The wait is bounded by wall clock rather than by a tick count: a poll whose
# `gh_api_with_retry` exhausts its backoff burns up to 62s on top of the 30s
# tick, so counting ticks says nothing about how long this runs. Only an
# absolute deadline keeps the loop -- not the runner -- the thing that ends
# the wait, which is what lets the timeout path below emit its diagnostics.
#
# The full budget is 65min from the job's start, sized off the green
# downstream runs only (median 29min, p90 41min, p95 44min, p99 60min, max
# 73min across the 381 successful runs of playwright_saas_pr_trigger_monorepo.yml
# between 2026-09-07 and 2026-09-22). At 50min, 9 of the 381 (2.4%) were still
# running and still green when the deadline fired; at 65min that is 2 (0.5%).
# Keep the job-level `timeout-minutes` 10min above it.
#
# That budget is longer than the 1h lifetime of the GitHub App token, so the
# workflow runs this script twice with a token refresh in between. With
# PHASE_ONLY=true, reaching WAIT_DEADLINE is not a timeout: the script hands
# the run id back (wait_incomplete=true, downstream_run_id) and exits 0 so the
# next phase can carry on with a fresh token via DOWNSTREAM_RUN_ID.
: "${WAIT_DEADLINE:?WAIT_DEADLINE is required}"

# Never sleep past the deadline: both the loop condition and the
# retry backoff are only re-checked between sleeps, so an unguarded
# sleep is exactly how the overshoot creeps back in.
deadline_sleep() {
  local want="$1" remaining
  remaining=$(( WAIT_DEADLINE - $(date +%s) ))
  if (( remaining <= 0 )); then
    return 0
  fi
  if (( remaining > want )); then
    remaining="$want"
  fi
  sleep "$remaining"
}

gh_api_with_retry() {
  local endpoint="$1"
  local max_attempts="${2:-6}"
  local sleep_s="${3:-2}"
  local attempt=1
  local out

  while true; do
    if out="$(gh api "$endpoint" 2>&1)"; then
      printf '%s' "$out"
      return 0
    fi

    # Rate limiting is matched by message, not by status code. GitHub
    # serves the primary limit as 403 and the secondary limit as 403
    # or 429, and a plain 403 is the permanent case this function
    # exists to separate out (a bad token, a repo the App cannot
    # see). Matching `HTTP 403` wholesale would poll a dead token to
    # the deadline instead of failing fast, so only the rate-limit
    # wording -- and 429, which is never anything else -- is treated
    # as transient. `abuse detection` is the same family: GitHub's
    # older wording for the secondary limit, served as 403 and
    # self-clearing, so it retries for the same reason.
    if echo "$out" | grep -Eqi 'HTTP 429|rate limit|abuse detection|HTTP 500|HTTP 502|HTTP 503|HTTP 504|TLS handshake timeout|timeout|temporarily unavailable|connection reset by peer|EOF|tls: failed to verify certificate|x509: certificate|stream error|GOAWAY|broken pipe|no such host|connection refused'; then
      if (( attempt >= max_attempts )); then
        echo "gh api failed after ${attempt}/${max_attempts} attempts for $endpoint" >&2
        echo "$out" >&2
        return 1
      fi
      echo "Transient gh/api error (attempt ${attempt}/${max_attempts}) for $endpoint:" >&2
      echo "$out" >&2
      deadline_sleep "$sleep_s"
      # Do not start another attempt past the deadline. Once the
      # clamped sleep has run the budget out, a further `gh api`
      # could stall -- and a stalled call is the one thing a
      # deadline cannot bound, which would hand the kill back to
      # the runner and lose the timeout report.
      if (( $(date +%s) >= WAIT_DEADLINE )); then
        echo "Deadline reached while retrying $endpoint; abandoning this poll." >&2
        return 1
      fi
      attempt=$((attempt + 1))
      sleep_s=$((sleep_s * 2))
      continue
    fi

    # Exit code 2 marks a permanent error (bad token, renamed workflow
    # file) so the caller fails fast instead of polling to the timeout.
    echo "Non-retryable gh/api error for $endpoint:" >&2
    echo "$out" >&2
    return 2
  done
}

RUN_ID="${DOWNSTREAM_RUN_ID:-}"
if [[ -n "$RUN_ID" ]]; then
  echo "Resuming wait on downstream run id: $RUN_ID"
  echo "downstream_run_url=https://github.com/$TARGET_REPO/actions/runs/$RUN_ID" >> "$GITHUB_OUTPUT"
fi

while (( $(date +%s) < WAIT_DEADLINE )); do
  if [[ -z "$RUN_ID" ]]; then
    # A retry-exhausted poll must not end the wait: this loop is the
    # timeout mechanism, so fall through to the next tick. Only a
    # permanent error (exit 2) aborts.
    RUNS_JSON="" RC=0
    RUNS_JSON="$(gh_api_with_retry "/repos/$TARGET_REPO/actions/workflows/$WORKFLOW_FILE/runs?event=repository_dispatch&per_page=50")" || RC=$?
    if (( RC == 2 )); then
      echo "downstream_conclusion=dispatch-error" >> "$GITHUB_OUTPUT"
      exit 1
    elif (( RC != 0 )); then
      echo "Could not list downstream runs on this poll; retrying on the next tick." >&2
      deadline_sleep 30
      continue
    fi

    RUN_ID=$(
      jq -r --arg c "$CORRELATION_ID" '
          .workflow_runs
          | map(select(.display_title != null and (.display_title | contains($c))))
          | sort_by(.created_at)
          | last
          | .id // empty
        ' <<<"$RUNS_JSON"
    )

    if [[ -z "$RUN_ID" ]]; then
      echo "No downstream run found yet for correlation id. Sleeping..."
      deadline_sleep 30
      continue
    fi

    echo "Locked onto downstream run id: $RUN_ID"
    echo "downstream_run_url=https://github.com/$TARGET_REPO/actions/runs/$RUN_ID" >> "$GITHUB_OUTPUT"

    # Re-check before falling through to the status request below.
    # Every other iteration reaches it with the loop's own deadline
    # check immediately behind it, but this one has an unbounded
    # `gh api` in between: the run-list call can start inside the
    # budget and return outside it. Starting a fresh unbounded call
    # on a spent budget is what hands the kill back to the runner --
    # a hang there only has the 10min of job-timeout headroom to
    # land in, and past that the job dies as `cancelled` with no
    # timeout report. The run url is already written above, so the
    # report still carries the link.
    if (( $(date +%s) >= WAIT_DEADLINE )); then
      echo "Deadline reached while locking onto run $RUN_ID; reporting the timeout instead of starting another poll." >&2
      break
    fi
  fi

  RUN_JSON="" RC=0
  RUN_JSON="$(gh_api_with_retry "/repos/$TARGET_REPO/actions/runs/$RUN_ID")" || RC=$?
  if (( RC == 2 )); then
    echo "downstream_conclusion=dispatch-error" >> "$GITHUB_OUTPUT"
    exit 1
  elif (( RC != 0 )); then
    echo "Could not read downstream run $RUN_ID on this poll; retrying on the next tick." >&2
    deadline_sleep 30
    continue
  fi

  STATUS="$(jq -r .status <<<"$RUN_JSON")"
  CONCLUSION="$(jq -r .conclusion <<<"$RUN_JSON")"
  echo "Workflow run $RUN_ID status: $STATUS ($CONCLUSION)"

  if [[ "$STATUS" == "completed" ]]; then
    if [[ "$CONCLUSION" == "success" ]]; then
      echo "downstream_conclusion=success" >> "$GITHUB_OUTPUT"
      echo "Downstream workflow succeeded!"
      exit 0
    else
      echo "downstream_conclusion=${CONCLUSION}" >> "$GITHUB_OUTPUT"
      echo "Downstream workflow failed: $CONCLUSION"
      exit 1
    fi
  fi

  deadline_sleep 30
done

if [[ "${PHASE_ONLY:-false}" == "true" ]]; then
  echo "Phase deadline reached; handing over to the next phase with a fresh token."
  echo "wait_incomplete=true" >> "$GITHUB_OUTPUT"
  echo "downstream_run_id=$RUN_ID" >> "$GITHUB_OUTPUT"
  exit 0
fi

echo "downstream_conclusion=timeout" >> "$GITHUB_OUTPUT"
echo "Timed out waiting for downstream workflow to complete."
exit 1
