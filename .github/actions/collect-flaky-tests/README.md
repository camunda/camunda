# Collect Flaky Tests Action

## Purpose

This composite action collects and aggregates flaky test results from multiple CI jobs in the Camunda orchestration cluster project. It processes results from various test suites including unit tests, integration tests with different databases (Elasticsearch, OpenSearch, RDBMS), and Docker checks.

## Inputs

| Input | Description | Required | Default |
|-------|-------------|----------|---------|
| `general-unit-tests-result` | Result of the general unit tests job | true | N/A |
| `general-unit-tests-flaky` | Flaky tests from the general unit tests job | true | N/A |
| `elasticsearch-tests-result` | Result of the Elasticsearch integration tests job | true | N/A |
| `elasticsearch-tests-flaky` | Flaky tests from the Elasticsearch integration tests job | true | N/A |
| `opensearch-tests-result` | Result of the OpenSearch integration tests job | true | N/A |
| `opensearch-tests-flaky` | Flaky tests from the OpenSearch integration tests job | true | N/A |
| `rdbms-h2-tests-result` | Result of the RDBMS H2 integration tests job | true | N/A |
| `rdbms-h2-tests-flaky` | Flaky tests from the RDBMS H2 integration tests job | true | N/A |
| `rdbms-tests-result` | Result of the RDBMS integration tests job | true | N/A |
| `rdbms-tests-flaky` | Flaky tests from the RDBMS integration tests job | true | N/A |
| `docker-checks-result` | Result of the Docker checks job | true | N/A |
| `docker-checks-flaky` | Flaky tests from the Docker checks job | true | N/A |
| `zeebe-tests-result` | Result of the Zeebe unit tests job | true | N/A |
| `zeebe-matrix-output-result` | Matrix output for Zeebe unit tests | true | N/A |
| `integration-tests-result` | Result of the integration tests job | true | N/A |
| `integration-matrix-output-result` | Matrix output for integration tests | true | N/A |

## Outputs

| Output | Description |
|--------|-------------|
| `flaky_tests_data` | JSON array of flaky tests data aggregated from all test jobs. Each object contains `job` (string) and `flaky_tests` (string) fields |

### Output Format Example

```json
[
  {
    "job": "elasticsearch-integration-tests",
    "flaky_tests": "io.camunda.it.auth.ProcessAuthorizationIT.shouldReturnProcessDefinitionStartForm(CamundaClient, CamundaClient)"
  },
  {
    "job": "general-unit-tests",
    "flaky_tests": "io.camunda.it.tenancy.VariableTenancyIT.getByKeyShouldReturnTenantOwnedVariable(CamundaClient, CamundaClient) io.camunda.it.tenancy.VariableTenancyIT.shouldReturnOnlyTenantAVariables(CamundaClient)"
  },
  {
    "job": "general-unit-tests",
    "flaky_tests": "io.camunda.it.rdbms.db.batchoperation.BatchOperationIT.shouldFindAllBatchOperationsPaged(CamundaRdbmsTestApplication)[1]"
  },
  {
    "job": "integration-tests/Zeebe - [IT] Zeebe QA - Core Features",
    "flaky_tests": "io.camunda.zeebe.it.clustering.AvailabilityTest.shouldCreateProcessWhenPartitionRecovers"
  }
]
```

## Example Usage

```yaml
- name: Collect flaky test results
  uses: ./.github/actions/collect-flaky-tests
  with:
    general-unit-tests-result: ${{ needs.general-unit-tests.result }}
    general-unit-tests-flaky: ${{ needs.general-unit-tests.outputs.flaky_tests }}
    elasticsearch-tests-result: ${{ needs.elasticsearch-integration-tests.result }}
    elasticsearch-tests-flaky: ${{ needs.elasticsearch-integration-tests.outputs.flaky_tests }}
    opensearch-tests-result: ${{ needs.opensearch-integration-tests.result }}
    opensearch-tests-flaky: ${{ needs.opensearch-integration-tests.outputs.flaky_tests }}
    rdbms-h2-tests-result: ${{ needs.rdbms-h2-integration-tests.result }}
    rdbms-h2-tests-flaky: ${{ needs.rdbms-h2-integration-tests.outputs.flaky_tests }}
    rdbms-tests-result: ${{ needs.rdbms-integration-tests.result }}
    rdbms-tests-flaky: ${{ needs.rdbms-integration-tests.outputs.flaky_tests }}
    docker-checks-result: ${{ needs.docker-checks.result }}
    docker-checks-flaky: ${{ needs.docker-checks.outputs.flaky_tests }}
    zeebe-tests-result: ${{ needs.zeebe-unit-tests.result }}
    zeebe-matrix-output-result: ${{ needs.zeebe-unit-tests.outputs.matrix_results }}
    integration-tests-result: ${{ needs.integration-tests.result }}
    integration-matrix-output-result: ${{ needs.integration-tests.outputs.matrix_results }}
```

## Internal Implementation

- Uses bash shell scripts located in `scripts/collect-flaky-tests.sh`
- Processes both single job results and matrix job results
- Aggregates flaky test data into a unified JSON structure
- Handles empty or skipped job results gracefully
