#!/usr/bin/env bash
set -euo pipefail

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Configuration
ENV_NAME="${1:-sm}"
HTTP_FILE="ai-agent-tests.http"
PUBLIC_ENV="http-client.env.json"
PRIVATE_ENV="http-client.private.env.json"

# Validate files exist
if [[ ! -f "$HTTP_FILE" ]]; then
  echo -e "${RED}❌ Error: $HTTP_FILE not found${NC}"
  exit 1
fi

# Get base URL from environment
ZEEBE_REST_ADDRESS=$(jq -r ".${ENV_NAME}.ZEEBE_REST_ADDRESS // empty" "$PRIVATE_ENV" 2>/dev/null || echo "")
if [[ -z "$ZEEBE_REST_ADDRESS" ]]; then
  echo -e "${RED}❌ Error: ZEEBE_REST_ADDRESS not found in $PRIVATE_ENV for environment '$ENV_NAME'${NC}"
  exit 1
fi

echo ""
echo -e "${BLUE}┌─────────────────────────────────────────────────────────────────┐${NC}"
echo -e "${BLUE}│       AI Agent Connector E2E Test - User Flow Validation       │${NC}"
echo -e "${BLUE}└─────────────────────────────────────────────────────────────────┘${NC}"
echo ""
echo -e "API Base URL: ${ZEEBE_REST_ADDRESS}"
echo -e "Environment: ${ENV_NAME}"
echo ""

# Extract and display the test flow description
echo -e "${BLUE}Test Scenario:${NC}"
echo "  User can deploy a process with an AI Agent, the agent processes"
echo "  the request, user validates the agent response, and completes the process"
echo ""

# Parse test steps from HTTP file
echo -e "${BLUE}Test Steps:${NC}"
grep "^### Step " "$HTTP_FILE" | sed 's/^### Step /  /' || true
echo ""

# Run ijhttp and capture output
echo -e "${YELLOW}▶ Starting test execution...${NC}"
echo ""

# Create temporary file for output
TMPOUT=$(mktemp)
trap "rm -f $TMPOUT" EXIT

# Run ijhttp with detailed output
if ijhttp "$HTTP_FILE" \
  --env-file "$PUBLIC_ENV" \
  --private-env-file "$PRIVATE_ENV" \
  --env "$ENV_NAME" \
  --report \
  --socket-timeout 300000 2>&1 | tee "$TMPOUT" | grep -v '━'; then
  
  # Parse results
  TOTAL_REQUESTS=$(grep -oP '\d+(?= requests completed)' "$TMPOUT" || echo "0")
  FAILED_TESTS=$(grep -oP '\d+(?= have failed tests)' "$TMPOUT" || echo "0")
  
  echo ""
  echo -e "${BLUE}┌─────────────────────────────────────────────────────────────────┐${NC}"
  echo -e "${BLUE}│                         Test Results                            │${NC}"
  echo -e "${BLUE}├─────────────────────────────────────────────────────────────────┤${NC}"
  
  if [[ "$FAILED_TESTS" == "0" ]]; then
    echo -e "${BLUE}│${NC} ${GREEN}✓ PASSED${NC} - All steps completed successfully                   ${BLUE}│${NC}"
    echo -e "${BLUE}│${NC}   - Authentication                                              ${BLUE}│${NC}"
    echo -e "${BLUE}│${NC}   - Process and form deployment                                 ${BLUE}│${NC}"
    echo -e "${BLUE}│${NC}   - Process instance creation                                   ${BLUE}│${NC}"
    echo -e "${BLUE}│${NC}   - AI Agent connector execution (~60s)                         ${BLUE}│${NC}"
    echo -e "${BLUE}│${NC}   - User task validation                                        ${BLUE}│${NC}"
    echo -e "${BLUE}│${NC}   - Agent response verification                                 ${BLUE}│${NC}"
    echo -e "${BLUE}│${NC}   - Task assignment and completion                              ${BLUE}│${NC}"
    echo -e "${BLUE}│${NC}   - Process instance completion assertion                       ${BLUE}│${NC}"
    echo -e "${BLUE}├─────────────────────────────────────────────────────────────────┤${NC}"
    echo -e "${BLUE}│${NC} HTTP Requests: $TOTAL_REQUESTS (including retries)                         ${BLUE}│${NC}"
    echo -e "${BLUE}└─────────────────────────────────────────────────────────────────┘${NC}"
    echo ""
    echo -e "${GREEN}🎉 E2E user flow validation successful${NC}"
    exit 0
  else
    echo -e "${BLUE}│${NC} ${RED}✗ FAILED${NC} - $FAILED_TESTS test assertion(s) failed                      ${BLUE}│${NC}"
    echo -e "${BLUE}├─────────────────────────────────────────────────────────────────┤${NC}"
    echo -e "${BLUE}│${NC} HTTP Requests: $TOTAL_REQUESTS                                            ${BLUE}│${NC}"
    echo -e "${BLUE}└─────────────────────────────────────────────────────────────────┘${NC}"
    echo ""
    echo -e "${RED}❌ E2E user flow validation failed${NC}"
    echo -e "${YELLOW}Check the test report for detailed failure information${NC}"
    exit 1
  fi
else
  echo ""
  echo -e "${RED}❌ Test execution failed${NC}"
  exit 1
fi
