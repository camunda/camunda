/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.process.test.impl.cleanup;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.CreateBatchOperationResponse;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.search.enums.BatchOperationState;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.BatchOperation;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.process.test.impl.client.CamundaManagementClient;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ResourceAndHistoryDeletionStrategy implements CleanupStrategy {

  private static final Logger LOG =
      LoggerFactory.getLogger(ResourceAndHistoryDeletionStrategy.class);

  private static final Duration BATCH_OPERATION_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration BATCH_OPERATION_POLL_INTERVAL = Duration.ofMillis(100);

  @Override
  public void cleanup(
      final CamundaManagementClient managementClient,
      final Supplier<CamundaClient> clientSupplier,
      final Instant testCaseStartTime,
      final Collection<DeploymentEvent> deployments) {
    if (testCaseStartTime == null) {
      LOG.warn(
          "Cannot use {} without a test case start time. Skipping runtime cleanup for this test.",
          getClass().getSimpleName());
      return;
    }

    LOG.debug("Deleting runtime data using resource and history deletion strategy");
    final Instant startTime = Instant.now();
    final OffsetDateTime testCaseStartDate = testCaseStartTime.atOffset(ZoneOffset.UTC);

    try (final CamundaClient client = clientSupplier.get()) {
      final String cancelProcessInstanceBatchOperationKey =
          createCancelProcessInstancesBatchOperation(client, testCaseStartDate);
      // Wait for the cancel operation to complete before proceeding with the delete operations
      waitForBatchOperationsToComplete(
          client, Collections.singletonList(cancelProcessInstanceBatchOperationKey));

      final List<String> batchOperationKeys = new ArrayList<>();

      final String deleteProcessInstanceBatchOperationKey =
          createDeleteProcessInstancesBatchOperation(client, testCaseStartDate);
      batchOperationKeys.add(deleteProcessInstanceBatchOperationKey);

      final String deleteDecisionInstanceBatchOperationKey =
          createDeleteDecisionInstancesBatchOperation(client, testCaseStartDate);
      batchOperationKeys.add(deleteDecisionInstanceBatchOperationKey);

      final List<String> deleteResourcesBatchOperationKeys = deleteResources(client, deployments);
      batchOperationKeys.addAll(deleteResourcesBatchOperationKeys);

      waitForBatchOperationsToComplete(client, batchOperationKeys);
    }
    final Duration duration = Duration.between(startTime, Instant.now());
    LOG.debug("Runtime data deleted in {}", duration);
  }

  private String createCancelProcessInstancesBatchOperation(
      final CamundaClient client, final OffsetDateTime testCaseStartDate) {
    return client
        .newCreateBatchOperationCommand()
        .processInstanceCancel()
        .filter(
            filter ->
                filter
                    .startDate(date -> date.gte(testCaseStartDate))
                    .state(ProcessInstanceState.ACTIVE))
        .send()
        .join()
        .getBatchOperationKey();
  }

  private String createDeleteProcessInstancesBatchOperation(
      final CamundaClient client, final OffsetDateTime testCaseStartDate) {
    return client
        .newCreateBatchOperationCommand()
        .deleteProcessInstance()
        .filter(
            filter ->
                filter
                    .startDate(date -> date.gte(testCaseStartDate))
                    .state(
                        state ->
                            state.in(
                                ProcessInstanceState.COMPLETED, ProcessInstanceState.TERMINATED)))
        .send()
        .join()
        .getBatchOperationKey();
  }

  private String createDeleteDecisionInstancesBatchOperation(
      final CamundaClient client, final OffsetDateTime testCaseStartDate) {
    return client
        .newCreateBatchOperationCommand()
        .deleteDecisionInstance()
        .filter(filter -> filter.evaluationDate(date -> date.gte(testCaseStartDate)))
        .send()
        .join()
        .getBatchOperationKey();
  }

  private List<String> deleteResources(
      final CamundaClient client, final Collection<DeploymentEvent> deployments) {
    if (deployments == null || deployments.isEmpty()) {
      LOG.debug("No deployment keys recorded for this test case. Skipping resource deletion.");
      return Collections.emptyList();
    }

    final Set<Long> resourceKeysToDelete = new LinkedHashSet<>();

    deployments.forEach(
        deployment -> {
          // Delete all process definitions
          deployment
              .getProcesses()
              .forEach(process -> resourceKeysToDelete.add(process.getProcessDefinitionKey()));

          // Delete all decision requirements. Ignore decision definitions, as they are deleted when
          // the decision requirements are deleted.
          deployment
              .getDecisionRequirements()
              .forEach(
                  decisionRequirements ->
                      resourceKeysToDelete.add(decisionRequirements.getDecisionRequirementsKey()));
        });

    final List<String> batchOperationKeys = new ArrayList<>();

    resourceKeysToDelete.forEach(
        resourceKey -> {
          final CreateBatchOperationResponse batchOperationResponse =
              client
                  .newDeleteResourceCommand(resourceKey)
                  .deleteHistory(true)
                  .send()
                  .join()
                  .getCreateBatchOperationResponse();
          if (batchOperationResponse != null) {
            batchOperationKeys.add(batchOperationResponse.getBatchOperationKey());
          }
        });

    return batchOperationKeys;
  }

  private void waitForBatchOperationsToComplete(
      final CamundaClient client, final List<String> batchOperationKeys) {

    final SearchResponse<BatchOperation> batchOperationResponse =
        Awaitility.await()
            .atMost(BATCH_OPERATION_TIMEOUT)
            .pollInterval(BATCH_OPERATION_POLL_INTERVAL)
            .until(
                () ->
                    client
                        .newBatchOperationSearchRequest()
                        .filter(
                            filter -> filter.batchOperationKey(key -> key.in(batchOperationKeys)))
                        .send()
                        .join(),
                response ->
                    response.items().size() == batchOperationKeys.size()
                        && response.items().stream()
                            .allMatch(ResourceAndHistoryDeletionStrategy::isBatchOperationEnded));

    final String batchOperationSummary =
        batchOperationResponse.items().stream()
            .map(ResourceAndHistoryDeletionStrategy::formatBatchOperation)
            .collect(Collectors.joining("\n"));

    final boolean batchOperationsCompleted =
        batchOperationResponse.items().stream()
            .allMatch(
                batchOperation ->
                    batchOperation.getStatus() == BatchOperationState.COMPLETED
                        || batchOperation.getStatus() == BatchOperationState.PARTIALLY_COMPLETED);

    if (batchOperationsCompleted) {
      LOG.debug("Batch operation summary:\n{}", batchOperationSummary);
    } else {
      LOG.warn("Batch operation summary:\n{}", batchOperationSummary);
      throw new IllegalStateException(
          "Some deletion batch operations failed to complete. See the batch operation summary in the logs for details.");
    }
  }

  private static String formatBatchOperation(final BatchOperation batchOperation) {
    final Duration duration =
        batchOperation.getEndDate() != null
            ? Duration.between(batchOperation.getStartDate(), batchOperation.getEndDate())
            : Duration.ZERO;
    final String errors =
        batchOperation.getErrors().stream()
            .map(
                error ->
                    String.format(
                        "Error: type = %s, message = %s", error.getType(), error.getMessage()))
            .collect(Collectors.joining(", ", "[", "]"));
    return String.format(
        "Batch operation: key = %s, type = %s, status = %s, duration = %s, errors = %s",
        batchOperation.getBatchOperationKey(),
        batchOperation.getType(),
        batchOperation.getStatus(),
        duration,
        errors);
  }

  private static boolean isBatchOperationEnded(final BatchOperation batchOperation) {
    return batchOperation.getStatus() == BatchOperationState.COMPLETED
        || batchOperation.getStatus() == BatchOperationState.PARTIALLY_COMPLETED
        || batchOperation.getStatus() == BatchOperationState.CANCELED
        || batchOperation.getStatus() == BatchOperationState.FAILED;
  }
}
