#!/usr/bin/env bats

# Tests for apply-changed-spec-verdict.sh, which turns a suite job's Playwright
# run into the job's exit code. It runs from the suite directory, so each test
# runs in a temporary suite dir with the real repository script and the real
# verdict script (GITHUB_WORKSPACE points at the repo root).
#
# What matters: "full" mode must keep Playwright's verdict, "changed" mode must
# defer to the changed-spec verdict, and a leg that produced no report must
# leave a sentinel so the Full Result check can see it.

SCRIPT="${BATS_TEST_DIRNAME}/apply-changed-spec-verdict.sh"

setup() {
  cd "${BATS_TEST_TMPDIR}"
  export GITHUB_WORKSPACE="$(cd "${BATS_TEST_DIRNAME}/../.." && pwd)"
  export RUNNER_TEMP="${BATS_TEST_TMPDIR}"
  export GITHUB_STEP_SUMMARY=""
  unset GATING CHANGED_SPECS
}

write_report() {
  mkdir -p json-report
  printf '%s\n' "$1" > json-report/results.json
}

failing_changed_spec='{"suites":[{"file":"tests/a.spec.ts","specs":[{"title":"a","ok":false}]}]}'

@test "full mode passes Playwright's exit code through" {
  write_report '{"suites":[]}'
  run bash "$SCRIPT" 0
  [ "$status" -eq 0 ]
  run bash "$SCRIPT" 1
  [ "$status" -eq 1 ]
}

@test "full mode is the default" {
  write_report "$failing_changed_spec"
  CHANGED_SPECS="" run bash "$SCRIPT" 1
  [ "$status" -eq 1 ]
}

@test "a missing report is replaced by a sentinel with a run-level error" {
  run bash "$SCRIPT" 0
  [ "$status" -eq 0 ]
  grep -q '"errors":\[{"message":"No Playwright report' json-report/results.json
}

@test "changed mode blocks on a failing changed spec, even when Playwright exited 0" {
  write_report "$failing_changed_spec"
  GATING=changed CHANGED_SPECS="tests/a.spec.ts" run bash "$SCRIPT" 0
  [ "$status" -eq 1 ]
}

@test "changed mode does not block on a failing untouched spec" {
  write_report "$failing_changed_spec"
  GATING=changed CHANGED_SPECS="tests/other.spec.ts" run bash "$SCRIPT" 1
  [ "$status" -eq 0 ]
}

@test "changed mode with no changed specs blocks on nothing" {
  GATING=changed CHANGED_SPECS="" run bash "$SCRIPT" 1
  [ "$status" -eq 0 ]
}

@test "changed mode blocks when the leg produced no report but specs changed" {
  # The sentinel's run-level error must block: the changed specs never ran.
  GATING=changed CHANGED_SPECS="tests/a.spec.ts" run bash "$SCRIPT" 0
  [ "$status" -eq 1 ]
}

@test "a missing Playwright exit code is a usage error" {
  run bash "$SCRIPT"
  [ "$status" -ne 0 ]
  [[ "$output" == *usage* ]]
}
