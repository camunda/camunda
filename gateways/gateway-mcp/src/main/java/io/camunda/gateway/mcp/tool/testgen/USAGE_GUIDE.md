# How to Use the Test Case Generator Agent

This guide explains how to set up and use the Test Case Generator Agent to automate test case creation from GitHub Epics.

## Quick Start

### Step 1: Configure Your Environment

Create or update your `application.yaml` (or `application.properties`) with the following configuration:

```yaml
camunda:
  mcp:
    github:
      token: your-github-personal-access-token  # Optional but recommended
    testrail:
      url: https://yourcompany.testrail.com
      username: your.email@company.com
      api-key: your-testrail-api-key
```

Or using environment variables:

```bash
export GITHUB_TOKEN="ghp_xxxxxxxxxxxxxxxxxxxx"
export TESTRAIL_URL="https://yourcompany.testrail.com"
export TESTRAIL_USERNAME="your.email@company.com"
export TESTRAIL_API_KEY="your-api-key"
```

### Step 2: Get Your Credentials

#### GitHub Token (Optional)
1. Go to https://github.com/settings/tokens
2. Click "Generate new token" → "Generate new token (classic)"
3. Select scope: `repo` (for private repos) or `public_repo` (for public repos only)
4. Generate and copy the token

#### TestRail API Key (Required)
1. Log in to your TestRail instance
2. Click your profile icon → "My Settings"
3. Go to "API Keys" tab
4. Click "Add Key" and give it a name
5. Copy the generated API key

### Step 3: Deploy the Application

```bash
# Build the application
./mvnw install -DskipTests -T1C

# Run the application
java -jar dist/target/camunda-zeebe-*.jar

# Or using Docker
docker run -p 8080:8080 \
  -e GITHUB_TOKEN="your-token" \
  -e TESTRAIL_URL="https://yourcompany.testrail.com" \
  -e TESTRAIL_USERNAME="your-email" \
  -e TESTRAIL_API_KEY="your-key" \
  camunda/camunda:current-test
```

### Step 4: Access the MCP Server

The MCP server is available at: `http://localhost:8080/mcp`

## Using with AI Assistants

### Claude Desktop

Add to your `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "camunda": {
      "command": "curl",
      "args": [
        "-X", "POST",
        "-H", "Content-Type: application/json",
        "http://localhost:8080/mcp"
      ]
    }
  }
}
```

### Other MCP Clients

Connect any MCP-compatible client to: `http://localhost:8080/mcp`

## Usage Examples

### Example 1: Fetch Epics from Product Hub

**Using an AI Assistant:**
```
"Fetch all open Epics from the product-hub repository"
```

The AI will call:
```javascript
fetchProductHubEpics({
  label: "epic",
  state: "open"
})
```

**Direct MCP Call:**
```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/call",
    "params": {
      "name": "fetchProductHubEpics",
      "arguments": {
        "label": "epic",
        "state": "open"
      }
    },
    "id": 1
  }'
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "[{\"number\":123,\"title\":\"New Feature X\",\"body\":\"...\",\"labels\":[\"epic\"]}]"
      }
    ]
  },
  "id": 1
}
```

### Example 2: Generate Test Cases from an Epic

**Using an AI Assistant:**
```
"Create test cases in TestRail from Epic #123 in product-hub. 
Use project ID 1 and suite ID 5"
```

The AI will call:
```javascript
generateTestCasesFromEpic({
  epicIssueNumber: "123",
  testRailProjectId: "1",
  testRailSuiteId: "5"
})
```

**Direct MCP Call:**
```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "tools/call",
    "params": {
      "name": "generateTestCasesFromEpic",
      "arguments": {
        "epicIssueNumber": "123",
        "testRailProjectId": "1",
        "testRailSuiteId": "5",
        "testRailSectionId": "42"
      }
    },
    "id": 1
  }'
```

### Example 3: Bulk Generate Test Cases

**Using an AI Assistant:**
```
"Generate test cases for all open Epics in product-hub and 
create them in TestRail project 1, suite 5"
```

The AI will call:
```javascript
bulkGenerateTestCases({
  epicLabel: "epic",
  state: "open",
  testRailProjectId: "1",
  testRailSuiteId: "5"
})
```

### Example 4: Validate TestRail Connection

**Using an AI Assistant:**
```
"Check if TestRail is configured correctly for project 1"
```

The AI will call:
```javascript
validateTestRailConnection({
  testRailProjectId: "1"
})
```

## Real-World Workflow

Here's a complete workflow for using the Test Case Generator Agent:

### Scenario: Quarterly Test Case Generation

1. **List Available Epics**
   ```
   AI: "Show me all open Epics from product-hub"
   → fetchProductHubEpics(label="epic", state="open")
   → Returns: List of 15 Epics
   ```

2. **Review Epic Details**
   ```
   AI: "Show me the details of Epic #456"
   → fetchProductHubEpics() filters to Epic #456
   → Returns: Epic with acceptance criteria
   ```

3. **Validate TestRail Setup**
   ```
   AI: "Check TestRail connection for project 3"
   → validateTestRailConnection(testRailProjectId="3")
   → Returns: Connected ✓, Project: "Q1 2026 Release"
   ```

4. **Generate Test Cases**
   ```
   AI: "Create test cases from Epic #456 in TestRail project 3, suite 12"
   → generateTestCasesFromEpic(
       epicIssueNumber="456",
       testRailProjectId="3",
       testRailSuiteId="12"
     )
   → Returns: Created 5 test cases (TC-1001 to TC-1005)
   ```

5. **Bulk Process Multiple Epics**
   ```
   AI: "Generate test cases for all Epics labeled 'Q1-2026'"
   → bulkGenerateTestCases(
       epicLabel="Q1-2026",
       state="open",
       testRailProjectId="3",
       testRailSuiteId="12"
     )
   → Returns: Processed 8 Epics, created 43 test cases
   ```

## Understanding the Test Case Transformation

### What the Agent Looks For in Epics

The agent intelligently parses Epic content. Here's what it recognizes:

#### Structured Acceptance Criteria

**Epic Example:**
```markdown
# User Authentication Feature

## Acceptance Criteria

- Scenario: Successful login
  Given a registered user
  When they enter valid credentials
  Then they should be logged in
  And see the dashboard

- Scenario: Failed login
  Given a registered user
  When they enter invalid credentials
  Then an error message should be displayed
```

**Generated Test Cases:**
- **TC-001**: User Authentication Feature - Scenario 1: Successful login
  - Steps: Given a registered user, When they enter valid credentials
  - Expected: they should be logged in, see the dashboard

- **TC-002**: User Authentication Feature - Scenario 2: Failed login
  - Steps: Given a registered user, When they enter invalid credentials
  - Expected: error message should be displayed

#### Simple Acceptance Criteria

**Epic Example:**
```markdown
## Acceptance Criteria
- User can create a new account
- Password must be at least 8 characters
- Email verification is sent
```

**Generated Test Case:**
- **TC-001**: Verify User Authentication Feature
  - Steps:
    1. User can create a new account
    2. Password must be at least 8 characters
    3. Email verification is sent
  - Expected: All acceptance criteria are met

#### Unstructured Epics

If no acceptance criteria found, the agent creates a basic test case:

**Epic Example:**
```markdown
# Improve Dashboard Performance

We need to make the dashboard load faster.
```

**Generated Test Case:**
- **TC-001**: Verify Improve Dashboard Performance
  - Steps:
    1. Review Epic requirements: Improve Dashboard Performance
    2. Implement feature according to Epic specifications
    3. Verify all requirements are met
  - Expected: Feature works as described in Epic

## Finding Your TestRail Project and Suite IDs

### Using TestRail UI

1. **Project ID**: 
   - Go to your TestRail dashboard
   - Look at the URL: `https://yourcompany.testrail.com/index.php?/projects/overview/3`
   - The number after `/overview/` is your project ID (e.g., `3`)

2. **Suite ID**:
   - Go to Test Suites tab
   - Click on a suite
   - Look at the URL: `https://yourcompany.testrail.com/index.php?/suites/view/12`
   - The number after `/view/` is your suite ID (e.g., `12`)

3. **Section ID** (Optional):
   - Within a suite, click on a section
   - Look at the URL: `https://yourcompany.testrail.com/index.php?/suites/view/12&group_by=cases:section_id&group_id=42`
   - The number after `group_id=` is your section ID (e.g., `42`)

### Using TestRail API

```bash
# List all projects
curl -u "your-email:your-api-key" \
  https://yourcompany.testrail.com/index.php?/api/v2/get_projects

# List suites for project 3
curl -u "your-email:your-api-key" \
  https://yourcompany.testrail.com/index.php?/api/v2/get_suites/3
```

## Troubleshooting

### Problem: "GitHub API rate limit exceeded"

**Solution:**
- Configure a GitHub personal access token
- Authenticated requests have 5000 requests/hour vs 60 for unauthenticated

### Problem: "TestRail authentication failed"

**Solution:**
- Verify your username (email) is correct
- Check API key is valid and not expired
- Ensure TestRail URL doesn't have trailing slash
- Test connection: `validateTestRailConnection(projectId="1")`

### Problem: "No test cases generated from Epic"

**Solution:**
- Check if Epic has content in the body
- The agent will create a basic test case even without acceptance criteria
- Review Epic format - add "Acceptance Criteria" section for better results

### Problem: "Cannot find project/suite ID"

**Solution:**
- Use `validateTestRailConnection()` to verify project exists
- Check TestRail permissions - you need access to create test cases
- Verify project and suite IDs are correct numbers, not names

## Advanced Usage

### Custom Test Case Organization

Use section IDs to organize test cases:

```javascript
generateTestCasesFromEpic({
  epicIssueNumber: "123",
  testRailProjectId: "1",
  testRailSuiteId: "5",
  testRailSectionId: "42"  // Place in specific section
})
```

### Filtering Epics

Fetch specific types of Epics:

```javascript
// Get Epics for a specific release
fetchProductHubEpics({
  label: "release-2.0",
  state: "open"
})

// Get all Epics (including closed)
fetchProductHubEpics({
  label: "epic",
  state: "all"
})
```

### Iterative Workflow

1. Fetch and review Epics
2. Select interesting ones
3. Generate test cases one by one
4. Review in TestRail
5. Adjust Epic format if needed
6. Regenerate

## Best Practices

### Epic Writing Tips for Better Test Cases

1. **Use Acceptance Criteria Section**: Add `## Acceptance Criteria` header
2. **Use Scenarios**: Format as "Scenario: [name]" for clarity
3. **Use Given/When/Then**: Helps generate structured test steps
4. **Be Specific**: Clear criteria = better test cases
5. **Add Details**: More detail in Epic = more detailed test cases

### Example Well-Formatted Epic

```markdown
# User Story: Shopping Cart Checkout

## Description
As a customer, I want to complete my purchase so that I can receive my items.

## Acceptance Criteria

- Scenario: Successful checkout with credit card
  Given I have items in my cart
  And I have entered shipping information
  When I click "Complete Purchase"
  Then my order should be confirmed
  And I should receive a confirmation email

- Scenario: Checkout fails with invalid card
  Given I have items in my cart
  When I enter an invalid credit card
  Then an error message should be displayed
  And my order should not be processed

## Technical Notes
- Integration with payment gateway
- Email service for confirmations
```

This format will generate excellent test cases!

## Integration with CI/CD

You can automate test case generation in your CI pipeline:

```yaml
# GitHub Actions example
- name: Generate Test Cases
  run: |
    curl -X POST http://camunda:8080/mcp \
      -H "Content-Type: application/json" \
      -d '{
        "jsonrpc": "2.0",
        "method": "tools/call",
        "params": {
          "name": "bulkGenerateTestCases",
          "arguments": {
            "epicLabel": "sprint-current",
            "state": "open",
            "testRailProjectId": "${{ secrets.TESTRAIL_PROJECT_ID }}",
            "testRailSuiteId": "${{ secrets.TESTRAIL_SUITE_ID }}"
          }
        },
        "id": 1
      }'
```

## Getting Help

- **README**: See `/gateways/gateway-mcp/src/main/java/io/camunda/gateway/mcp/tool/testgen/README.md`
- **Implementation Details**: See `IMPLEMENTATION_SUMMARY.md` in the same directory
- **Issues**: Report problems on GitHub issues
- **Questions**: Ask in Camunda community forums

## Next Steps

1. ✅ Configure credentials
2. ✅ Deploy application
3. ✅ Connect AI assistant
4. ✅ Fetch some Epics
5. ✅ Generate your first test cases
6. ✅ Review in TestRail
7. ✅ Refine and iterate

Happy test case generation! 🚀
