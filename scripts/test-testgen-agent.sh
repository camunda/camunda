#!/bin/bash
# Example curl commands for Test Case Generator Agent
# Usage: ./test-agent.sh [command]

BASE_URL="${MCP_BASE_URL:-http://localhost:8080}"
MCP_ENDPOINT="${BASE_URL}/mcp"

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

print_section() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

print_command() {
    echo -e "${YELLOW}Running: $1${NC}"
    echo ""
}

# Function to make MCP call
mcp_call() {
    local tool_name="$1"
    local arguments="$2"
    
    curl -s -X POST "${MCP_ENDPOINT}" \
        -H "Content-Type: application/json" \
        -d "{
            \"jsonrpc\": \"2.0\",
            \"method\": \"tools/call\",
            \"params\": {
                \"name\": \"${tool_name}\",
                \"arguments\": ${arguments}
            },
            \"id\": 1
        }" | jq '.'
}

# Test 1: Fetch Epics
test_fetch_epics() {
    print_section "Test 1: Fetch Product Hub Epics"
    print_command "fetchProductHubEpics"
    
    mcp_call "fetchProductHubEpics" '{
        "label": "epic",
        "state": "open"
    }'
}

# Test 2: Validate TestRail
test_validate_testrail() {
    print_section "Test 2: Validate TestRail Connection"
    print_command "validateTestRailConnection"
    
    local project_id="${1:-1}"
    
    mcp_call "validateTestRailConnection" "{
        \"testRailProjectId\": \"${project_id}\"
    }"
}

# Test 3: Generate from Epic
test_generate_from_epic() {
    print_section "Test 3: Generate Test Cases from Epic"
    print_command "generateTestCasesFromEpic"
    
    local epic_num="${1:-123}"
    local project_id="${2:-1}"
    local suite_id="${3:-5}"
    
    mcp_call "generateTestCasesFromEpic" "{
        \"epicIssueNumber\": \"${epic_num}\",
        \"testRailProjectId\": \"${project_id}\",
        \"testRailSuiteId\": \"${suite_id}\"
    }"
}

# Test 4: Bulk Generate
test_bulk_generate() {
    print_section "Test 4: Bulk Generate Test Cases"
    print_command "bulkGenerateTestCases"
    
    local project_id="${1:-1}"
    local suite_id="${2:-5}"
    
    mcp_call "bulkGenerateTestCases" "{
        \"epicLabel\": \"epic\",
        \"state\": \"open\",
        \"testRailProjectId\": \"${project_id}\",
        \"testRailSuiteId\": \"${suite_id}\"
    }"
}

# Test MCP endpoint availability
test_endpoint() {
    print_section "Testing MCP Endpoint"
    echo "Endpoint: ${MCP_ENDPOINT}"
    echo ""
    
    response=$(curl -s -o /dev/null -w "%{http_code}" "${MCP_ENDPOINT}")
    
    if [ "$response" = "200" ] || [ "$response" = "405" ]; then
        echo -e "${GREEN}✓ MCP endpoint is accessible${NC}"
    else
        echo -e "${YELLOW}⚠ MCP endpoint returned status: ${response}${NC}"
        echo "Make sure Camunda is running with MCP enabled"
    fi
}

# Show help
show_help() {
    echo "Test Case Generator Agent - Test Script"
    echo ""
    echo "Usage: $0 [command] [args...]"
    echo ""
    echo "Commands:"
    echo "  endpoint                    - Test if MCP endpoint is accessible"
    echo "  fetch-epics                 - Fetch Epics from product-hub"
    echo "  validate [project_id]       - Validate TestRail connection"
    echo "  generate [epic] [proj] [suite] - Generate test cases from Epic"
    echo "  bulk [project_id] [suite_id]  - Bulk generate test cases"
    echo "  all                         - Run all tests"
    echo ""
    echo "Environment Variables:"
    echo "  MCP_BASE_URL  - Base URL (default: http://localhost:8080)"
    echo ""
    echo "Examples:"
    echo "  $0 endpoint"
    echo "  $0 fetch-epics"
    echo "  $0 validate 1"
    echo "  $0 generate 123 1 5"
    echo "  $0 bulk 1 5"
}

# Main command handler
case "${1:-help}" in
    endpoint)
        test_endpoint
        ;;
    fetch-epics)
        test_fetch_epics
        ;;
    validate)
        test_validate_testrail "${2:-1}"
        ;;
    generate)
        test_generate_from_epic "${2:-123}" "${3:-1}" "${4:-5}"
        ;;
    bulk)
        test_bulk_generate "${2:-1}" "${3:-5}"
        ;;
    all)
        test_endpoint
        echo ""
        test_fetch_epics
        echo ""
        test_validate_testrail "1"
        ;;
    help|*)
        show_help
        ;;
esac
