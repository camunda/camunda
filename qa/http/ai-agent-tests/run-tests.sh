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

if ijhttp "$HTTP_FILE" \
  --env-file "$PUBLIC_ENV" \
  --private-env-file "$PRIVATE_ENV" \
  --env "$ENV_NAME" \
  --report \
  --socket-timeout 300000 > "$TMPOUT" 2>&1; then

  # Parse results (macOS-compatible: use sed instead of grep -oP)
  TOTAL_REQUESTS=$(sed -n 's/.*\([0-9][0-9]*\) requests completed.*/\1/p' "$TMPOUT" | tail -1)
  TOTAL_REQUESTS="${TOTAL_REQUESTS:-0}"
  FAILED_TESTS=$(sed -n 's/.*\([0-9][0-9]*\) have failed tests.*/\1/p' "$TMPOUT" | tail -1)
  FAILED_TESTS="${FAILED_TESTS:-0}"

  echo ""
  printf "${BLUE}┌%${WIDTH}s┐${NC}\n" "" | tr ' ' '─'
  printf "${BLUE}│${NC}${BOLD}  Test Results%*s${BLUE}│${NC}\n" $((WIDTH - 15)) ""
  printf "${BLUE}├%${WIDTH}s┤${NC}\n" "" | tr ' ' '─'

  if [[ "$FAILED_TESTS" == "0" ]]; then
    printf "${BLUE}│${NC} ${GREEN}✓ PASSED${NC} — All assertions passed%*s${BLUE}│${NC}\n" $((WIDTH - 35)) ""
    printf "${BLUE}│${NC}%*s${BLUE}│${NC}\n" $WIDTH ""

    # Print each discovered test as a passed item
    for i in "${!TESTS[@]}"; do
      line="  ✓ Test $((i+1)): ${TESTS[$i]}"
      printf "${BLUE}│${NC} ${GREEN}%s${NC}%*s${BLUE}│${NC}\n" "$line" $((WIDTH - ${#line} - 1)) ""
    done

    printf "${BLUE}├%${WIDTH}s┤${NC}\n" "" | tr ' ' '─'
    req_line=" HTTP Requests: $TOTAL_REQUESTS"
    printf "${BLUE}│${NC}%s%*s${BLUE}│${NC}\n" "$req_line" $((WIDTH - ${#req_line})) ""
    printf "${BLUE}└%${WIDTH}s┘${NC}\n" "" | tr ' ' '─'
    echo ""
    echo -e "${GREEN}E2E validation successful${NC}"
    exit 0
  else
    fail_line=" ✗ FAILED — $FAILED_TESTS assertion(s) failed"
    printf "${BLUE}│${NC} ${RED}%s${NC}%*s${BLUE}│${NC}\n" "$fail_line" $((WIDTH - ${#fail_line} - 1)) ""
    printf "${BLUE}├%${WIDTH}s┤${NC}\n" "" | tr ' ' '─'

    printf "${BLUE}├%${WIDTH}s┤${NC}\n" "" | tr ' ' '─'
    req_line=" HTTP Requests: $TOTAL_REQUESTS"
    printf "${BLUE}│${NC}%s%*s${BLUE}│${NC}\n" "$req_line" $((WIDTH - ${#req_line})) ""
    printf "${BLUE}└%${WIDTH}s┘${NC}\n" "" | tr ' ' '─'
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
  echo ""
  echo -e "${RED}Test execution failed (ijhttp exited with error)${NC}"
  echo ""
  echo -e "${DIM}── ijhttp output ──${NC}"
  cat "$TMPOUT"
  exit 1
fi
