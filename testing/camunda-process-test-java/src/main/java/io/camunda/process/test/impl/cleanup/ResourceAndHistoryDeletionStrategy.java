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
import io.camunda.client.api.response.DecisionRequirements;
import io.camunda.client.api.response.Resource;
import io.camunda.client.api.search.enums.BatchOperationState;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.BatchOperation;
import io.camunda.process.test.impl.client.CamundaManagementClient;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ResourceAndHistoryDeletionStrategy implements CleanupStrategy {

  private static final Logger LOG = LoggerFactory.getLogger(ResourceAndHistoryDeletionStrategy.class);
  private static final Duration BATCH_OPERATION_TIMEOUT = Duration.ofSeconds(30);
  private static final Consumer<io.camunda.client.api.search.page.AnyPage> DEFAULT_PAGE_REQUEST =
      page -> page.limit(100);

  @Override
  public void cleanup(
      final CamundaManagementClient managementClient,
      final Supplier<CamundaClient> clientSupplier,
      final Instant testCaseStartTime) {
    if (testCaseStartTime == null) {
      LOG.warn(
          "Cannot use {} without a test case start time. Skipping runtime cleanup for this test.",
          getClass().getSimpleName());
      return;
    }

    final OffsetDateTime testCaseStartDate = testCaseStartTime.atOffset(ZoneOffset.UTC);

    try (CamundaClient client = clientSupplier.get()) {
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

      deleteResources(client, testCaseStartDate);
    }
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

  private void deleteResources(final CamundaClient client, final OffsetDateTime testCaseStartDate) {
    final Set<ResourceIdentity> resourcesToDelete = findResourcesToDelete(client, testCaseStartDate);
    for (final ResourceIdentity resource : resourcesToDelete) {
      final List<Resource> matchingResources =
          client
              .newResourceSearchRequest()
              .filter(
                  filter -> {
                    filter.resourceId(resource.resourceId());
                    filter.version(resource.version());
                    if (resource.tenantId() != null) {
                      filter.tenantId(resource.tenantId());
                    }
                  })
              .page(DEFAULT_PAGE_REQUEST)
              .send()
              .join()
              .items();

      for (final Resource matchingResource : matchingResources) {
        final CreateBatchOperationResponse batchOperationResponse =
            client
                .newDeleteResourceCommand(matchingResource.getResourceKey())
                .deleteHistory(true)
                .send()
                .join()
                .getCreateBatchOperationResponse();
        if (batchOperationResponse != null) {
          waitForBatchOperationToComplete(
              client, batchOperationResponse.getBatchOperationKey(), "delete resource history");
        }
      }
    }
  }

  private Set<ResourceIdentity> findResourcesToDelete(
      final CamundaClient client, final OffsetDateTime testCaseStartDate) {
    final Set<ResourceIdentity> resourcesToDelete = new LinkedHashSet<>();

    final List<Long> processDefinitionKeys =
        client
            .newProcessInstanceSearchRequest()
            .filter(filter -> filter.startDate(date -> date.gte(testCaseStartDate)))
            .page(DEFAULT_PAGE_REQUEST)
            .send()
            .join()
            .items()
            .stream()
            .map(processInstance -> processInstance.getProcessDefinitionKey())
            .distinct()
            .collect(Collectors.toList());

    for (final Long processDefinitionKey : processDefinitionKeys) {
      final io.camunda.client.api.search.response.ProcessDefinition processDefinition =
          client.newProcessDefinitionGetRequest(processDefinitionKey).send().join();
      resourcesToDelete.add(
          new ResourceIdentity(
              processDefinition.getProcessDefinitionId(),
              processDefinition.getVersion(),
              processDefinition.getTenantId()));
    }

    final List<Long> decisionRequirementsKeys =
        client
            .newDecisionInstanceSearchRequest()
            .filter(filter -> filter.evaluationDate(date -> date.gte(testCaseStartDate)))
            .page(DEFAULT_PAGE_REQUEST)
            .send()
            .join()
            .items()
            .stream()
            .map(decisionInstance -> decisionInstance.getDecisionDefinitionKey())
            .distinct()
            .map(
                decisionDefinitionKey ->
                    client.newDecisionDefinitionGetRequest(decisionDefinitionKey).send().join())
            .map(decisionDefinition -> decisionDefinition.getDecisionRequirementsKey())
            .collect(Collectors.toList());

    for (final Long decisionRequirementsKey : decisionRequirementsKeys) {
      final DecisionRequirements decisionRequirements =
          client.newDecisionRequirementsGetRequest(decisionRequirementsKey).send().join();
      resourcesToDelete.add(
          new ResourceIdentity(
              decisionRequirements.getDmnDecisionRequirementsId(),
              decisionRequirements.getVersion(),
              decisionRequirements.getTenantId()));
    }

    return resourcesToDelete;
  }

  private void waitForBatchOperationToComplete(
      final CamundaClient client, final String batchOperationKey, final String operation) {
    Awaitility.await(operation)
        .atMost(BATCH_OPERATION_TIMEOUT)
        .until(() -> hasTerminalState(client, batchOperationKey));

    final BatchOperationState status =
        client.newBatchOperationGetRequest(batchOperationKey).send().join().getStatus();
    if (status != BatchOperationState.COMPLETED && status != BatchOperationState.PARTIALLY_COMPLETED) {
      throw new IllegalStateException(
          String.format(
              "Batch operation %s for '%s' ended in state %s", batchOperationKey, operation, status));
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

  private static final class ResourceIdentity {
    private final String resourceId;
    private final int version;
    private final String tenantId;

    private ResourceIdentity(final String resourceId, final int version, final String tenantId) {
      this.resourceId = resourceId;
      this.version = version;
      this.tenantId = tenantId;
    }

    private String resourceId() {
      return resourceId;
    }

    private int version() {
      return version;
    }

    private String tenantId() {
      return tenantId;
    }

    @Override
    public boolean equals(final Object object) {
      if (this == object) {
        return true;
      }
      if (object == null || getClass() != object.getClass()) {
        return false;
      }
      final ResourceIdentity that = (ResourceIdentity) object;
      return version == that.version
          && Objects.equals(resourceId, that.resourceId)
          && Objects.equals(tenantId, that.tenantId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(resourceId, version, tenantId);
    }
  }
}
