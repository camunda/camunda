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

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.response.CreateBatchOperationResponse;
import io.camunda.client.api.response.Decision;
import io.camunda.client.api.response.DecisionRequirements;
import io.camunda.client.api.response.DeleteResourceResponse;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.search.enums.BatchOperationState;
import io.camunda.client.api.search.filter.DecisionInstanceFilter;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.response.BatchOperation;
import io.camunda.process.test.impl.client.CamundaManagementClient;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResourceAndHistoryDeletionStrategyTest {

  @Mock private CamundaManagementClient managementClient;
  @Mock private Supplier<CamundaClient> clientSupplier;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Test
  void shouldSkipCleanupWhenTestCaseStartTimeIsMissing() {
    // given
    final ResourceAndHistoryDeletionStrategy strategy = new ResourceAndHistoryDeletionStrategy();

    // when
    strategy.cleanup(managementClient, clientSupplier, null, Collections.emptySet());

    // then
    verify(clientSupplier, never()).get();
  }

  @Test
  void shouldDeleteTestCaseDataAndDeploymentResources() {
    // given
    final ResourceAndHistoryDeletionStrategy strategy = new ResourceAndHistoryDeletionStrategy();
    final DeploymentEvent deployment = createDeployment(11L, 12L, 13L);
    when(clientSupplier.get()).thenReturn(camundaClient);
    mockBatchOperationExecution();
    mockDeleteResourceBatchOperation("delete-resource");

    // when
    strategy.cleanup(
        managementClient,
        clientSupplier,
        Instant.parse("2026-01-01T00:00:00Z"),
        Collections.singletonList(deployment));

    // then
    verify(camundaClient).newDeleteResourceCommand(11L);
    verify(camundaClient).newDeleteResourceCommand(12L);
    verify(camundaClient).newDeleteResourceCommand(13L);
  }

  @Test
  void shouldSkipResourceDeletionWhenDeploymentsAreEmpty() {
    // given
    final ResourceAndHistoryDeletionStrategy strategy = new ResourceAndHistoryDeletionStrategy();
    when(clientSupplier.get()).thenReturn(camundaClient);
    mockBatchOperationExecution();

    // when
    strategy.cleanup(
        managementClient,
        clientSupplier,
        Instant.parse("2026-01-01T00:00:00Z"),
        Collections.emptyList());

    // then
    verify(camundaClient, never()).newDeleteResourceCommand(anyLong());
  }

  private DeploymentEvent createDeployment(
      final long processDefinitionKey, final long decisionKey, final long decisionRequirementsKey) {
    final Process process = mock(Process.class);
    when(process.getProcessDefinitionKey()).thenReturn(processDefinitionKey);
    final Decision decision = mock(Decision.class);
    when(decision.getDecisionKey()).thenReturn(decisionKey);
    final DecisionRequirements decisionRequirements = mock(DecisionRequirements.class);
    when(decisionRequirements.getDecisionRequirementsKey()).thenReturn(decisionRequirementsKey);

    final DeploymentEvent deployment = mock(DeploymentEvent.class);
    when(deployment.getProcesses()).thenReturn(Arrays.asList(process));
    when(deployment.getDecisions()).thenReturn(Arrays.asList(decision));
    when(deployment.getDecisionRequirements()).thenReturn(Arrays.asList(decisionRequirements));
    return deployment;
  }

  private void mockBatchOperationExecution() {
    final CamundaFuture<CreateBatchOperationResponse> cancelFuture = mock(CamundaFuture.class);
    final CamundaFuture<CreateBatchOperationResponse> deleteProcessFuture =
        mock(CamundaFuture.class);
    final CamundaFuture<CreateBatchOperationResponse> deleteDecisionFuture =
        mock(CamundaFuture.class);

    final CreateBatchOperationResponse cancelResponse = mock(CreateBatchOperationResponse.class);
    final CreateBatchOperationResponse deleteProcessResponse =
        mock(CreateBatchOperationResponse.class);
    final CreateBatchOperationResponse deleteDecisionResponse =
        mock(CreateBatchOperationResponse.class);
    when(cancelResponse.getBatchOperationKey()).thenReturn("cancel");
    when(deleteProcessResponse.getBatchOperationKey()).thenReturn("delete-process");
    when(deleteDecisionResponse.getBatchOperationKey()).thenReturn("delete-decision");
    when(cancelFuture.join()).thenReturn(cancelResponse);
    when(deleteProcessFuture.join()).thenReturn(deleteProcessResponse);
    when(deleteDecisionFuture.join()).thenReturn(deleteDecisionResponse);

    when(camundaClient
            .newCreateBatchOperationCommand()
            .processInstanceCancel()
            .filter(org.mockito.ArgumentMatchers.<Consumer<ProcessInstanceFilter>>any())
            .send())
        .thenReturn(cancelFuture);
    when(camundaClient
            .newCreateBatchOperationCommand()
            .deleteProcessInstance()
            .filter(org.mockito.ArgumentMatchers.<Consumer<ProcessInstanceFilter>>any())
            .send())
        .thenReturn(deleteProcessFuture);
    when(camundaClient
            .newCreateBatchOperationCommand()
            .deleteDecisionInstance()
            .filter(org.mockito.ArgumentMatchers.<Consumer<DecisionInstanceFilter>>any())
            .send())
        .thenReturn(deleteDecisionFuture);

    final BatchOperation completedBatchOperation = mock(BatchOperation.class);
    when(completedBatchOperation.getStatus()).thenReturn(BatchOperationState.COMPLETED);
    final CamundaFuture<BatchOperation> batchOperationFuture = mock(CamundaFuture.class);
    when(batchOperationFuture.join()).thenReturn(completedBatchOperation);
    when(camundaClient.newBatchOperationGetRequest(org.mockito.ArgumentMatchers.anyString()).send())
        .thenReturn(batchOperationFuture);
  }

  private void mockDeleteResourceBatchOperation(final String batchOperationKey) {
    final CreateBatchOperationResponse createBatchOperationResponse =
        mock(CreateBatchOperationResponse.class);
    when(createBatchOperationResponse.getBatchOperationKey()).thenReturn(batchOperationKey);

    final DeleteResourceResponse deleteResourceResponse = mock(DeleteResourceResponse.class);
    when(deleteResourceResponse.getCreateBatchOperationResponse())
        .thenReturn(createBatchOperationResponse);

    final CamundaFuture<DeleteResourceResponse> deleteResourceFuture = mock(CamundaFuture.class);
    when(deleteResourceFuture.join()).thenReturn(deleteResourceResponse);
    when(camundaClient.newDeleteResourceCommand(anyLong()).deleteHistory(true).send())
        .thenReturn(deleteResourceFuture);
  }
}
