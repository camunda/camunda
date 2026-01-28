# Test Case Generator Agent

## Overview

The Test Case Generator Agent is an MCP (Model Context Protocol) tool that automates the generation of test cases from GitHub Epics in the [camunda/product-hub](https://github.com/camunda/product-hub/) repository and creates them in TestRail using the [TestRail MCP server](https://github.com/bun913/mcp-testrail).

## Features

- **Fetch Product Hub Epics**: Retrieve Epics from the camunda/product-hub GitHub repository
- **Generate Test Cases**: Automatically transform Epic requirements into structured test cases
- **TestRail Integration**: Create test cases directly in TestRail with proper categorization
- **Bulk Operations**: Process multiple Epics in batch mode
- **Connection Validation**: Verify TestRail API configuration before creating test cases

## Configuration

### Required Environment Variables

Add the following configuration to your application properties:

```yaml
camunda:
  mcp:
    github:
      token: ${GITHUB_TOKEN}  # GitHub personal access token with repo read access
    testrail:
      url: ${TESTRAIL_URL}  # TestRail instance URL (e.g., https://yourcompany.testrail.com)
      username: ${TESTRAIL_USERNAME}  # TestRail username
      api-key: ${TESTRAIL_API_KEY}  # TestRail API key
```

### GitHub Token Setup

1. Go to GitHub Settings > Developer settings > Personal access tokens
2. Generate a new token with `repo` read access
3. Set the token as `GITHUB_TOKEN` environment variable

### TestRail API Key Setup

1. Log in to TestRail
2. Go to My Settings > API Keys
3. Generate a new API key
4. Set your username and API key as environment variables

## MCP Tools

### 1. fetchProductHubEpics

Fetches Epics from the camunda/product-hub repository.

**Parameters:**
- `label` (optional): Filter Epics by label (default: "epic")
- `state` (optional): Filter by state - "open", "closed", or "all" (default: "open")

**Example:**
```json
{
  "tool": "fetchProductHubEpics",
  "arguments": {
    "label": "epic",
    "state": "open"
  }
}
```

### 2. generateTestCasesFromEpic

Generates and creates test cases in TestRail from a single Epic.

**Parameters:**
- `epicIssueNumber` (required): GitHub issue number of the Epic
- `testRailProjectId` (required): TestRail project ID
- `testRailSuiteId` (required): TestRail suite ID
- `testRailSectionId` (optional): TestRail section ID for organization

**Example:**
```json
{
  "tool": "generateTestCasesFromEpic",
  "arguments": {
    "epicIssueNumber": "123",
    "testRailProjectId": "1",
    "testRailSuiteId": "5",
    "testRailSectionId": "42"
  }
}
```

### 3. bulkGenerateTestCases

Processes multiple Epics and creates test cases in bulk.

**Parameters:**
- `epicLabel` (optional): Label filter (default: "epic")
- `state` (optional): State filter (default: "open")
- `testRailProjectId` (required): TestRail project ID
- `testRailSuiteId` (required): TestRail suite ID

**Example:**
```json
{
  "tool": "bulkGenerateTestCases",
  "arguments": {
    "epicLabel": "epic",
    "state": "open",
    "testRailProjectId": "1",
    "testRailSuiteId": "5"
  }
}
```

### 4. validateTestRailConnection

Validates TestRail API connection and configuration.

**Parameters:**
- `testRailProjectId` (required): TestRail project ID to validate

**Example:**
```json
{
  "tool": "validateTestRailConnection",
  "arguments": {
    "testRailProjectId": "1"
  }
}
```

## Test Case Transformation

The transformer analyzes Epic content and extracts structured test cases:

### Acceptance Criteria Parsing

If the Epic contains an "Acceptance Criteria" section, the transformer will:
- Extract individual scenarios
- Parse Given/When/Then format
- Create separate test cases for each scenario

**Epic Example:**
```markdown
## Acceptance Criteria

- Scenario: User can create a new process
  Given a user is logged in
  When they click "Create Process"
  Then a new process is created
```

### Basic Transformation

For Epics without structured criteria, a basic test case is created with:
- Title: "Verify [Epic Title]"
- Steps: Generic verification steps
- Expected Result: Reference to Epic requirements

## Architecture

```
TestCaseGeneratorTools (MCP Tool)
├── GitHubProductHubService (GitHub API integration)
├── TestRailIntegrationService (TestRail API integration)
└── TestCaseTransformer (Epic → Test Case transformation)
```

## Error Handling

All tools include comprehensive error handling:
- GitHub API failures return detailed error messages
- TestRail API errors include status codes and response bodies
- Missing configuration errors provide clear guidance
- Invalid Epic formats gracefully fallback to basic test case generation

## Testing

Tests follow the established MCP testing patterns:

```bash
./mvnw -pl gateways/gateway-mcp -am test -DskipITs -DskipChecks -Dtest=TestCaseGeneratorToolsTest -T1C
```

## Integration with AI Assistants

This MCP tool is designed to be used by AI assistants like Claude or ChatGPT through the MCP protocol. Example workflow:

1. AI fetches Epics: `fetchProductHubEpics(label="epic", state="open")`
2. AI reviews Epic content
3. AI generates test cases: `generateTestCasesFromEpic(epicIssueNumber="123", ...)`
4. TestRail is automatically updated with structured test cases

## Security Considerations

- **Never commit credentials**: Use environment variables for all secrets
- **Token scope**: GitHub token only needs `repo:read` access
- **API key rotation**: Regularly rotate TestRail API keys
- **HTTPS**: All API communications use HTTPS

## Troubleshooting

### GitHub Rate Limiting

If you encounter rate limiting:
- Ensure you're using an authenticated GitHub token
- Authenticated requests have a limit of 5000 requests/hour

### TestRail Connection Issues

If TestRail connection fails:
1. Verify TestRail URL is correct
2. Check username and API key are valid
3. Ensure network connectivity to TestRail instance
4. Use `validateTestRailConnection` tool to diagnose

### No Test Cases Generated

If no test cases are generated from an Epic:
- Check Epic format contains requirements
- Verify Epic is not empty
- Review transformation logs for parsing errors
- A basic test case will be created as fallback

## Future Enhancements

Potential improvements:
- Support for custom test case templates
- Integration with TestRail MCP server for advanced features
- AI-powered test case enhancement and suggestion
- Automatic linking of test cases to process definitions
- Support for multiple Epic formats and templates

## References

- [camunda/product-hub repository](https://github.com/camunda/product-hub/)
- [TestRail MCP Server](https://github.com/bun913/mcp-testrail)
- [TestRail API Documentation](https://www.gurock.com/testrail/docs/api)
- [GitHub API Documentation](https://docs.github.com/en/rest)
- [Model Context Protocol](https://modelcontextprotocol.io/)
