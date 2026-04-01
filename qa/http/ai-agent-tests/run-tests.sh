#!/usr/bin/env bash
set -euo pipefail

# ─────────────────────────────────────────────────────────────────
# Generic ijhttp test runner
# ─────────────────────────────────────────────────────────────────
# Automatically discovers test metadata from the .http file:
#   ### @test <name>      — declares a test scenario
#   ### PHASE <N>: <desc> — declares an execution phase
#   ### Step <N>: <desc>  — declares a step (grouped under phases)
#
# Emits metrics: pass rate, execution time, per-test results.
# Writes metrics.json for CI aggregation (flakiness/trend analysis).
#
# Usage: ./run-tests.sh [env_name]
# ─────────────────────────────────────────────────────────────────

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
DIM='\033[2m'
BOLD='\033[1m'
NC='\033[0m'

# Configuration
ENV_NAME="${1:-sm}"
HTTP_FILE="ai-agent-tests.http"
PUBLIC_ENV="http-client.env.json"
PRIVATE_ENV="http-client.private.env.json"
METRICS_FILE="metrics.json"

# Validate files exist
for f in "$HTTP_FILE" "$PUBLIC_ENV"; do
  if [[ ! -f "$f" ]]; then
    echo -e "${RED}Error: $f not found${NC}"
    exit 1
  fi
done

# Get base URL from environment
ZEEBE_REST_ADDRESS=$(jq -r ".${ENV_NAME}.ZEEBE_REST_ADDRESS // empty" "$PRIVATE_ENV" 2>/dev/null || echo "")
if [[ -z "$ZEEBE_REST_ADDRESS" ]]; then
  echo -e "${RED}Error: ZEEBE_REST_ADDRESS not found in $PRIVATE_ENV for environment '$ENV_NAME'${NC}"
  exit 1
fi

# ── Auto-discover metadata from .http file ──────────────────────

# Extract test names from "### @test <name>" lines
mapfile -t TESTS < <(grep -oP '(?<=^### @test ).*' "$HTTP_FILE" || true)
TEST_COUNT=${#TESTS[@]}

# Extract suite title from first non-empty ### line
SUITE_TITLE=$(grep -m1 -oP '(?<=^### )(?!=)[A-Za-z].*' "$HTTP_FILE" | head -1 || echo "E2E Tests")

# ── Helper: format duration ─────────────────────────────────────
format_duration() {
  local secs=$1
  if (( secs >= 60 )); then
    printf "%dm %ds" $((secs / 60)) $((secs % 60))
  else
    printf "%ds" "$secs"
  fi
}

# ── Helper: write metrics.json for CI aggregation ───────────────
write_metrics() {
  local status="$1" duration="$2" total_req="$3" failed_assert="$4"
  local passed_count=$((TEST_COUNT - failed_test_count))
  local pass_rate
  if (( TEST_COUNT > 0 )); then
    pass_rate=$(awk "BEGIN { printf \"%.1f\", ($passed_count / $TEST_COUNT) * 100 }")
  else
    pass_rate="0.0"
  fi

  # Build per-test JSON array
  local tests_json="["
  for i in "${!TESTS[@]}"; do
    local tname="${TESTS[$i]}"
    local tstatus="${TEST_STATUSES[$i]:-unknown}"
    (( i > 0 )) && tests_json+=","
    tests_json+="{\"name\":\"${tname}\",\"status\":\"${tstatus}\"}"
  done
  tests_json+="]"

  cat > "$METRICS_FILE" <<EOF
{
  "suite": "${SUITE_TITLE}",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "environment": "${ENV_NAME}",
  "status": "${status}",
  "duration_seconds": ${duration},
  "test_count": ${TEST_COUNT},
  "tests_passed": ${passed_count},
  "tests_failed": ${failed_test_count},
  "pass_rate": ${pass_rate},
  "http_requests": ${total_req},
  "failed_assertions": ${failed_assert},
  "tests": ${tests_json}
}
EOF
}

# ── Display header ──────────────────────────────────────────────

WIDTH=65
echo ""
printf "${BLUE}┌%${WIDTH}s┐${NC}\n" "" | tr ' ' '─'
printf "${BLUE}│${NC}${BOLD}%*s${NC}${BLUE}│${NC}\n" $(( (WIDTH + ${#SUITE_TITLE}) / 2 )) "$SUITE_TITLE"
printf "${BLUE}└%${WIDTH}s┘${NC}\n" "" | tr ' ' '─'
echo ""
echo -e "  Environment:  ${BOLD}${ENV_NAME}${NC}"
echo -e "  API Base URL: ${ZEEBE_REST_ADDRESS}"
echo -e "  Tests:        ${TEST_COUNT}"
echo ""

# List discovered tests
if (( TEST_COUNT > 0 )); then
  echo -e "${BLUE}Test Scenarios:${NC}"
  for i in "${!TESTS[@]}"; do
    echo -e "  $((i+1)). ${TESTS[$i]}"
  done
  echo ""
fi

# ── Run ijhttp (output suppressed — we show our own results) ───

echo -e "${YELLOW}▶ Starting test execution...${NC}"
echo ""

TMPOUT=$(mktemp)
trap "rm -f $TMPOUT" EXIT

# Track execution time
START_TIME=$(date +%s)

if ijhttp "$HTTP_FILE" \
  --env-file "$PUBLIC_ENV" \
  --private-env-file "$PRIVATE_ENV" \
  --env "$ENV_NAME" \
  --report \
  --socket-timeout 300000 > "$TMPOUT" 2>&1; then

  END_TIME=$(date +%s)
  DURATION=$((END_TIME - START_TIME))

  # Parse results (macOS-compatible: use sed instead of grep -oP)
  TOTAL_REQUESTS=$(sed -n 's/.*\([0-9][0-9]*\) requests completed.*/\1/p' "$TMPOUT" | tail -1)
  TOTAL_REQUESTS="${TOTAL_REQUESTS:-0}"
  FAILED_TESTS=$(sed -n 's/.*\([0-9][0-9]*\) have failed tests.*/\1/p' "$TMPOUT" | tail -1)
  FAILED_TESTS="${FAILED_TESTS:-0}"

  # ── Determine per-test pass/fail status ──────────────────────
  # Assertion names contain [Test Name] prefix — match against TESTS array
  declare -a TEST_STATUSES
  failed_test_count=0
  for i in "${!TESTS[@]}"; do
    # Extract the tag prefix from the test name (e.g. "Happy Path" from "Happy Path - ...")
    tag="${TESTS[$i]%% - *}"
    if grep -qi "\[${tag}\].*FAIL\|FAIL.*\[${tag}\]" "$TMPOUT" 2>/dev/null; then
      TEST_STATUSES[$i]="failed"
      (( failed_test_count++ ))
    else
      TEST_STATUSES[$i]="passed"
    fi
  done

  # Compute pass rate
  PASSED_COUNT=$((TEST_COUNT - failed_test_count))
  if (( TEST_COUNT > 0 )); then
    PASS_RATE=$(awk "BEGIN { printf \"%.0f\", ($PASSED_COUNT / $TEST_COUNT) * 100 }")
  else
    PASS_RATE="0"
  fi

  # ── Write metrics.json ───────────────────────────────────────
  if [[ "$FAILED_TESTS" == "0" ]]; then
    write_metrics "passed" "$DURATION" "$TOTAL_REQUESTS" "$FAILED_TESTS"
  else
    write_metrics "failed" "$DURATION" "$TOTAL_REQUESTS" "$FAILED_TESTS"
  fi

  # ── Display results ──────────────────────────────────────────

  echo ""
  printf "${BLUE}┌%${WIDTH}s┐${NC}\n" "" | tr ' ' '─'
  printf "${BLUE}│${NC}${BOLD}  Test Results%*s${BLUE}│${NC}\n" $((WIDTH - 15)) ""
  printf "${BLUE}├%${WIDTH}s┤${NC}\n" "" | tr ' ' '─'

  if [[ "$FAILED_TESTS" == "0" ]]; then
    printf "${BLUE}│${NC} ${GREEN}✓ PASSED${NC} — All assertions passed%*s${BLUE}│${NC}\n" $((WIDTH - 35)) ""
    printf "${BLUE}│${NC}%*s${BLUE}│${NC}\n" $WIDTH ""

    # Print each discovered test with its status
    for i in "${!TESTS[@]}"; do
      line="  ✓ Test $((i+1)): ${TESTS[$i]}"
      printf "${BLUE}│${NC} ${GREEN}%s${NC}%*s${BLUE}│${NC}\n" "$line" $((WIDTH - ${#line} - 1)) ""
    done

    printf "${BLUE}├%${WIDTH}s┤${NC}\n" "" | tr ' ' '─'
    req_line=" HTTP Requests: $TOTAL_REQUESTS"
    printf "${BLUE}│${NC}%s%*s${BLUE}│${NC}\n" "$req_line" $((WIDTH - ${#req_line})) ""
    rate_line=" Pass Rate:     ${PASS_RATE}% (${PASSED_COUNT}/${TEST_COUNT} tests)"
    printf "${BLUE}│${NC}%s%*s${BLUE}│${NC}\n" "$rate_line" $((WIDTH - ${#rate_line})) ""
    dur_line=" Duration:      $(format_duration $DURATION)"
    printf "${BLUE}│${NC}%s%*s${BLUE}│${NC}\n" "$dur_line" $((WIDTH - ${#dur_line})) ""
    printf "${BLUE}└%${WIDTH}s┘${NC}\n" "" | tr ' ' '─'
    echo ""
    echo -e "${DIM}Metrics written to ${METRICS_FILE}${NC}"
    echo ""
    echo -e "${GREEN}E2E validation successful${NC}"
    exit 0
  else
    fail_line=" ✗ FAILED — $FAILED_TESTS assertion(s) failed"
    printf "${BLUE}│${NC} ${RED}%s${NC}%*s${BLUE}│${NC}\n" "$fail_line" $((WIDTH - ${#fail_line} - 1)) ""
    printf "${BLUE}│${NC}%*s${BLUE}│${NC}\n" $WIDTH ""

    # Print each test with its pass/fail status
    for i in "${!TESTS[@]}"; do
      if [[ "${TEST_STATUSES[$i]}" == "failed" ]]; then
        line="  ✗ Test $((i+1)): ${TESTS[$i]}"
        printf "${BLUE}│${NC} ${RED}%s${NC}%*s${BLUE}│${NC}\n" "$line" $((WIDTH - ${#line} - 1)) ""
      else
        line="  ✓ Test $((i+1)): ${TESTS[$i]}"
        printf "${BLUE}│${NC} ${GREEN}%s${NC}%*s${BLUE}│${NC}\n" "$line" $((WIDTH - ${#line} - 1)) ""
      fi
    done

    printf "${BLUE}├%${WIDTH}s┤${NC}\n" "" | tr ' ' '─'
    req_line=" HTTP Requests: $TOTAL_REQUESTS"
    printf "${BLUE}│${NC}%s%*s${BLUE}│${NC}\n" "$req_line" $((WIDTH - ${#req_line})) ""
    rate_line=" Pass Rate:     ${PASS_RATE}% (${PASSED_COUNT}/${TEST_COUNT} tests)"
    printf "${BLUE}│${NC}%s%*s${BLUE}│${NC}\n" "$rate_line" $((WIDTH - ${#rate_line})) ""
    dur_line=" Duration:      $(format_duration $DURATION)"
    printf "${BLUE}│${NC}%s%*s${BLUE}│${NC}\n" "$dur_line" $((WIDTH - ${#dur_line})) ""
    printf "${BLUE}└%${WIDTH}s┘${NC}\n" "" | tr ' ' '─'
    echo ""
    echo -e "${DIM}Metrics written to ${METRICS_FILE}${NC}"
    echo ""

    # Show execution steps grouped by phase so we can see where it failed
    echo -e "${BLUE}Execution Steps:${NC}"
    while IFS= read -r sline; do
      if [[ "$sline" =~ ^###\ PHASE\ [0-9]+:\ (.*) ]]; then
        echo ""
        echo -e "  ${BOLD}${BASH_REMATCH[1]}${NC}"
      elif [[ "$sline" =~ ^###\ Step\ (.*) ]]; then
        echo -e "    → ${BASH_REMATCH[1]}"
      fi
    done < "$HTTP_FILE"
    echo ""

    # Show failed assertion details from ijhttp output
    echo -e "${RED}Failed Assertions:${NC}"
    grep -i "FAIL\|AssertionError\|Expected" "$TMPOUT" | head -20 || true
    echo ""

    echo -e "${RED}E2E validation failed${NC}"
    # Print full ijhttp output for CI log inspection
    echo ""
    echo -e "${DIM}── Full ijhttp output ──${NC}"
    cat "$TMPOUT"
    exit 1
  fi
else
  END_TIME=$(date +%s)
  DURATION=$((END_TIME - START_TIME))

  # Write failure metrics
  declare -a TEST_STATUSES
  failed_test_count=$TEST_COUNT
  for i in "${!TESTS[@]}"; do
    TEST_STATUSES[$i]="error"
  done
  write_metrics "error" "$DURATION" "0" "0"

  echo ""
  echo -e "${RED}Test execution failed (ijhttp exited with error)${NC}"
  echo -e "${DIM}Duration: $(format_duration $DURATION)${NC}"
  echo -e "${DIM}Metrics written to ${METRICS_FILE}${NC}"
  echo ""
  echo -e "${DIM}── ijhttp output ──${NC}"
  cat "$TMPOUT"
  exit 1
fi
