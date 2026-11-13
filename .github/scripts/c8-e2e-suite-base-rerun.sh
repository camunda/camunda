#!/usr/bin/env bash
#
# Re-run the failed tests of a suite job with the base branch's test code.
#
# Called from the test steps of c8-orchestration-cluster-e2e-tests-reusable.yml
# on a PR run, right after `npm run test` failed, from the suite directory and
# with the step's environment, so the base code runs against the same Camunda
# stack, project and configuration as the PR's code did. The outcome per failed
# test (passed / failed / skipped / absent on the base) is written to
# json-report/base-rerun.json, which the PR check's triage job turns into
# "regression from this PR" or "fails with the base code too".
#
# This never changes the job's result: the caller exits with the original test
# exit code. Any problem here is recorded in base-rerun.json as "error" or
# "skipped" with a reason, and the triage falls back to hints for that job.
#
# Usage: c8-e2e-suite-base-rerun.sh <playwright args, e.g. --project=chromium>
# Env:   COMPARE_BASE_SHA (the PR's base commit), GITHUB_WORKSPACE, RUNNER_TEMP,
#        JOB_STARTED_AT (epoch seconds) and JOB_TIMEOUT_MIN, so the re-run only
#        uses time the job has left: a job killed by its timeout uploads no
#        report at all, which would lose more than the comparison gains.
set -uo pipefail

: "${COMPARE_BASE_SHA:?}" "${GITHUB_WORKSPACE:?}" "${RUNNER_TEMP:?}"

suite_rel="qa/c8-orchestration-cluster-e2e-test-suite"
# The helper next to this script: the workflow checks both out from its own
# commit, which is not necessarily the tested branch.
triage="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/c8-e2e-suite-triage.mjs"
report="json-report/results.json"
out="json-report/base-rerun.json"
max="${MAX_RERUN:-15}"
# Kept free for the report upload, teardown and build-status steps.
reserve_s=300

record() { # status reason
  jq -n --arg status "$1" --arg reason "$2" --arg base "$COMPARE_BASE_SHA" \
    '{status: $status, base: $base, reason: $reason}' > "$out"
  echo "Base re-run: $1 ($2)"
}

PROJECT_ARGS=("$@")
# The projects the job selected: a failed test in any other project ran as
# a teardown and is not re-run (c8-e2e-suite-triage.mjs).
RERUN_PROJECTS="$(printf '%s\n' "${PROJECT_ARGS[@]}" | sed -n 's/^--project=//p' | paste -sd, -)"
export RERUN_PROJECTS

if [[ ! -s "$report" ]]; then
  echo "No test report; nothing to compare."
  exit 0
fi

# One deadline for the whole comparison (checkout, install, test runs): at
# most 12 minutes, and always reserve_s before the job's own timeout. Each
# phase gets only what is left, and is skipped, with the reason recorded,
# when too little is.
now() { date +%s; }
deadline=$(( ${JOB_STARTED_AT:-$(now)} + ${JOB_TIMEOUT_MIN:-45} * 60 - reserve_s ))
cap=$(( $(now) + 720 ))
(( deadline > cap )) && deadline=$cap
left() { echo $(( deadline - $(now) )); }
need() { # seconds phase
  if (( $(left) < $1 )); then
    record skipped "not enough time left in the job (before $2)"
    exit 0
  fi
}

# The base suite: the suite directory at the PR's base commit, checked out
# into a repository of its own. Not fetched into the job's checkout: a git
# process outside this script (e.g. background maintenance) can hold its
# lock, which made the fetch fail. Only the suite directory's files are
# downloaded (partial clone, sparse checkout).
need 120 "checking out the base test code"
base_root="${RUNNER_TEMP}/c8-e2e-base-suite"
rm -rf "$base_root" && mkdir -p "$base_root"
origin_url="$(git -C "$GITHUB_WORKSPACE" remote get-url origin)"
if ! (
  cd "$base_root" &&
    git init -q &&
    git remote add origin "$origin_url" &&
    git sparse-checkout set "$suite_rel" &&
    timeout "$(left)s" git fetch -q --no-tags --depth=1 --filter=blob:none origin "$COMPARE_BASE_SHA" &&
    timeout "$(left)s" git checkout -q FETCH_HEAD
); then
  record error "could not check out the base test code"
  exit 0
fi
base_suite="${base_root}/${suite_rel}"

# Whether this PR changes the suite's test code at all: if the suite
# directory is the same at the PR's commit and at the base commit, a test
# that passes on the base re-run ran the very same code, in the same job,
# against the same Camunda stack, so the difference is intermittent, not
# caused by the PR. Compared by git's tree of the directory, so generated
# files in the job's checkout (node_modules, reports) do not count. Unknown
# (false) if the PR's commit cannot be fetched.
head_sha="$(git -C "$GITHUB_WORKSPACE" rev-parse HEAD 2>/dev/null || true)"
RERUN_SAME_TEST_CODE=false
if [[ -n "$head_sha" ]] && (
  cd "$base_root" &&
    timeout "$(left)s" git fetch -q --no-tags --depth=1 --filter=blob:none origin "$head_sha" &&
    git diff --quiet HEAD "$head_sha" -- "$suite_rel"
); then
  RERUN_SAME_TEST_CODE=true
  echo "This PR does not change the suite's test code."
fi
export RERUN_SAME_TEST_CODE

plan="$(node "$triage" rerun-plan "$report" "$base_suite")"
failed="$(jq -r '.failed' <<<"$plan")"
if (( failed == 0 )); then
  echo "No failed test in the report (the failure is outside any test); nothing to compare."
  exit 0
fi
if (( failed > max )); then
  record skipped "${failed} failed tests, over the re-run limit of ${max}"
  exit 0
fi
mapfile -t files < <(jq -r '.files[]' <<<"$plan")
mapfile -t serial_files < <(jq -r '.serialFiles[]' <<<"$plan")
grep_re="$(jq -r '.grep' <<<"$plan")"

base_reports=()
if (( ${#files[@]} + ${#serial_files[@]} )); then
  # The PR's tests ran on the node_modules the job installed. With the same
  # package.json on the base, reuse them, so both sides run on the same
  # dependencies. (Not the lockfile: the job deletes it and resolves afresh,
  # so its lockfile never matches the committed one.) Otherwise install the
  # base's dependencies the way the job does, resolved now, like the PR's.
  if cmp -s package.json "${base_suite}/package.json"; then
    ln -s "$PWD/node_modules" "${base_suite}/node_modules"
  else
    need 60 "installing the base dependencies"
    if ! (cd "$base_suite" && rm -f package-lock.json &&
      timeout "$(left)s" npm install --no-audit --no-fund >/dev/null); then
      record error "npm install failed or ran out of time for the base test code"
      exit 0
    fi
    # The job installed browsers for the PR's Playwright version. With a
    # different version on the base, every base test would fail to launch a
    # browser and wrongly read as "fails with the base test code", so install
    # the base's browser, or give up on the comparison.
    pw_version() { node -p "require('$1/node_modules/@playwright/test/package.json').version" 2>/dev/null; }
    pr_pw="$(pw_version "$PWD")"
    base_pw="$(pw_version "$base_suite")"
    if [[ "$pr_pw" != "$base_pw" ]]; then
      echo "Base uses Playwright ${base_pw:-unknown} (PR: ${pr_pw:-unknown}); installing its browser."
      need 60 "installing the base Playwright browser"
      if ! (cd "$base_suite" && timeout "$(left)s" npx playwright install chromium >/dev/null); then
        record error "could not install the browser for the base's Playwright ${base_pw:-version}"
        exit 0
      fi
    fi
  fi
  run_base() { # out-file playwright-args...
    local out_file="$1" status=0
    shift
    need 60 "re-running the tests"
    (
      cd "$base_suite" &&
        PLAYWRIGHT_JSON_OUTPUT_FILE="$out_file" \
          timeout "$(left)s" npx playwright test "$@" --reporter=json >/dev/null
    ) || status=$?
    echo "Base re-run finished with exit code ${status}."
    # Killed by the deadline (timeout's 124, or 137 if it had to SIGKILL):
    # whatever report it left is partial, and a test interrupted mid-run
    # would read as failed with the base code. Its tests count as not run.
    if (( status == 124 || status == 137 )); then
      echo "The base re-run ran out of time; its report is not used."
      RERUN_TIMED_OUT=true
      return
    fi
    base_reports+=("$out_file")
  }
  echo "Re-running ${failed} failed test(s) with the base test code (${COMPARE_BASE_SHA:0:12})."
  if (( ${#files[@]} )); then
    echo "Failed tests only: ${files[*]}"
    run_base "${RUNNER_TEMP}/c8-e2e-base-results.json" "${files[@]}" "${PROJECT_ARGS[@]}" --grep "$grep_re"
  fi
  if (( ${#serial_files[@]} )); then
    # Serial specs whole: their tests build on the earlier ones.
    echo "Serial specs, whole: ${serial_files[*]}"
    run_base "${RUNNER_TEMP}/c8-e2e-base-results-serial.json" "${serial_files[@]}" "${PROJECT_ARGS[@]}"
  fi
else
  # Every failed spec is new in this PR: an empty report marks them absent.
  echo '{"suites":[]}' > "${RUNNER_TEMP}/c8-e2e-base-results.json"
  base_reports+=("${RUNNER_TEMP}/c8-e2e-base-results.json")
fi

if [[ "${RERUN_TIMED_OUT:-}" == "true" && ${#base_reports[@]} -eq 0 ]]; then
  record skipped "the base re-run ran out of time"
  exit 0
fi
export RERUN_TIMED_OUT
node "$triage" rerun-compare "$report" "$base_suite" "$COMPARE_BASE_SHA" "$out" "${base_reports[@]}"
