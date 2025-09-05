# Flaky Tests Summary Comment Action

## Purpose

Creates or updates a PR comment with a comprehensive summary of flaky tests across all test jobs.

## Inputs

| ·······Input······· | ·····················Description····················· | ·Required· | ·Default· |
|---------------------|-------------------------------------------------------|------------|-----------|
| `flaky-tests-data`  | JSON array containing processed flaky test data       | true       | N/A       |
| `pr-number`         | Pull request number for the comment                   | true       | N/A       |
| `branch-name`       | Branch name where the comment will be posted          | true       | N/A       |

## Expected Data Structure

### Example `flaky-tests-data` input:

```json
[
  {
    "job": "elasticsearch-integration-tests",
    "flaky_tests": "io.camunda.it.auth.ProcessAuthorizationIT.shouldReturnProcessDefinitionStartForm(CamundaClient, CamundaClient)"
  },
  {
    "job": "general-unit-tests",
    "flaky_tests": "io.camunda.it.tenancy.VariableTenancyIT.getByKeyShouldReturnTenantOwnedVariable(CamundaClient, CamundaClient) io.camunda.it.tenancy.VariableTenancyIT.shouldReturnOnlyTenantAVariables(CamundaClient)"
  }
]
```

### Example internal data structure:

```json
[
  {
    "packageName": "io.camunda.it.auth",
    "className": "ProcessAuthorizationIT",
    "methodName": "shouldReturnProcessDefinitionStartForm(CamundaClient, CamundaClient)",
    "jobs": [
      "elasticsearch-integration-tests"
    ],
    "failuresHistory": [1, 1],
    "overallRetries": 2,
    "totalRuns": 2
  }
]
```

## Outputs

This action does not produce outputs. It creates or updates a PR comment directly.

## Example Usage

```yaml
- name: Post flaky tests summary comment
  uses: ./.github/actions/flaky-tests-summary-comment
  with:
    flaky-tests-data: ${{ steps.collect-flaky-tests.outputs.flaky_tests_data }}
    pr-number: ${{ github.event.pull_request.number }}
    branch-name: ${{ github.head_ref }}
```

## Features

- Processes JSON array of flaky test data from multiple test jobs
- Creates formatted PR comments with test failure summaries
- Updates existing comments to avoid spam
- Handles empty or invalid data gracefully
- Provides detailed logging for debugging

## Internal Implementation

- Uses GitHub Script action for API interactions
- Processes data through modular JavaScript files:
  - `src/flaky-tests-data-processor.js` - Structures and processes raw flaky test data
  - `src/flaky-tests-comment-generator.js` - Generates formatted comment content
  - `src/github-api.js` - Handles GitHub API interactions
  - `src/helpers.js` - Utility functions

## Dependencies

- `actions/github-script@v7` for GitHub API access
- Modular JavaScript processors in `src/` directory

