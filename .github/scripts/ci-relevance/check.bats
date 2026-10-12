#!/usr/bin/env bats

# Tests for check.sh — verifies that .github/ path changes are correctly classified
# as CI-relevant or not, based on the excluded-paths.txt patterns.

SCRIPT="${BATS_TEST_DIRNAME}/check.sh"
PATTERNS="${BATS_TEST_DIRNAME}/excluded-paths.txt"

# Helper: pipe the given filenames (one per arg) into check.sh
check() {
  printf '%s\n' "$@" | "$SCRIPT" "$PATTERNS"
}

# ── Excluded paths: should NOT trigger CI ────────────────────────────────────

@test "should not be CI-relevant when only a load-test workflow changed" {
  # given a PR that only changes a load-test workflow
  # when checking CI relevance
  run check ".github/workflows/camunda-weekly-load-tests.yml"
  # then CI should not be triggered
  [ "$status" -eq 1 ]
}

@test "should not be CI-relevant when only a daily load-test workflow changed" {
  # given a PR that only changes a daily load-test workflow
  # when checking CI relevance
  run check ".github/workflows/camunda-daily-load-tests.yml"
  # then CI should not be triggered
  [ "$status" -eq 1 ]
}

@test "should not be CI-relevant when only a testbench workflow changed" {
  # given a PR that only changes a testbench workflow
  # when checking CI relevance
  run check ".github/workflows/zeebe-testbench.yaml"
  # then CI should not be triggered
  [ "$status" -eq 1 ]
}

@test "should not be CI-relevant when only the await-load-test action changed" {
  # given a PR that only changes the await-load-test action
  # when checking CI relevance
  run check ".github/actions/await-load-test/action.yml"
  # then CI should not be triggered
  [ "$status" -eq 1 ]
}

@test "should not be CI-relevant when only non-.github/ files changed" {
  # given a PR that only changes Java source files (handled by other filters)
  # when checking CI relevance
  run check "zeebe/engine/src/main/java/Foo.java" \
            "operate/backend/src/main/java/Bar.java"
  # then CI should not be triggered (non-.github/ files are ignored here)
  [ "$status" -eq 1 ]
}

@test "should not be CI-relevant when input is empty" {
  # given no changed files
  # when checking CI relevance
  run bash -c "printf '' | '$SCRIPT' '$PATTERNS'"
  # then CI should not be triggered
  [ "$status" -eq 1 ]
}

# ── CI-relevant paths: should trigger CI ─────────────────────────────────────

@test "should not be CI-relevant when only the coverage-plan workflow changed" {
  # given a PR that only changes the coverage-plan validation workflow
  # when checking CI relevance
  run check ".github/workflows/check-coverage-plans.yml"
  # then CI should not be triggered
  [ "$status" -eq 1 ]
}

@test "should not be CI-relevant when only a nested coverage-plan-check file changed" {
  # given a PR that only changes a file nested under the validator directory
  # when checking CI relevance
  run check ".github/scripts/coverage-plan-check/coverage-plan.mjs"
  # then CI should not be triggered
  [ "$status" -eq 1 ]
}

@test "should be CI-relevant when a coverage-plan file and a CI workflow both changed" {
  # given a PR that changes the validator alongside a CI-relevant workflow
  # when checking CI relevance
  run check ".github/scripts/coverage-plan-check/package.json
.github/workflows/ci.yml"
  # then CI should be triggered
  [ "$status" -eq 0 ]
}

@test "should be CI-relevant when a CI workflow changed" {
  # given a PR that changes the main CI workflow
  # when checking CI relevance
  run check ".github/workflows/ci.yml"
  # then CI should be triggered
  [ "$status" -eq 0 ]
}

@test "should be CI-relevant when a .github/actions/ file changed" {
  # given a PR that changes the paths-filter composite action
  # when checking CI relevance
  run check ".github/actions/paths-filter/action.yml"
  # then CI should be triggered
  [ "$status" -eq 0 ]
}

@test "should be CI-relevant when an actionlint config changed" {
  # given a PR that changes the actionlint config
  # when checking CI relevance
  run check ".github/actionlint.yaml"
  # then CI should be triggered
  [ "$status" -eq 0 ]
}

# ── Mixed changes: CI-relevant signal wins ───────────────────────────────────

@test "should be CI-relevant when a load-test workflow and a CI workflow both changed" {
  # given a PR that changes both a load-test workflow and the CI workflow
  # when checking CI relevance
  run check ".github/workflows/camunda-weekly-load-tests.yml" \
            ".github/workflows/ci.yml"
  # then CI should be triggered (non-excluded file wins)
  [ "$status" -eq 0 ]
}

@test "should be CI-relevant when a load-test workflow and a .github/actions/ file both changed" {
  # given a PR that changes both a load-test workflow and a CI-relevant action
  # when checking CI relevance
  run check ".github/workflows/camunda-weekly-load-tests.yml" \
            ".github/actions/paths-filter/action.yml"
  # then CI should be triggered (non-excluded file wins)
  [ "$status" -eq 0 ]
}


# ── Scoped E2E run ───────────────────────────────────────────────────────────

@test "should not be CI-relevant when only the scoped E2E run workflow changed" {
  # given a PR that changes only the dispatch-only scoped E2E run workflow
  # when checking CI relevance
  run check ".github/workflows/c8-orchestration-cluster-e2e-scoped-run.yml"
  # then CI should be skipped — it runs an existing test, it builds nothing
  [ "$status" -eq 1 ]
}

@test "should not be CI-relevant when only the scoped E2E verdict script changed" {
  # given a PR that changes only the verdict reader for that workflow
  # when checking CI relevance
  run check ".github/scripts/e2e-scoped-verdict.mjs"
  # then CI should be skipped
  [ "$status" -eq 1 ]
}

@test "should be CI-relevant when the scoped E2E run workflow and a CI workflow both changed" {
  # given a PR that changes the scoped E2E run workflow and the CI workflow
  # when checking CI relevance
  run check ".github/workflows/c8-orchestration-cluster-e2e-scoped-run.yml" \
            ".github/workflows/ci.yml"
  # then CI should be triggered (non-excluded file wins)
  [ "$status" -eq 0 ]
}
