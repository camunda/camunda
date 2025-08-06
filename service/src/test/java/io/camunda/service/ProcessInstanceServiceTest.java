/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.ProcessInstanceSearchClient;
import io.camunda.search.clients.SequenceFlowSearchClient;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.entities.SequenceFlowEntity;
import io.camunda.search.exception.ResourceAccessDeniedException;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SequenceFlowQuery;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceMigrateBatchOperationRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceModifyBatchOperationRequest;
import io.camunda.service.authorization.Authorizations;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateBatchOperationRequest;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationProcessInstanceModificationMoveInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationMappingInstruction;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public final class ProcessInstanceServiceTest {

  private ProcessInstanceServices services;
  private ProcessInstanceSearchClient processInstanceSearchClient;
  private SequenceFlowSearchClient sequenceFlowSearchClient;
  private IncidentServices incidentServices;
  private SecurityContextProvider securityContextProvider;
  private CamundaAuthentication authentication;
  private BrokerClient brokerClient;
  private ApiServicesExecutorProvider executorProvider;

  @BeforeEach
  public void before() {
    processInstanceSearchClient = mock(ProcessInstanceSearchClient.class);
    sequenceFlowSearchClient = mock(SequenceFlowSearchClient.class);
    authentication = CamundaAuthentication.none();
    incidentServices = mock(IncidentServices.class);
    when(processInstanceSearchClient.withSecurityContext(any()))
        .thenReturn(processInstanceSearchClient);
    when(sequenceFlowSearchClient.withSecurityContext(any())).thenReturn(sequenceFlowSearchClient);
    when(incidentServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(incidentServices);
    securityContextProvider = mock(SecurityContextProvider.class);
    brokerClient = mock(BrokerClient.class);
    executorProvider = mock(ApiServicesExecutorProvider.class);
    when(executorProvider.getExecutor()).thenReturn(ForkJoinPool.commonPool());
    services =
        new ProcessInstanceServices(
            brokerClient,
            securityContextProvider,
            processInstanceSearchClient,
            sequenceFlowSearchClient,
            incidentServices,
            authentication,
            executorProvider);
  }

  @Test
  public void shouldReturnProcessInstance() {
    // given
    final var result = mock(SearchQueryResult.class);
    when(processInstanceSearchClient.searchProcessInstances(any())).thenReturn(result);

    final ProcessInstanceQuery searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery().build();

    // when
    final SearchQueryResult<ProcessInstanceEntity> searchQueryResult = services.search(searchQuery);

    // then
    assertThat(searchQueryResult).isEqualTo(result);
  }

  @Test
  public void shouldReturnProcessInstanceSequenceFlows() {
    // given
    final SearchQueryResult<SequenceFlowEntity> result =
        SearchQueryResult.of(
            r ->
                r.items(
                    List.of(
                        new SequenceFlowEntity(
                            "pi1_sequenceFlow1", "node1", 1L, 1L, "pd1", "<default>"),
                        new SequenceFlowEntity(
                            "pi1_sequenceFlow2", "node1", 1L, 1L, "pd1", "<default>"))));
    when(sequenceFlowSearchClient.searchSequenceFlows(any())).thenReturn(result);

    // when
    final var actual = services.sequenceFlows(123L);

    // then
    verify(sequenceFlowSearchClient)
        .searchSequenceFlows(SequenceFlowQuery.of(q -> q.filter(f -> f.processInstanceKey(123L))));
    assertThat(actual).isEqualTo(result.items());
  }

  @Test
  public void shouldReturnProcessInstanceByKey() {
    // given
    final var key = 123L;
    final var entity = mock(ProcessInstanceEntity.class);
    when(entity.processInstanceKey()).thenReturn(key);
    when(entity.processDefinitionId()).thenReturn("processId");
    when(processInstanceSearchClient.getProcessInstance(eq(123L))).thenReturn(entity);

    // when
    final var searchQueryResult = services.getByKey(key);

    // then
    assertThat(searchQueryResult.processInstanceKey()).isEqualTo(key);
  }

  @Test
  public void getByKeyShouldThrowForbiddenExceptionIfNotAuthorized() {
    // given
    final var entity = mock(ProcessInstanceEntity.class);
    when(entity.processDefinitionId()).thenReturn("processId");
    when(processInstanceSearchClient.getProcessInstance(any(Long.class)))
        .thenThrow(
            new ResourceAccessDeniedException(Authorizations.PROCESS_INSTANCE_READ_AUTHORIZATION));
    // when
    final ThrowingCallable executeGetByKey = () -> services.getByKey(1L);
    // then
    final var exception =
        (ServiceException)
            assertThatThrownBy(executeGetByKey).isInstanceOf(ServiceException.class).actual();
    assertThat(exception.getMessage())
        .isEqualTo(
            "Unauthorized to perform operation 'READ_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION'");
    assertThat(exception.getStatus()).isEqualTo(Status.FORBIDDEN);
  }

  @Test
  void shouldCancelProcessInstanceBatchOperationWithResult() {
    // given
    final var filter =
        FilterBuilders.processInstance(b -> b.processDefinitionIds("test-process-definition-id"));

    final long batchOperationKey = 123L;
    final var record = new BatchOperationCreationRecord();
    record.setBatchOperationKey(batchOperationKey);
    record.setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE);

    final var captor = ArgumentCaptor.forClass(BrokerCreateBatchOperationRequest.class);
    when(brokerClient.sendRequest(captor.capture()))
        .thenReturn(CompletableFuture.completedFuture(new BrokerResponse<>(record)));

    // when
    final var result = services.cancelProcessInstanceBatchOperationWithResult(filter).join();

    // then
    assertThat(result.getBatchOperationKey()).isEqualTo(batchOperationKey);
    assertThat(result.getBatchOperationType())
        .isEqualTo(BatchOperationType.CANCEL_PROCESS_INSTANCE);

    // and our request got enriched
    final var enrichedRecord = captor.getValue().getRequestWriter();

    assertThat(
            MsgPackConverter.convertToObject(
                enrichedRecord.getAuthenticationBuffer(), CamundaAuthentication.class))
        .isEqualTo(authentication);
  }

  @Test
  public void shouldReturnOrderedProcessHierarchy() {
    // given
    final var childProcess = mock(ProcessInstanceEntity.class);
    when(childProcess.processInstanceKey()).thenReturn(789L);
    when(childProcess.treePath()).thenReturn("PI_123/FN_A/FNI_456/PI_789/FN_B/FNI_654");
    when(childProcess.processDefinitionId()).thenReturn("child_process_id");

    final var parentProcess = mock(ProcessInstanceEntity.class);
    when(parentProcess.processInstanceKey()).thenReturn(123L);
    when(parentProcess.processDefinitionId()).thenReturn("parent_process_id");

    when(processInstanceSearchClient.getProcessInstance(eq(789L))).thenReturn(childProcess);
    when(processInstanceSearchClient.searchProcessInstances(any()))
        .thenReturn(
            new SearchQueryResult<>(1, false, List.of(childProcess, parentProcess), null, null));

    // when
    final var result = services.callHierarchy(childProcess.processInstanceKey());

    // then
    assertThat(result).hasSize(2);
    assertThat(result.get(0).processInstanceKey()).isEqualTo(123L); // Parent comes first
    assertThat(result.get(1).processInstanceKey()).isEqualTo(789L); // Child comes next
  }

  @Test
  public void shouldReturnEmptyListForBlankTreePath() {
    // given
    final var rootInstance = mock(ProcessInstanceEntity.class);
    when(rootInstance.processInstanceKey()).thenReturn(123L);
    when(rootInstance.processDefinitionId()).thenReturn("root_process_id");
    when(rootInstance.treePath()).thenReturn(null); // No treePath

    when(processInstanceSearchClient.getProcessInstance(eq(123L))).thenReturn(rootInstance);

    // when
    final var result = services.callHierarchy(123L);

    // then
    assertThat(result).isEmpty(); // No hierarchy should return an empty list
  }

  @Test
  public void shouldReturnEmptyListForRootTreePath() {
    // given
    final var rootInstance = mock(ProcessInstanceEntity.class);
    when(rootInstance.processInstanceKey()).thenReturn(123L);
    when(rootInstance.processDefinitionId()).thenReturn("root_process_id");
    when(rootInstance.treePath()).thenReturn("PI_123"); // Root treePath

    when(processInstanceSearchClient.getProcessInstance(eq(123L))).thenReturn(rootInstance);

    // when
    final var result = services.callHierarchy(123L);

    // then
    assertThat(result).isEmpty(); // No hierarchy should return an empty list
  }

  @Test
  void shouldResolveIncidentBatchOperationWithResult() {
    // given
    final var filter =
        FilterBuilders.processInstance(b -> b.processDefinitionIds("test-process-definition-id"));

    final long batchOperationKey = 123L;
    final var record = new BatchOperationCreationRecord();
    record.setBatchOperationKey(batchOperationKey);
    record.setBatchOperationType(BatchOperationType.RESOLVE_INCIDENT);

    final var captor = ArgumentCaptor.forClass(BrokerCreateBatchOperationRequest.class);
    when(brokerClient.sendRequest(captor.capture()))
        .thenReturn(CompletableFuture.completedFuture(new BrokerResponse<>(record)));

    // when
    final var result = services.resolveIncidentsBatchOperationWithResult(filter).join();

    // then
    assertThat(result.getBatchOperationKey()).isEqualTo(batchOperationKey);
    assertThat(result.getBatchOperationType()).isEqualTo(BatchOperationType.RESOLVE_INCIDENT);

    // and our request got enriched
    final var enrichedRecord = captor.getValue().getRequestWriter();

    assertThat(
            MsgPackConverter.convertToObject(
                enrichedRecord.getAuthenticationBuffer(), CamundaAuthentication.class))
        .isEqualTo(authentication);
  }

  @Test
  void shouldMigrateProcessInstanceBatchOperationWithResult() {
    // given
    final var filter =
        FilterBuilders.processInstance(b -> b.processDefinitionIds("test-process-definition-id"));

    final long batchOperationKey = 123L;
    final var record = new BatchOperationCreationRecord();
    record.setBatchOperationKey(batchOperationKey);
    record.setBatchOperationType(BatchOperationType.MIGRATE_PROCESS_INSTANCE);

    final var captor = ArgumentCaptor.forClass(BrokerCreateBatchOperationRequest.class);
    when(brokerClient.sendRequest(captor.capture()))
        .thenReturn(CompletableFuture.completedFuture(new BrokerResponse<>(record)));

    final var request =
        new ProcessInstanceMigrateBatchOperationRequest(
            filter,
            42L,
            List.of(
                new ProcessInstanceMigrationMappingInstruction()
                    .setSourceElementId("source1")
                    .setTargetElementId("target1")));

    // when
    final var result = services.migrateProcessInstancesBatchOperation(request).join();

    // then
    assertThat(result.getBatchOperationKey()).isEqualTo(batchOperationKey);
    assertThat(result.getBatchOperationType())
        .isEqualTo(BatchOperationType.MIGRATE_PROCESS_INSTANCE);

    // and our request got enriched
    final var enrichedRecord = captor.getValue().getRequestWriter();

    assertThat(
            MsgPackConverter.convertToObject(
                enrichedRecord.getAuthenticationBuffer(), CamundaAuthentication.class))
        .isEqualTo(authentication);

    final var modificationPlan = enrichedRecord.getMigrationPlan();
    assertThat(modificationPlan.getTargetProcessDefinitionKey()).isEqualTo(42L);
    assertThat(modificationPlan.getMappingInstructions().getFirst().getSourceElementId())
        .isEqualTo("source1");
    assertThat(modificationPlan.getMappingInstructions().getFirst().getTargetElementId())
        .isEqualTo("target1");
  }

  @Test
  void shouldModifyProcessInstanceBatchOperationWithResult() {
    // given
    final var filter =
        FilterBuilders.processInstance(b -> b.processDefinitionIds("test-process-definition-id"));

    final long batchOperationKey = 123L;
    final var record = new BatchOperationCreationRecord();
    record.setBatchOperationKey(batchOperationKey);
    record.setBatchOperationType(BatchOperationType.MODIFY_PROCESS_INSTANCE);

    final var captor = ArgumentCaptor.forClass(BrokerCreateBatchOperationRequest.class);
    when(brokerClient.sendRequest(captor.capture()))
        .thenReturn(CompletableFuture.completedFuture(new BrokerResponse<>(record)));

    final var request =
        new ProcessInstanceModifyBatchOperationRequest(
            filter,
            List.of(
                new BatchOperationProcessInstanceModificationMoveInstruction()
                    .setSourceElementId("source1")
                    .setTargetElementId("target1")));

    // when
    final var result = services.modifyProcessInstancesBatchOperation(request).join();

    // then
    assertThat(result.getBatchOperationKey()).isEqualTo(batchOperationKey);
    assertThat(result.getBatchOperationType())
        .isEqualTo(BatchOperationType.MODIFY_PROCESS_INSTANCE);

    // and our request got enriched
    final var enrichedRecord = captor.getValue().getRequestWriter();

    assertThat(
            MsgPackConverter.convertToObject(
                enrichedRecord.getAuthenticationBuffer(), CamundaAuthentication.class))
        .isEqualTo(authentication);

    final var filterBuffer = enrichedRecord.getEntityFilterBuffer();
    final var enhancedFilter =
        MsgPackConverter.convertToObject(filterBuffer, ProcessInstanceFilter.class);
    assertThat(enhancedFilter.stateOperations())
        .containsExactly(Operation.eq(ProcessInstanceState.ACTIVE.name()));

    final var modificationPlan = captor.getValue().getRequestWriter().getModificationPlan();
    assertThat(modificationPlan.getMoveInstructions()).hasSize(1);
    assertThat(modificationPlan.getMoveInstructions().getFirst().getSourceElementId())
        .isEqualTo("source1");
    assertThat(modificationPlan.getMoveInstructions().getFirst().getTargetElementId())
        .isEqualTo("target1");
  }

  @Test
  void shouldReturnIncidentsForProcessInstanceKey() {
    // given
    final var processInstanceKey = 123L;
    final var query = SearchQueryBuilders.incidentSearchQuery().build();
    final SearchQueryResult<IncidentEntity> queryResult = mock(SearchQueryResult.class);

    final var processInstance = mock(ProcessInstanceEntity.class);
    when(processInstance.treePath()).thenReturn("PI_123/FN_A/FNI_456/PI_789/FN_B/FNI_654");
    when(processInstance.processInstanceKey()).thenReturn(processInstanceKey);
    when(processInstance.processDefinitionId()).thenReturn("processId");
    when(processInstanceSearchClient.getProcessInstance(eq(processInstanceKey)))
        .thenReturn(processInstance);

    when(incidentServices.search(any(IncidentQuery.class))).thenReturn(queryResult);

    // when
    final var result = services.searchIncidents(processInstanceKey, query);

    // then
    assertThat(result).isEqualTo(queryResult);

    final var incidentQueryCaptor = ArgumentCaptor.forClass(IncidentQuery.class);
    verify(incidentServices).search(incidentQueryCaptor.capture());
    final var incidentQuery = incidentQueryCaptor.getValue();
    assertThat(incidentQuery.filter().treePathOperations())
        .containsExactly(Operation.like("*" + processInstance.treePath() + "*"));
    assertThat(incidentQuery.page()).isEqualTo(query.page());
    assertThat(incidentQuery.sort()).isEqualTo(query.sort());
  }

  @Test
  void incidentsShouldThrowForbiddenExceptionIfNotAuthorizedToReadProcessInstance() {
    // given
    final var processInstanceKey = 123L;

    final var processInstance = mock(ProcessInstanceEntity.class);
    when(processInstance.processDefinitionId()).thenReturn("processId");
    when(processInstanceSearchClient.getProcessInstance(eq(processInstanceKey)))
        .thenThrow(
            new ResourceAccessDeniedException(
                Authorization.of(a -> a.processDefinition().readProcessInstance())));

    final var query = new IncidentQuery.Builder().build();

    // when
    final ThrowingCallable executeGetByKey =
        () -> services.searchIncidents(processInstanceKey, query);

    // then
    final var exception =
        (ServiceException)
            assertThatThrownBy(executeGetByKey).isInstanceOf(ServiceException.class).actual();
    assertThat(exception.getMessage())
        .isEqualTo(
            "Unauthorized to perform operation 'READ_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION'");
    assertThat(exception.getStatus()).isEqualTo(Status.FORBIDDEN);
    verifyNoInteractions(incidentServices);
  }
}
