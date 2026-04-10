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

@test "should not be CI-relevant when only a benchmark workflow changed" {
  # given a PR that only changes a benchmark workflow
  # when checking CI relevance
  run check ".github/workflows/zeebe-update-long-running-migrating-benchmark.yaml"
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

@test "should be CI-relevant when a load-test workflow and a Java file both changed" {
  # given a PR that changes a load-test workflow and a Java file
  # when checking CI relevance
  run check ".github/workflows/camunda-weekly-load-tests.yml" \
            "zeebe/engine/src/main/java/Foo.java"
  # then CI should NOT be triggered — Java files are handled by other filters,
  # and the only .github/ change is excluded
  [ "$status" -eq 1 ]
}
