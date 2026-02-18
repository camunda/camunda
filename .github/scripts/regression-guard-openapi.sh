#!/usr/bin/env bash
# owner: Camunda-Ex #team-camunda-ex on Slack
# DRI: Josh Wulf <josh.wulf@camunda.com>
#
# Regression guard for the openapi-lint CI job.
#
# Proves that the openapi-lint job defined in ci.yml:
#   1. Detects known-bad specs (no false negatives)
#   2. Passes on the current spec (no false positives)
#
# HOW IT WORKS:
#   - Extracts the actual openapi-lint job from .github/workflows/ci.yml using yq
#   - Strips infrastructure (needs, if, observe-build-status) that requires CI context
#   - Checks out real specs from known-bad commits in git history as test fixtures
#   - Runs the extracted job via nektos/act against these specs (must fail) and the current spec (must pass)
#   - This tests the ACTUAL ci.yml job definition, not a replica — catching regressions
#     from ci.yml edits that silently break the lint
#
# Usage:  regression-guard-openapi.sh
# Requires: yq, act, docker

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
SPEC_DIR="zeebe/gateway-protocol/src/main/proto/v2"
TEMP_WORKFLOW=".github/workflows/_regression-guard-openapi-temp.yml"
ACT_IMAGE="catthehacker/ubuntu:act-latest"

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

cd "$REPO_ROOT"

# ── Known-bad commits ─────────────────────────────────────────────────────────
# Each entry: "commit|description|expected_pattern"
# These are real commits where the spec had known defects that the lint MUST catch.
# The spec files are checked out from git at these commits as test fixtures.
# The expected_pattern is a grep -E pattern that MUST appear in the act output to
# confirm the failure was for the right reason (not an unrelated crash).
KNOWN_BAD_COMMITS=(
  # Commit before "fix: incorrectly labeled x-eventually-consistent properties for mutation operations"
  # 19 command operations incorrectly annotated as eventually-consistent.
  # The x-eventually-consistent lint step must detect this.
  "98c17cb659|Incorrect x-eventually-consistent on mutations|19 violation\(s\) found"

  # Commit before "Fix malformed OpenAPI spec and replace Vacuum with Spectral (#45371)"
  # Has `required: false` at property level (invalid OpenAPI), missing property
  # descriptions, periods in summaries, and other schema violations.
  # See: https://github.com/camunda/camunda/issues/45369
  # The Spectral lint step must detect this.
  "6c21455848a|Malformed schema - required:false at property level, missing descriptions|data/required must be array"
)

# ── Prerequisites ──────────────────────────────────────────────────────────────

for cmd in yq act docker; do
  if ! command -v "$cmd" &>/dev/null; then
    echo -e "${RED}ERROR: '$cmd' is required but not found.${NC}" >&2
    exit 1
  fi
done

# ── Extract openapi-lint job from ci.yml ───────────────────────────────────────

echo "── Extracting openapi-lint job from ci.yml ──"

# Extract the job, remove needs/if/observe-build-status, wrap in a valid workflow
yq eval '
  {
    "name": "OpenAPI Lint (regression test)",
    "on": "push",
    "defaults": {"run": {"shell": "bash"}},
    "jobs": {
      "openapi-lint": .jobs.openapi-lint
        | del(.needs)
        | del(.if)
        | .steps |= map(select(.name != "Observe build status"))
    }
  }
' .github/workflows/ci.yml > "$TEMP_WORKFLOW"

echo "  Extracted to $TEMP_WORKFLOW"

# Verify extraction produced a valid workflow with steps
step_count=$(yq '.jobs.openapi-lint.steps | length' "$TEMP_WORKFLOW")
if [ "$step_count" -lt 2 ]; then
  echo -e "${RED}ERROR: Extracted workflow has only $step_count step(s) — expected at least 2.${NC}" >&2
  echo "The openapi-lint job structure in ci.yml may have changed." >&2
  rm -f "$TEMP_WORKFLOW"
  exit 1
fi
echo "  Job has $step_count steps"

# ── Helper: run act and return pass/fail ───────────────────────────────────────

run_act() {
  local label="$1"
  echo ""
  echo "── Running: $label ──"
  # Temporarily disable errexit+pipefail so we can capture act's exit code
  # even when piping through tee.
  set +eo pipefail
  act push \
    -j openapi-lint \
    -W "$TEMP_WORKFLOW" \
    -P "ubuntu-latest=$ACT_IMAGE" \
    --container-architecture linux/amd64 \
    2>&1 | tee /tmp/regression-guard-act-output.txt
  local rc=${PIPESTATUS[0]}
  set -eo pipefail
  return "$rc"
}

# ── Helper: swap spec from a git commit ────────────────────────────────────────

swap_spec_from_commit() {
  local commit="$1"
  # Back up current spec
  backup_dir=$(mktemp -d)
  cp -a "$SPEC_DIR/." "$backup_dir/"
  echo "$backup_dir"
  # Checkout spec files from the known-bad commit
  git checkout "$commit" -- "$SPEC_DIR/" 2>/dev/null
}

restore_spec() {
  local backup_dir="$1"
  git checkout HEAD -- "$SPEC_DIR/"
  rm -rf "$backup_dir"
}

# ── Phase 1: Prove linters detect known-bad specs (no false negatives) ─────────

echo ""
echo "════════════════════════════════════════════════════════════════════════════"
echo "  PHASE 1: Verify linters detect known-bad specs (expect failures)"
echo "════════════════════════════════════════════════════════════════════════════"

phase1_pass=true

for entry in "${KNOWN_BAD_COMMITS[@]}"; do
  commit="${entry%%|*}"
  rest="${entry#*|}"
  description="${rest%%|*}"
  expected_pattern="${rest#*|}"

  echo ""
  echo "  Checking out spec from commit $commit"
  echo "  Defect: $description"
  echo "  Expected pattern: $expected_pattern"

  backup_dir=$(swap_spec_from_commit "$commit")

  if run_act "Known-bad: $description (expect FAILURE)"; then
    echo -e "${RED}  FAIL: Lint did NOT detect defect from commit $commit.${NC}"
    echo -e "${RED}        Defect: $description${NC}"
    echo -e "${RED}        This means the CI quality gate has a false negative!${NC}"
    phase1_pass=false
  else
    # The job failed — but was it for the RIGHT reason?
    if grep -qE "$expected_pattern" /tmp/regression-guard-act-output.txt; then
      echo -e "${GREEN}  PASS: Lint correctly detected defect from commit $commit.${NC}"
      echo -e "${GREEN}        Verified output contains: $expected_pattern${NC}"
    else
      echo -e "${RED}  FAIL: Job failed, but NOT for the expected reason!${NC}"
      echo -e "${RED}        Expected pattern: $expected_pattern${NC}"
      echo -e "${RED}        The failure may be due to an unrelated error (missing tool, bad config, etc).${NC}"
      echo -e "${RED}        Check /tmp/regression-guard-act-output.txt for details.${NC}"
      phase1_pass=false
    fi
  fi

  restore_spec "$backup_dir"
done

# ── Phase 2: Prove linters pass on current spec (no false positives) ──────

echo ""
echo "════════════════════════════════════════════════════════════════════════════"
echo "  PHASE 2: Verify linters pass on current spec (expect success)"
echo "════════════════════════════════════════════════════════════════════════════"

phase2_pass=true

if run_act "Current spec (expect SUCCESS)"; then
  echo -e "${GREEN}  PASS: Lint passed on current spec.${NC}"
else
  echo -e "${RED}  FAIL: Lint failed on current spec!${NC}"
  echo -e "${RED}        Either the spec has a regression, or the linter is broken.${NC}"
  phase2_pass=false
fi

# ── Cleanup ────────────────────────────────────────────────────────────────────

rm -f "$TEMP_WORKFLOW"

# ── Summary ────────────────────────────────────────────────────────────────────

echo ""
echo "════════════════════════════════════════════════════════════════════════════"
echo "  SUMMARY"
echo "════════════════════════════════════════════════════════════════════════════"

exit_code=0

if [[ "$phase1_pass" == "true" ]]; then
  echo -e "${GREEN}  Phase 1 - defect detection: PASS - all fixtures correctly detected.${NC}"
else
  echo -e "${RED}  Phase 1 - defect detection: FAIL - linters missed known defects!${NC}"
  exit_code=1
fi

if [[ "$phase2_pass" == "true" ]]; then
  echo -e "${GREEN}  Phase 2 - current spec:     PASS - spec on main is valid.${NC}"
else
  echo -e "${RED}  Phase 2 - current spec:     FAIL - spec on main has issues!${NC}"
  exit_code=1
fi

echo ""
if [ $exit_code -eq 0 ]; then
  echo -e "${GREEN}  ✓ OpenAPI CI quality gate is operating correctly.${NC}"
else
  echo -e "${RED}  ✗ OpenAPI CI quality gate CANNOT be proven.${NC}"
fi

exit $exit_code
