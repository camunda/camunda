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
import io.camunda.client.api.response.Resource;
import io.camunda.client.api.search.enums.BatchOperationState;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.BatchOperation;
import io.camunda.process.test.impl.client.CamundaManagementClient;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ResourceAndHistoryDeletionStrategy implements CleanupStrategy {

  private static final Logger LOG =
      LoggerFactory.getLogger(ResourceAndHistoryDeletionStrategy.class);
  private static final Duration BATCH_OPERATION_TIMEOUT = Duration.ofSeconds(30);
  private static final Consumer<io.camunda.client.api.search.page.AnyPage> DEFAULT_PAGE_REQUEST =
      page -> page.limit(100);

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
      waitForBatchOperationToComplete(
          client, cancelProcessInstanceBatchOperationKey, "cancel process instances");

      final String deleteProcessInstanceBatchOperationKey =
          createDeleteProcessInstancesBatchOperation(client, testCaseStartDate);
      final String deleteDecisionInstanceBatchOperationKey =
          createDeleteDecisionInstancesBatchOperation(client, testCaseStartDate);

      waitForBatchOperationToComplete(
          client, deleteProcessInstanceBatchOperationKey, "delete process instances");
      waitForBatchOperationToComplete(
          client, deleteDecisionInstanceBatchOperationKey, "delete decision instances");

      deleteResources(client, deployments);
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

  private void deleteResources(
      final CamundaClient client, final Collection<DeploymentEvent> deployments) {
    if (deployments == null || deployments.isEmpty()) {
      LOG.debug("No deployment keys recorded for this test case. Skipping resource deletion.");
      return;
    }

    final Set<Long> resourceKeysToDelete = new LinkedHashSet<>();
    for (final DeploymentEvent deployment : deployments) {
      final List<Resource> resourcesForDeployment =
          client
              .newResourceSearchRequest()
              .filter(filter -> filter.deploymentKey(deployment.getKey()))
              .page(DEFAULT_PAGE_REQUEST)
              .send()
              .join()
              .items();
      resourcesForDeployment.stream()
          .map(Resource::getResourceKey)
          .forEach(resourceKeysToDelete::add);
    }

    for (final long resourceKey : resourceKeysToDelete) {
      final CreateBatchOperationResponse batchOperationResponse =
          client
              .newDeleteResourceCommand(resourceKey)
              .send()
              .join()
              .getCreateBatchOperationResponse();
      if (batchOperationResponse != null) {
        waitForBatchOperationToComplete(
            client, batchOperationResponse.getBatchOperationKey(), "delete resources");
      }
    }
  }

  private void waitForBatchOperationToComplete(
      final CamundaClient client, final String batchOperationKey, final String operation) {
    Awaitility.await(operation)
        .atMost(BATCH_OPERATION_TIMEOUT)
        .until(() -> hasTerminalState(client, batchOperationKey));

    final BatchOperationState status =
        client.newBatchOperationGetRequest(batchOperationKey).send().join().getStatus();
    if (status != BatchOperationState.COMPLETED
        && status != BatchOperationState.PARTIALLY_COMPLETED) {
      throw new IllegalStateException(
          String.format(
              "Batch operation %s for '%s' ended in state %s",
              batchOperationKey, operation, status));
    }
  }

  private boolean hasTerminalState(final CamundaClient client, final String batchOperationKey) {
    final BatchOperation batchOperation =
        client.newBatchOperationGetRequest(batchOperationKey).send().join();
    final BatchOperationState status = batchOperation.getStatus();
    return status == BatchOperationState.COMPLETED
        || status == BatchOperationState.PARTIALLY_COMPLETED
        || status == BatchOperationState.CANCELED
        || status == BatchOperationState.FAILED;
  }
}
