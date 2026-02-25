/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.search.exception.ErrorMessages.ERROR_ENTITY_BY_KEY_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;
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
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.CamundaSearchException.Reason;
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
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
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
import io.camunda.zeebe.gateway.impl.broker.request.BrokerDeleteHistoryRequest;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.history.HistoryDeletionRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationMappingInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationMoveInstruction;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.instancio.Instancio;
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
  private Authorization<BatchOperationCreationRecord> authorizationCheck;
  private BrokerClient brokerClient;
  private ApiServicesExecutorProvider executorProvider;
  private BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter;

  @BeforeEach
  public void before() {
    processInstanceSearchClient = mock(ProcessInstanceSearchClient.class);
    sequenceFlowSearchClient = mock(SequenceFlowSearchClient.class);
    authentication = CamundaAuthentication.none();
    authorizationCheck =
        Authorization.withAuthorization(
            Authorization.of(a -> a.processDefinition().updateProcessInstance()), "myProcess");
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
    brokerRequestAuthorizationConverter = mock(BrokerRequestAuthorizationConverter.class);
    services =
        new ProcessInstanceServices(
            brokerClient,
            securityContextProvider,
            processInstanceSearchClient,
            sequenceFlowSearchClient,
            incidentServices,
            authentication,
            executorProvider,
            brokerRequestAuthorizationConverter);
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
                            "pi1_sequenceFlow1", "node1", 1L, 37L, 1L, "pd1", "<default>"),
                        new SequenceFlowEntity(
                            "pi1_sequenceFlow2", "node1", 1L, 37L, 1L, "pd1", "<default>"))));
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
    final var entity =
        Instancio.of(ProcessInstanceEntity.class)
            .set(field(ProcessInstanceEntity::processInstanceKey), key)
            .create();
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
  void shouldResolveProcessInstanceIncidentsWithResult() {
    // given
    final long batchOperationKey = 123L;
    final var record = new BatchOperationCreationRecord();
    record.setBatchOperationKey(batchOperationKey);
    record.setBatchOperationType(BatchOperationType.RESOLVE_INCIDENT);

    final var entity = mock(ProcessInstanceEntity.class);
    when(entity.processDefinitionId()).thenReturn("myProcess");
    when(processInstanceSearchClient.getProcessInstance(any(Long.class))).thenReturn(entity);

    final var captor = ArgumentCaptor.forClass(BrokerCreateBatchOperationRequest.class);
    when(brokerClient.sendRequest(captor.capture()))
        .thenReturn(CompletableFuture.completedFuture(new BrokerResponse<>(record)));

    // when
    final var result = services.resolveProcessInstanceIncidents(345L).join();

    // then
    assertThat(result.getBatchOperationKey()).isEqualTo(batchOperationKey);
    assertThat(result.getBatchOperationType()).isEqualTo(BatchOperationType.RESOLVE_INCIDENT);

    // and our request got enriched
    final var enrichedRecord = captor.getValue().getRequestWriter();

    assertThat(
            MsgPackConverter.convertToObject(
                enrichedRecord.getAuthenticationBuffer(), CamundaAuthentication.class))
        .isEqualTo(authentication);

    assertThat(
            MsgPackConverter.convertToObject(
                enrichedRecord.getAuthorizationCheckBuffer(), Authorization.class))
        .isEqualTo(authorizationCheck);
  }

  @Test
  public void shouldReturnOrderedProcessHierarchy() {
    // given
    final var childProcess =
        Instancio.of(ProcessInstanceEntity.class)
            .set(field(ProcessInstanceEntity::processInstanceKey), 789L)
            .set(field(ProcessInstanceEntity::treePath), "PI_123/FN_A/FNI_456/PI_789/FN_B/FNI_654")
            .create();

    final var parentProcess =
        Instancio.of(ProcessInstanceEntity.class)
            .set(field(ProcessInstanceEntity::processInstanceKey), 123L)
            .create();

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
    final var rootInstance =
        Instancio.of(ProcessInstanceEntity.class)
            .set(field(ProcessInstanceEntity::processInstanceKey), 123L)
            .set(field(ProcessInstanceEntity::treePath), null)
            .create();

    when(processInstanceSearchClient.getProcessInstance(eq(123L))).thenReturn(rootInstance);

    // when
    final var result = services.callHierarchy(123L);

    // then
    assertThat(result).isEmpty(); // No hierarchy should return an empty list
  }

  @Test
  public void shouldReturnEmptyListForRootTreePath() {
    // given
    final var rootInstance =
        Instancio.of(ProcessInstanceEntity.class)
            .set(field(ProcessInstanceEntity::processInstanceKey), 123L)
            .set(field(ProcessInstanceEntity::treePath), "PI_123")
            .create();

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
                new ProcessInstanceModificationMoveInstruction()
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

  @Test
  void shouldDeleteProcessInstanceBatchOperationWithResult() {
    // given
    final var filter =
        FilterBuilders.processInstance(b -> b.processDefinitionIds("test-process-definition-id"));

    final long batchOperationKey = 123L;
    final var record = new BatchOperationCreationRecord();
    record.setBatchOperationKey(batchOperationKey);
    record.setBatchOperationType(BatchOperationType.DELETE_PROCESS_INSTANCE);

    final var captor = ArgumentCaptor.forClass(BrokerCreateBatchOperationRequest.class);
    when(brokerClient.sendRequest(captor.capture()))
        .thenReturn(CompletableFuture.completedFuture(new BrokerResponse<>(record)));

    // when
    final var result = services.deleteProcessInstancesBatchOperation(filter).join();

    // then
    assertThat(result.getBatchOperationKey()).isEqualTo(batchOperationKey);
    assertThat(result.getBatchOperationType())
        .isEqualTo(BatchOperationType.DELETE_PROCESS_INSTANCE);

    // and our request got enriched
    final var enrichedRecord = captor.getValue().getRequestWriter();

    assertThat(
            MsgPackConverter.convertToObject(
                enrichedRecord.getAuthenticationBuffer(), CamundaAuthentication.class))
        .isEqualTo(authentication);
  }

  @Test
  void shouldDeleteProcessInstanceWithResult() {
    // given
    final long processInstanceKey = 123L;
    final var processId = "processId";
    final var tenantId = "tenantId";

    final var entity = mock(ProcessInstanceEntity.class);
    when(entity.processInstanceKey()).thenReturn(processInstanceKey);
    when(entity.processDefinitionId()).thenReturn(processId);
    when(entity.tenantId()).thenReturn(tenantId);
    when(processInstanceSearchClient.getProcessInstance(eq(processInstanceKey))).thenReturn(entity);

    final var record =
        new HistoryDeletionRecord()
            .setResourceKey(processInstanceKey)
            .setResourceType(HistoryDeletionType.PROCESS_INSTANCE)
            .setProcessId(processId)
            .setTenantId(tenantId);
    final var captor = ArgumentCaptor.forClass(BrokerDeleteHistoryRequest.class);
    when(brokerClient.sendRequest(captor.capture()))
        .thenReturn(CompletableFuture.completedFuture(new BrokerResponse<>(record)));

    // when
    services.deleteProcessInstance(processInstanceKey, null).join();

    // then
    final var brokerRequest = (HistoryDeletionRecord) captor.getValue().getRequestWriter();
    assertThat(brokerRequest.getResourceKey()).isEqualTo(processInstanceKey);
    assertThat(brokerRequest.getResourceType()).isEqualTo(HistoryDeletionType.PROCESS_INSTANCE);
    assertThat(brokerRequest.getProcessId()).isEqualTo(processId);
    assertThat(brokerRequest.getTenantId()).isEqualTo(tenantId);
  }

  @Test
  void shouldNotDeleteProcessInstanceWithResult() {
    // given
    final long processInstanceKey = 123L;

    when(processInstanceSearchClient.getProcessInstance(eq(processInstanceKey)))
        .thenThrow(
            new CamundaSearchException(
                ERROR_ENTITY_BY_KEY_NOT_FOUND.formatted("Process Instance", processInstanceKey),
                Reason.NOT_FOUND));

    // when/then
    assertThatThrownBy(() -> services.deleteProcessInstance(processInstanceKey, null).join())
        .isInstanceOf(ServiceException.class)
        .hasMessage("Process Instance with key '123' not found");
  }

  @Test
  void shouldCreateProcessInstanceWithResultUsingCustomRequestTimeout() {
    // given
    final var request =
        new ProcessInstanceServices.ProcessInstanceCreateRequest(
            123L, // processDefinitionKey
            "", // bpmnProcessId
            -1, // version
            null, // variables
            "<default>", // tenantId
            true, // awaitCompletion
            600000L, // requestTimeout (10 minutes)
            null, // operationReference
            List.of(), // startInstructions
            List.of(), // runtimeInstructions
            List.of(), // fetchVariables
            null, // tags
            null // businessId
            );

    final var mockResponse =
        new io.camunda.zeebe.protocol.impl.record.value.processinstance
            .ProcessInstanceResultRecord();
    when(brokerClient.sendRequest(any(), any(java.time.Duration.class)))
        .thenReturn(CompletableFuture.completedFuture(new BrokerResponse<>(mockResponse)));

    // when
    services.createProcessInstanceWithResult(request).join();

    // then
    verify(brokerClient)
        .sendRequest(
            any(
                io.camunda.zeebe.gateway.impl.broker.request
                    .BrokerCreateProcessInstanceWithResultRequest.class),
            eq(java.time.Duration.ofMillis(600000L)));
  }

  @Test
  void shouldCreateProcessInstanceWithResultUsingDefaultTimeoutWhenNotProvided() {
    // given
    final var request =
        new ProcessInstanceServices.ProcessInstanceCreateRequest(
            123L, // processDefinitionKey
            "", // bpmnProcessId
            -1, // version
            null, // variables
            "<default>", // tenantId
            true, // awaitCompletion
            null, // requestTimeout (not provided)
            null, // operationReference
            List.of(), // startInstructions
            List.of(), // runtimeInstructions
            List.of(), // fetchVariables
            null, // tags
            null // businessId
            );

    final var mockResponse =
        new io.camunda.zeebe.protocol.impl.record.value.processinstance
            .ProcessInstanceResultRecord();
    when(brokerClient.sendRequest(any()))
        .thenReturn(CompletableFuture.completedFuture(new BrokerResponse<>(mockResponse)));

    // when
    services.createProcessInstanceWithResult(request).join();

    // then
    verify(brokerClient)
        .sendRequest(
            any(
                io.camunda.zeebe.gateway.impl.broker.request
                    .BrokerCreateProcessInstanceWithResultRequest.class));
    verify(brokerClient, org.mockito.Mockito.never())
        .sendRequest(any(), any(java.time.Duration.class));
  }

  @Test
  void shouldCreateProcessInstanceWithResultUsingDefaultTimeoutWhenRequestTimeoutIsZero() {
    // given
    final var request =
        new ProcessInstanceServices.ProcessInstanceCreateRequest(
            123L, // processDefinitionKey
            "", // bpmnProcessId
            -1, // version
            null, // variables
            "<default>", // tenantId
            true, // awaitCompletion
            0L, // requestTimeout explicitly set to zero
            null, // operationReference
            List.of(), // startInstructions
            List.of(), // runtimeInstructions
            List.of(), // fetchVariables
            null, // tags
            null // businessId
            );

    final var mockResponse =
        new io.camunda.zeebe.protocol.impl.record.value.processinstance
            .ProcessInstanceResultRecord();
    when(brokerClient.sendRequest(any()))
        .thenReturn(CompletableFuture.completedFuture(new BrokerResponse<>(mockResponse)));

    // when
    services.createProcessInstanceWithResult(request).join();

    // then
    verify(brokerClient)
        .sendRequest(
            any(
                io.camunda.zeebe.gateway.impl.broker.request
                    .BrokerCreateProcessInstanceWithResultRequest.class));
    verify(brokerClient, org.mockito.Mockito.never())
        .sendRequest(any(), any(java.time.Duration.class));
  }
}
