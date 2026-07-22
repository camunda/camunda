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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
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
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.filter.DecisionInstanceFilter;
import io.camunda.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.filter.builder.ProcessInstanceStateProperty;
import io.camunda.process.test.impl.client.CamundaManagementClient;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResourceAndHistoryDeletionStrategyTest {

  @Mock private CamundaManagementClient managementClient;
  @Mock private Supplier<CamundaClient> clientSupplier;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private Process process;
  @Mock private Decision decision;
  @Mock private DecisionRequirements decisionRequirements;
  @Mock private DeploymentEvent deployment;
  @Mock private CreateBatchOperationResponse cancelResponse;
  @Mock private CreateBatchOperationResponse deleteProcessResponse;
  @Mock private CreateBatchOperationResponse deleteDecisionResponse;
  @Mock private CreateBatchOperationResponse deleteResourceBatchResponse;
  @Mock private DeleteResourceResponse deleteResourceResponse;
  @Mock private CamundaFuture<DeleteResourceResponse> deleteResourceFuture;
  @Mock private ProcessInstanceFilter cancelProcessInstanceFilter;
  @Mock private DateTimeProperty cancelDateTimeProperty;
  @Mock private ProcessInstanceFilter deleteProcessInstanceFilter;
  @Mock private DateTimeProperty deleteProcessDateTimeProperty;
  @Mock private ProcessInstanceStateProperty processInstanceStateProperty;
  @Mock private DecisionInstanceFilter deleteDecisionInstanceFilter;
  @Mock private DateTimeProperty deleteDecisionDateTimeProperty;

  @Captor private ArgumentCaptor<Consumer<ProcessInstanceFilter>> cancelFilterCaptor;
  @Captor private ArgumentCaptor<Consumer<ProcessInstanceFilter>> deleteProcessFilterCaptor;
  @Captor private ArgumentCaptor<Consumer<DecisionInstanceFilter>> deleteDecisionFilterCaptor;
  @Captor private ArgumentCaptor<Consumer<DateTimeProperty>> cancelDateFilterCaptor;
  @Captor private ArgumentCaptor<Consumer<DateTimeProperty>> deleteProcessDateFilterCaptor;
  @Captor private ArgumentCaptor<Consumer<ProcessInstanceStateProperty>> processStateFilterCaptor;
  @Captor private ArgumentCaptor<Consumer<DateTimeProperty>> deleteDecisionDateFilterCaptor;

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
    final Instant testCaseStartTime = Instant.parse("2026-01-01T00:00:00Z");
    mockDeployment(11L, 12L, 13L);
    when(clientSupplier.get()).thenReturn(camundaClient);
    mockBatchOperationExecution(BatchOperationState.COMPLETED, BatchOperationState.COMPLETED);
    mockDeleteResourceBatchOperation("delete-resource");
    clearInvocations(
        camundaClient.newCreateBatchOperationCommand().processInstanceCancel(),
        camundaClient.newCreateBatchOperationCommand().deleteProcessInstance(),
        camundaClient.newCreateBatchOperationCommand().deleteDecisionInstance());

    // when
    strategy.cleanup(
        managementClient, clientSupplier, testCaseStartTime, Collections.singletonList(deployment));

    // then
    verifyCancelAndDeleteFilters(testCaseStartTime.atOffset(ZoneOffset.UTC));
    verifyProcessInstancesAreCancelledBeforeDeletion();
    verify(camundaClient).newDeleteResourceCommand(11L);
    verify(camundaClient).newDeleteResourceCommand(12L);
    verify(camundaClient).newDeleteResourceCommand(13L);
    verify(camundaClient.newCreateBatchOperationCommand(), atLeastOnce()).deleteProcessInstance();
    verify(camundaClient.newCreateBatchOperationCommand(), atLeastOnce()).deleteDecisionInstance();
  }

  @Test
  void shouldSkipResourceDeletionWhenDeploymentsAreEmpty() {
    // given
    final ResourceAndHistoryDeletionStrategy strategy = new ResourceAndHistoryDeletionStrategy();
    when(clientSupplier.get()).thenReturn(camundaClient);
    mockBatchOperationExecution(BatchOperationState.COMPLETED, BatchOperationState.COMPLETED);

    // when
    strategy.cleanup(
        managementClient,
        clientSupplier,
        Instant.parse("2026-01-01T00:00:00Z"),
        Collections.emptyList());

    // then
    verify(camundaClient, never()).newDeleteResourceCommand(anyLong());
  }

  @Test
  void shouldFailWhenDeleteProcessInstancesBatchOperationIsNotCompleted() {
    // given
    final ResourceAndHistoryDeletionStrategy strategy = new ResourceAndHistoryDeletionStrategy();
    when(clientSupplier.get()).thenReturn(camundaClient);
    mockBatchOperationExecution(BatchOperationState.FAILED, BatchOperationState.COMPLETED);

    // when
    // then
    assertThatThrownBy(
            () ->
                strategy.cleanup(
                    managementClient,
                    clientSupplier,
                    Instant.parse("2026-01-01T00:00:00Z"),
                    Collections.emptyList()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("delete process instances");
  }

  @Test
  void shouldFailWhenDeleteDecisionInstancesBatchOperationIsNotCompleted() {
    // given
    final ResourceAndHistoryDeletionStrategy strategy = new ResourceAndHistoryDeletionStrategy();
    when(clientSupplier.get()).thenReturn(camundaClient);
    mockBatchOperationExecution(BatchOperationState.COMPLETED, BatchOperationState.FAILED);

    // when
    // then
    assertThatThrownBy(
            () ->
                strategy.cleanup(
                    managementClient,
                    clientSupplier,
                    Instant.parse("2026-01-01T00:00:00Z"),
                    Collections.emptyList()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("delete decision instances");
  }

  private void mockDeployment(
      final long processDefinitionKey, final long decisionKey, final long decisionRequirementsKey) {
    when(process.getProcessDefinitionKey()).thenReturn(processDefinitionKey);
    when(decision.getDecisionKey()).thenReturn(decisionKey);
    when(decisionRequirements.getDecisionRequirementsKey()).thenReturn(decisionRequirementsKey);

    when(deployment.getProcesses()).thenReturn(Arrays.asList(process));
    when(deployment.getDecisions()).thenReturn(Arrays.asList(decision));
    when(deployment.getDecisionRequirements()).thenReturn(Arrays.asList(decisionRequirements));
  }

  private void mockBatchOperationExecution(
      final BatchOperationState deleteProcessState, final BatchOperationState deleteDecisionState) {
    when(cancelResponse.getBatchOperationKey()).thenReturn("cancel");
    when(deleteProcessResponse.getBatchOperationKey()).thenReturn("delete-process");
    when(deleteDecisionResponse.getBatchOperationKey()).thenReturn("delete-decision");
    when(camundaClient
            .newCreateBatchOperationCommand()
            .processInstanceCancel()
            .filter(anyProcessInstanceFilter())
            .send()
            .join())
        .thenReturn(cancelResponse);
    when(camundaClient
            .newCreateBatchOperationCommand()
            .deleteProcessInstance()
            .filter(anyProcessInstanceFilter())
            .send()
            .join())
        .thenReturn(deleteProcessResponse);
    when(camundaClient
            .newCreateBatchOperationCommand()
            .deleteDecisionInstance()
            .filter(anyDecisionInstanceFilter())
            .send()
            .join())
        .thenReturn(deleteDecisionResponse);

    when(camundaClient.newBatchOperationGetRequest(eq("cancel")).send().join().getStatus())
        .thenReturn(BatchOperationState.COMPLETED);
    when(camundaClient.newBatchOperationGetRequest(eq("delete-process")).send().join().getStatus())
        .thenReturn(deleteProcessState);
    when(camundaClient.newBatchOperationGetRequest(eq("delete-decision")).send().join().getStatus())
        .thenReturn(deleteDecisionState);
    when(camundaClient.newBatchOperationGetRequest(eq("delete-resource")).send().join().getStatus())
        .thenReturn(BatchOperationState.COMPLETED);
  }

  private void mockDeleteResourceBatchOperation(final String batchOperationKey) {
    when(deleteResourceBatchResponse.getBatchOperationKey()).thenReturn(batchOperationKey);
    when(deleteResourceResponse.getCreateBatchOperationResponse())
        .thenReturn(deleteResourceBatchResponse);
    when(deleteResourceFuture.join()).thenReturn(deleteResourceResponse);
    when(camundaClient.newDeleteResourceCommand(anyLong()).deleteHistory(anyBoolean()).send())
        .thenReturn(deleteResourceFuture);
  }

  private Consumer<ProcessInstanceFilter> anyProcessInstanceFilter() {
    return any();
  }

  private Consumer<DecisionInstanceFilter> anyDecisionInstanceFilter() {
    return any();
  }

  private void verifyCancelAndDeleteFilters(final OffsetDateTime expectedDate) {
    verify(camundaClient.newCreateBatchOperationCommand().processInstanceCancel())
        .filter(cancelFilterCaptor.capture());
    verify(camundaClient.newCreateBatchOperationCommand().deleteProcessInstance())
        .filter(deleteProcessFilterCaptor.capture());
    verify(camundaClient.newCreateBatchOperationCommand().deleteDecisionInstance())
        .filter(deleteDecisionFilterCaptor.capture());

    verifyCancelProcessFilter(cancelFilterCaptor.getValue(), expectedDate);
    verifyDeleteProcessFilter(deleteProcessFilterCaptor.getValue(), expectedDate);
    verifyDeleteDecisionFilter(deleteDecisionFilterCaptor.getValue(), expectedDate);
  }

  private void verifyCancelProcessFilter(
      final Consumer<ProcessInstanceFilter> filterConsumer, final OffsetDateTime expectedDate) {
    when(cancelProcessInstanceFilter.startDate(anyDateFilter()))
        .thenReturn(cancelProcessInstanceFilter);
    when(cancelProcessInstanceFilter.state(any(ProcessInstanceState.class)))
        .thenReturn(cancelProcessInstanceFilter);

    filterConsumer.accept(cancelProcessInstanceFilter);

    verify(cancelProcessInstanceFilter).startDate(cancelDateFilterCaptor.capture());
    cancelDateFilterCaptor.getValue().accept(cancelDateTimeProperty);
    verify(cancelDateTimeProperty).gte(expectedDate);
    verify(cancelProcessInstanceFilter).state(ProcessInstanceState.ACTIVE);
  }

  private void verifyDeleteProcessFilter(
      final Consumer<ProcessInstanceFilter> filterConsumer, final OffsetDateTime expectedDate) {
    when(deleteProcessInstanceFilter.startDate(anyDateFilter()))
        .thenReturn(deleteProcessInstanceFilter);
    when(deleteProcessInstanceFilter.state(anyStateFilter()))
        .thenReturn(deleteProcessInstanceFilter);

    filterConsumer.accept(deleteProcessInstanceFilter);

    verify(deleteProcessInstanceFilter).startDate(deleteProcessDateFilterCaptor.capture());
    deleteProcessDateFilterCaptor.getValue().accept(deleteProcessDateTimeProperty);
    verify(deleteProcessDateTimeProperty).gte(expectedDate);

    verify(deleteProcessInstanceFilter).state(processStateFilterCaptor.capture());
    processStateFilterCaptor.getValue().accept(processInstanceStateProperty);
    verify(processInstanceStateProperty)
        .in(ProcessInstanceState.COMPLETED, ProcessInstanceState.TERMINATED);
  }

  private void verifyDeleteDecisionFilter(
      final Consumer<DecisionInstanceFilter> filterConsumer, final OffsetDateTime expectedDate) {
    when(deleteDecisionInstanceFilter.evaluationDate(anyDateFilter()))
        .thenReturn(deleteDecisionInstanceFilter);

    filterConsumer.accept(deleteDecisionInstanceFilter);

    verify(deleteDecisionInstanceFilter).evaluationDate(deleteDecisionDateFilterCaptor.capture());
    deleteDecisionDateFilterCaptor.getValue().accept(deleteDecisionDateTimeProperty);
    verify(deleteDecisionDateTimeProperty).gte(expectedDate);
  }

  private void verifyProcessInstancesAreCancelledBeforeDeletion() {
    final InOrder inOrder =
        inOrder(
            camundaClient.newCreateBatchOperationCommand().processInstanceCancel(),
            camundaClient.newCreateBatchOperationCommand().deleteProcessInstance());
    inOrder
        .verify(camundaClient.newCreateBatchOperationCommand().processInstanceCancel())
        .filter(anyProcessInstanceFilter());
    inOrder
        .verify(camundaClient.newCreateBatchOperationCommand().deleteProcessInstance())
        .filter(anyProcessInstanceFilter());
  }

  @SuppressWarnings("unchecked")
  private Consumer<DateTimeProperty> anyDateFilter() {
    return any();
  }

  @SuppressWarnings("unchecked")
  private Consumer<ProcessInstanceStateProperty> anyStateFilter() {
    return any();
  }
}
