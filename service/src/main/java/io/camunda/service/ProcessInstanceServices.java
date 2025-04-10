/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.search.query.SearchQueryBuilders.processInstanceSearchQuery;

import io.camunda.search.clients.ProcessInstanceSearchClient;
import io.camunda.search.entities.ProcessFlowNodeStatisticsEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.Authorization;
import io.camunda.service.exception.ForbiddenException;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCancelProcessInstanceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateBatchOperationRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateProcessInstanceWithResultRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerMigrateProcessInstanceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerModifyProcessInstanceRequest;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
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
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class ProcessInstanceServices
    extends SearchQueryService<
        ProcessInstanceServices, ProcessInstanceQuery, ProcessInstanceEntity> {

  public static final long NO_PARENT_EXISTS_KEY = -1L;

  private final ProcessInstanceSearchClient processInstanceSearchClient;

  public ProcessInstanceServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ProcessInstanceSearchClient processInstanceSearchClient,
      final Authentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
    this.processInstanceSearchClient = processInstanceSearchClient;
  }

  @Override
  public ProcessInstanceServices withAuthentication(final Authentication authentication) {
    return new ProcessInstanceServices(
        brokerClient, securityContextProvider, processInstanceSearchClient, authentication);
  }

  @Override
  public SearchQueryResult<ProcessInstanceEntity> search(final ProcessInstanceQuery query) {
    return processInstanceSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication, Authorization.of(a -> a.processDefinition().readProcessInstance())))
        .searchProcessInstances(query);
  }

  public SearchQueryResult<ProcessInstanceEntity> search(
      final Function<ProcessInstanceQuery.Builder, ObjectBuilder<ProcessInstanceQuery>> fn) {
    return search(processInstanceSearchQuery(fn));
  }

  public List<ProcessFlowNodeStatisticsEntity> flowNodeStatistics(final long processInstanceKey) {
    return processInstanceSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication, Authorization.of(a -> a.processDefinition().readProcessInstance())))
        .processInstanceFlowNodeStatistics(processInstanceKey);
  }

  public ProcessInstanceEntity getByKey(final Long processInstanceKey) {
    final var result =
        processInstanceSearchClient
            .withSecurityContext(securityContextProvider.provideSecurityContext(authentication))
            .searchProcessInstances(
                processInstanceSearchQuery(
                    q -> q.filter(f -> f.processInstanceKeys(processInstanceKey))));
    final var processInstanceEntity =
        getSingleResultOrThrow(result, processInstanceKey, "Process instance");
    final var authorization = Authorization.of(a -> a.processDefinition().readProcessInstance());
    if (!securityContextProvider.isAuthorized(
        processInstanceEntity.processDefinitionId(), authentication, authorization)) {
      throw new ForbiddenException(authorization);
    }
    return processInstanceEntity;
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
            .setInstructions(request.startInstructions());

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
    final var rootInstanceFilter =
        filter.toBuilder()
            // It is only possible to cancel root processes in zeebe,
            // whereby zeebe then automatically cancels the sub-processes.
            .parentProcessInstanceKeys(NO_PARENT_EXISTS_KEY)
            .states(ProcessInstanceState.ACTIVE.name())
            .build();

    final var brokerRequest =
        new BrokerCreateBatchOperationRequest()
            .setFilter(rootInstanceFilter)
            .setBatchOperationType(BatchOperationType.PROCESS_CANCELLATION);

    return sendBrokerRequest(brokerRequest);
  }

  public CompletableFuture<BatchOperationCreationRecord> resolveIncidentsBatchOperationWithResult(
      final ProcessInstanceFilter filter) {
    final var brokerRequest =
        new BrokerCreateBatchOperationRequest()
            .setFilter(filter)
            .setBatchOperationType(BatchOperationType.RESOLVE_INCIDENT);

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
}
