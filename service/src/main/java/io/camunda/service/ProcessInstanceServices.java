/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.search.query.SearchQueryBuilders.processInstanceSearchQuery;
import static io.camunda.security.auth.Authorization.withAuthorization;
import static io.camunda.service.authorization.Authorizations.PROCESS_INSTANCE_READ_AUTHORIZATION;

import io.camunda.search.clients.ProcessInstanceSearchClient;
import io.camunda.search.clients.SequenceFlowSearchClient;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.ProcessFlowNodeStatisticsEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.entities.SequenceFlowEntity;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SequenceFlowQuery;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.service.util.TreePathParser;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCancelProcessInstanceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateBatchOperationRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateProcessInstanceWithResultRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerMigrateProcessInstanceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerModifyProcessInstanceRequest;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationProcessInstanceMigrationPlan;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationProcessInstanceModificationMoveInstruction;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationProcessInstanceModificationPlan;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRuntimeInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationStartInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationMappingInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationActivateInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationTerminateInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceResultRecord;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ProcessInstanceServices
    extends SearchQueryService<
        ProcessInstanceServices, ProcessInstanceQuery, ProcessInstanceEntity> {

  private final ProcessInstanceSearchClient processInstanceSearchClient;
  private final SequenceFlowSearchClient sequenceFlowSearchClient;
  private final IncidentServices incidentServices;

  public ProcessInstanceServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ProcessInstanceSearchClient processInstanceSearchClient,
      final SequenceFlowSearchClient sequenceFlowSearchClient,
      final IncidentServices incidentServices,
      final CamundaAuthentication authentication,
      final ApiServicesExecutorProvider executorProvider) {
    super(brokerClient, securityContextProvider, authentication, executorProvider);
    this.processInstanceSearchClient = processInstanceSearchClient;
    this.sequenceFlowSearchClient = sequenceFlowSearchClient;
    this.incidentServices = incidentServices;
  }

  @Override
  public ProcessInstanceServices withAuthentication(final CamundaAuthentication authentication) {
    return new ProcessInstanceServices(
        brokerClient,
        securityContextProvider,
        processInstanceSearchClient,
        sequenceFlowSearchClient,
        incidentServices,
        authentication,
        executorProvider);
  }

  @Override
  public SearchQueryResult<ProcessInstanceEntity> search(final ProcessInstanceQuery query) {
    return executeSearchRequest(
        () ->
            processInstanceSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, PROCESS_INSTANCE_READ_AUTHORIZATION))
                .searchProcessInstances(query));
  }

  public SearchQueryResult<ProcessInstanceEntity> search(
      final Function<ProcessInstanceQuery.Builder, ObjectBuilder<ProcessInstanceQuery>> fn) {
    return search(processInstanceSearchQuery(fn));
  }

  public List<ProcessFlowNodeStatisticsEntity> elementStatistics(final long processInstanceKey) {
    return executeSearchRequest(
        () ->
            processInstanceSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, PROCESS_INSTANCE_READ_AUTHORIZATION))
                .processInstanceFlowNodeStatistics(processInstanceKey));
  }

  public List<ProcessInstanceEntity> callHierarchy(final long processInstanceKey) {
    final var rootInstance = getByKey(processInstanceKey);

    final var treePath = rootInstance.treePath();
    if (treePath == null || treePath.isBlank()) {
      return List.of();
    }

    final List<Long> orderedKeys =
        TreePathParser.extractProcessInstanceKeys(rootInstance.treePath()).stream().toList();

    if (orderedKeys.size() == 1
        && orderedKeys.getFirst().equals(rootInstance.processInstanceKey())) {
      return List.of();
    }

    final var resultsByKey =
        executeSearchRequest(
                () ->
                    processInstanceSearchClient
                        .withSecurityContext(
                            securityContextProvider.provideSecurityContext(
                                CamundaAuthentication.anonymous()))
                        .searchProcessInstances(
                            processInstanceSearchQuery(
                                q ->
                                    q.filter(
                                        f ->
                                            f.processInstanceKeyOperations(
                                                Operation.in(orderedKeys))))))
            .items()
            .stream()
            .collect(
                Collectors.toMap(ProcessInstanceEntity::processInstanceKey, Function.identity()));

    return orderedKeys.stream().map(resultsByKey::get).filter(Objects::nonNull).toList();
  }

  public List<SequenceFlowEntity> sequenceFlows(final long processInstanceKey) {
    return executeSearchRequest(
            () ->
                sequenceFlowSearchClient
                    .withSecurityContext(
                        securityContextProvider.provideSecurityContext(
                            authentication, PROCESS_INSTANCE_READ_AUTHORIZATION))
                    .searchSequenceFlows(
                        SequenceFlowQuery.of(
                            b ->
                                b.filter(f -> f.processInstanceKey(processInstanceKey))
                                    .unlimited())))
        .items();
  }

  public ProcessInstanceEntity getByKey(final Long processInstanceKey) {
    return executeSearchRequest(
        () ->
            processInstanceSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication,
                        withAuthorization(
                            PROCESS_INSTANCE_READ_AUTHORIZATION,
                            ProcessInstanceEntity::processDefinitionId)))
                .getProcessInstance(processInstanceKey));
  }

  public CompletableFuture<ProcessInstanceCreationRecord> createProcessInstance(
      final ProcessInstanceCreateRequest request) {
    final var brokerRequest =
        new BrokerCreateProcessInstanceRequest()
            .setBpmnProcessId(request.bpmnProcessId())
            .setKey(request.processDefinitionKey())
            .setVersion(request.version())
            .setTenantId(request.tenantId())
            .setVariables(getDocumentOrEmpty(request.variables()))
            .setStartInstructionsFromRecord(request.startInstructions())
            .setRuntimeInstructionsFromRecord(request.runtimeInstructions());

    if (request.operationReference() != null) {
      brokerRequest.setOperationReference(request.operationReference());
    }
    return sendBrokerRequest(brokerRequest);
  }

  public CompletableFuture<ProcessInstanceResultRecord> createProcessInstanceWithResult(
      final ProcessInstanceCreateRequest request) {
    final var brokerRequest =
        new BrokerCreateProcessInstanceWithResultRequest()
            .setBpmnProcessId(request.bpmnProcessId())
            .setKey(request.processDefinitionKey())
            .setVersion(request.version())
            .setTenantId(request.tenantId())
            .setVariables(getDocumentOrEmpty(request.variables()))
            .setInstructions(request.startInstructions())
            .setFetchVariables(request.fetchVariables());

    if (request.operationReference() != null) {
      brokerRequest.setOperationReference(request.operationReference());
    }
    return sendBrokerRequest(brokerRequest);
  }

  public CompletableFuture<ProcessInstanceRecord> cancelProcessInstance(
      final ProcessInstanceCancelRequest request) {
    final var brokerRequest =
        new BrokerCancelProcessInstanceRequest()
            .setProcessInstanceKey(request.processInstanceKey());

    if (request.operationReference() != null) {
      brokerRequest.setOperationReference(request.operationReference());
    }
    return sendBrokerRequest(brokerRequest);
  }

  public CompletableFuture<BatchOperationCreationRecord>
      cancelProcessInstanceBatchOperationWithResult(final ProcessInstanceFilter filter) {
    final var brokerRequest =
        new BrokerCreateBatchOperationRequest()
            .setFilter(filter)
            .setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE)
            .setAuthentication(authentication);

    return sendBrokerRequest(brokerRequest);
  }

  public CompletableFuture<BatchOperationCreationRecord> resolveIncidentsBatchOperationWithResult(
      final ProcessInstanceFilter filter) {
    final var brokerRequest =
        new BrokerCreateBatchOperationRequest()
            .setFilter(filter)
            .setBatchOperationType(BatchOperationType.RESOLVE_INCIDENT)
            .setAuthentication(authentication);

    return sendBrokerRequest(brokerRequest);
  }

  public CompletableFuture<BatchOperationCreationRecord> migrateProcessInstancesBatchOperation(
      final ProcessInstanceMigrateBatchOperationRequest request) {
    final var migrationPlan = new BatchOperationProcessInstanceMigrationPlan();
    migrationPlan.setTargetProcessDefinitionKey(request.targetProcessDefinitionKey);
    request.mappingInstructions.forEach(migrationPlan::addMappingInstruction);

    final var brokerRequest =
        new BrokerCreateBatchOperationRequest()
            .setFilter(request.filter)
            .setMigrationPlan(migrationPlan)
            .setBatchOperationType(BatchOperationType.MIGRATE_PROCESS_INSTANCE)
            .setAuthentication(authentication);

    return sendBrokerRequest(brokerRequest);
  }

  public CompletableFuture<ProcessInstanceMigrationRecord> migrateProcessInstance(
      final ProcessInstanceMigrateRequest request) {
    final var brokerRequest =
        new BrokerMigrateProcessInstanceRequest()
            .setProcessInstanceKey(request.processInstanceKey())
            .setTargetProcessDefinitionKey(request.targetProcessDefinitionKey())
            .setMappingInstructions(request.mappingInstructions());

    if (request.operationReference() != null) {
      brokerRequest.setOperationReference(request.operationReference());
    }
    return sendBrokerRequest(brokerRequest);
  }

  public CompletableFuture<ProcessInstanceModificationRecord> modifyProcessInstance(
      final ProcessInstanceModifyRequest request) {
    final var brokerRequest =
        new BrokerModifyProcessInstanceRequest()
            .setProcessInstanceKey(request.processInstanceKey())
            .addActivationInstructions(request.activateInstructions())
            .addTerminationInstructions(request.terminateInstructions());

    if (request.operationReference() != null) {
      brokerRequest.setOperationReference(request.operationReference());
    }
    return sendBrokerRequest(brokerRequest);
  }

  public CompletableFuture<BatchOperationCreationRecord> modifyProcessInstancesBatchOperation(
      final ProcessInstanceModifyBatchOperationRequest request) {
    final var rootInstanceFilter =
        request.filter.toBuilder()
            // It is only possible to modify active processes in zeebe
            .states(ProcessInstanceState.ACTIVE.name())
            .build();
    final var modificationPlan = new BatchOperationProcessInstanceModificationPlan();
    request.moveInstructions().forEach(modificationPlan::addMoveInstruction);

    final var brokerRequest =
        new BrokerCreateBatchOperationRequest()
            .setModificationPlan(modificationPlan)
            .setFilter(rootInstanceFilter)
            .setBatchOperationType(BatchOperationType.MODIFY_PROCESS_INSTANCE)
            .setAuthentication(authentication);

    return sendBrokerRequest(brokerRequest);
  }

  public SearchQueryResult<IncidentEntity> searchIncidents(
      final long processInstanceKey, final IncidentQuery query) {
    final var processInstance = getByKey(processInstanceKey);
    final var treePath = processInstance.treePath();
    return incidentServices
        .withAuthentication(authentication)
        .search(
            IncidentQuery.of(
                b ->
                    b.filter(f -> f.treePathOperations(Operation.like("*" + treePath + "*")))
                        .page(query.page())
                        .sort(query.sort())));
  }

  public record ProcessInstanceCreateRequest(
      Long processDefinitionKey,
      String bpmnProcessId,
      Integer version,
      Map<String, Object> variables,
      String tenantId,
      Boolean awaitCompletion,
      Long requestTimeout,
      Long operationReference,
      List<ProcessInstanceCreationStartInstruction> startInstructions,
      List<ProcessInstanceCreationRuntimeInstruction> runtimeInstructions,
      List<String> fetchVariables) {}

  public record ProcessInstanceCancelRequest(Long processInstanceKey, Long operationReference) {}

  public record ProcessInstanceMigrateRequest(
      Long processInstanceKey,
      Long targetProcessDefinitionKey,
      List<ProcessInstanceMigrationMappingInstruction> mappingInstructions,
      Long operationReference) {}

  public record ProcessInstanceModifyRequest(
      Long processInstanceKey,
      List<ProcessInstanceModificationActivateInstruction> activateInstructions,
      List<ProcessInstanceModificationTerminateInstruction> terminateInstructions,
      Long operationReference) {}

  public record ProcessInstanceMigrateBatchOperationRequest(
      ProcessInstanceFilter filter,
      Long targetProcessDefinitionKey,
      List<ProcessInstanceMigrationMappingInstruction> mappingInstructions) {}

  public record ProcessInstanceModifyBatchOperationRequest(
      ProcessInstanceFilter filter,
      List<BatchOperationProcessInstanceModificationMoveInstruction> moveInstructions) {}
}
