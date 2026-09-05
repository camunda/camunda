#!/bin/bash
# Quick Start Script for Test Case Generator Agent
# This script helps you set up and test the Test Case Generator Agent

set -e

echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║   Test Case Generator Agent - Quick Start Setup              ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo ""

# Function to prompt for input
prompt_input() {
    local prompt="$1"
    local var_name="$2"
    local is_secret="${3:-false}"
    
    if [ "$is_secret" = "true" ]; then
        read -s -p "$prompt: " value
        echo ""
    else
        read -p "$prompt: " value
    fi
    
    eval "$var_name='$value'"
}

# Check if configuration already exists
if [ -f ".env.testgen" ]; then
    echo "⚠️  Configuration file .env.testgen already exists."
    read -p "Do you want to recreate it? (y/n): " recreate
    if [ "$recreate" != "y" ]; then
        echo "Using existing configuration."
        source .env.testgen
    else
        rm .env.testgen
    fi
fi

# Collect configuration if not already set
if [ ! -f ".env.testgen" ]; then
    echo ""
    echo "Let's set up your Test Case Generator Agent configuration."
    echo ""
    
    echo "📝 GitHub Configuration (Optional)"
    echo "   GitHub token increases rate limits from 60 to 5000 requests/hour"
    prompt_input "   GitHub Personal Access Token (press Enter to skip)" GITHUB_TOKEN true
    echo ""
    
    echo "📝 TestRail Configuration (Required)"
    prompt_input "   TestRail URL (e.g., https://yourcompany.testrail.com)" TESTRAIL_URL
    prompt_input "   TestRail Username (your email)" TESTRAIL_USERNAME
    prompt_input "   TestRail API Key" TESTRAIL_API_KEY true
    echo ""
    
    # Save configuration
    cat > .env.testgen << EOF
# Test Case Generator Agent Configuration
export GITHUB_TOKEN="${GITHUB_TOKEN}"
export TESTRAIL_URL="${TESTRAIL_URL}"
export TESTRAIL_USERNAME="${TESTRAIL_USERNAME}"
export TESTRAIL_API_KEY="${TESTRAIL_API_KEY}"
EOF
    
    echo "✅ Configuration saved to .env.testgen"
    echo ""
fi

# Load configuration
source .env.testgen

# Test GitHub connection
echo "🔍 Testing GitHub connection..."
if [ -n "$GITHUB_TOKEN" ]; then
    response=$(curl -s -H "Authorization: Bearer $GITHUB_TOKEN" \
        "https://api.github.com/repos/camunda/product-hub" 2>/dev/null || echo "error")
    if [[ "$response" == *"error"* ]] || [[ "$response" == *"Not Found"* ]]; then
        echo "   ⚠️  GitHub connection failed or repository not accessible"
        echo "   Will use unauthenticated access (60 requests/hour limit)"
    else
        echo "   ✅ GitHub connection successful (authenticated)"
    fi
else
    echo "   ℹ️  No GitHub token configured (using unauthenticated access)"
fi

# Test TestRail connection
echo ""
echo "🔍 Testing TestRail connection..."
response=$(curl -s -u "${TESTRAIL_USERNAME}:${TESTRAIL_API_KEY}" \
    "${TESTRAIL_URL}/index.php?/api/v2/get_projects" 2>/dev/null || echo "error")
if [[ "$response" == *"error"* ]] || [[ "$response" == *"Authentication failed"* ]]; then
    echo "   ❌ TestRail connection failed"
    echo "   Please check your credentials and try again"
    exit 1
else
    echo "   ✅ TestRail connection successful"
    # Parse and display projects
    echo ""
    echo "   Available TestRail Projects:"
    echo "$response" | grep -o '"name":"[^"]*"' | sed 's/"name":"/   - /g' | sed 's/"//g' | head -5
fi

echo ""
echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║   Configuration Complete!                                     ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo ""
echo "📚 Next Steps:"
echo ""
echo "1️⃣  Start the Camunda application with environment variables:"
echo "   source .env.testgen"
echo "   ./mvnw spring-boot:run -pl dist"
echo ""
echo "2️⃣  Or run with Docker:"
echo "   source .env.testgen"
echo "   docker run -p 8080:8080 \\"
echo "     -e GITHUB_TOKEN=\"\$GITHUB_TOKEN\" \\"
echo "     -e TESTRAIL_URL=\"\$TESTRAIL_URL\" \\"
echo "     -e TESTRAIL_USERNAME=\"\$TESTRAIL_USERNAME\" \\"
echo "     -e TESTRAIL_API_KEY=\"\$TESTRAIL_API_KEY\" \\"
echo "     camunda/camunda:current-test"
echo ""
echo "3️⃣  Test the MCP endpoint:"
echo "   curl http://localhost:8080/mcp"
echo ""
echo "4️⃣  Try a test call (fetch Epics):"
echo "   curl -X POST http://localhost:8080/mcp \\"
echo "     -H 'Content-Type: application/json' \\"
echo "     -d '{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\",\"params\":{\"name\":\"fetchProductHubEpics\",\"arguments\":{\"label\":\"epic\",\"state\":\"open\"}},\"id\":1}'"
echo ""
echo "📖 For detailed usage instructions, see:"
echo "   gateways/gateway-mcp/src/main/java/io/camunda/gateway/mcp/tool/testgen/USAGE_GUIDE.md"
echo ""
echo "🎉 Happy test case generation!"
