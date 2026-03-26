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
import static io.camunda.service.authorization.Authorizations.PROCESS_INSTANCE_UPDATE_AUTHORIZATION;

import io.camunda.search.clients.ProcessInstanceSearchClient;
import io.camunda.search.clients.SequenceFlowSearchClient;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.ProcessFlowNodeStatisticsEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.entities.SequenceFlowEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SequenceFlowQuery;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.SecurityContext;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.service.util.TreePathParser;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.gateway.impl.broker.RequestRetryHandler;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCancelProcessInstanceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateBatchOperationRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateProcessInstanceWithResultRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerDeleteHistoryRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerMigrateProcessInstanceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerModifyProcessInstanceRequest;
import io.camunda.zeebe.gateway.validation.VariableNameLengthValidator;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationProcessInstanceMigrationPlan;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationProcessInstanceModificationPlan;
import io.camunda.zeebe.protocol.impl.record.value.history.HistoryDeletionRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRuntimeInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationStartInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationMappingInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationActivateInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationMoveInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationTerminateInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceResultRecord;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ProcessInstanceServices
    extends SearchQueryService<
        ProcessInstanceServices, ProcessInstanceQuery, ProcessInstanceEntity> {

  private final ProcessInstanceSearchClient processInstanceSearchClient;
  private final SequenceFlowSearchClient sequenceFlowSearchClient;
  private final IncidentServices incidentServices;
  private final RequestRetryHandler requestRetryHandler;
  private final ExecutorService executor;
  private final int maxVariableNameLength;
  private final ConcurrentHashMap<String, RequestRetryHandler> processIdToRetryHandler =
      new ConcurrentHashMap<>();

  public ProcessInstanceServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ProcessInstanceSearchClient processInstanceSearchClient,
      final SequenceFlowSearchClient sequenceFlowSearchClient,
      final IncidentServices incidentServices,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    this(
        brokerClient,
        securityContextProvider,
        processInstanceSearchClient,
        sequenceFlowSearchClient,
        incidentServices,
        executorProvider,
        brokerRequestAuthorizationConverter,
        new RequestRetryHandler(brokerClient, brokerClient.getTopologyManager()),
        VariableNameLengthValidator.DEFAULT_MAX_NAME_FIELD_LENGTH);
  }

  public ProcessInstanceServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ProcessInstanceSearchClient processInstanceSearchClient,
      final SequenceFlowSearchClient sequenceFlowSearchClient,
      final IncidentServices incidentServices,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter,
      final int maxVariableNameLength) {
    this(
        brokerClient,
        securityContextProvider,
        processInstanceSearchClient,
        sequenceFlowSearchClient,
        incidentServices,
        executorProvider,
        brokerRequestAuthorizationConverter,
        new RequestRetryHandler(brokerClient, brokerClient.getTopologyManager()),
        maxVariableNameLength);
  }

  public ProcessInstanceServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ProcessInstanceSearchClient processInstanceSearchClient,
      final SequenceFlowSearchClient sequenceFlowSearchClient,
      final IncidentServices incidentServices,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter,
      final RequestRetryHandler requestRetryHandler) {
    this(
        brokerClient,
        securityContextProvider,
        processInstanceSearchClient,
        sequenceFlowSearchClient,
        incidentServices,
        executorProvider,
        brokerRequestAuthorizationConverter,
        requestRetryHandler,
        VariableNameLengthValidator.DEFAULT_MAX_NAME_FIELD_LENGTH);
  }

  public ProcessInstanceServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final ProcessInstanceSearchClient processInstanceSearchClient,
      final SequenceFlowSearchClient sequenceFlowSearchClient,
      final IncidentServices incidentServices,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter,
      final RequestRetryHandler requestRetryHandler,
      final int maxVariableNameLength) {
    super(
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.processInstanceSearchClient = processInstanceSearchClient;
    this.sequenceFlowSearchClient = sequenceFlowSearchClient;
    this.incidentServices = incidentServices;
    this.requestRetryHandler = requestRetryHandler;
    executor = executorProvider.getExecutor();
    this.maxVariableNameLength = maxVariableNameLength;
  }

  @Override
  public SearchQueryResult<ProcessInstanceEntity> search(
      final ProcessInstanceQuery query, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            processInstanceSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, PROCESS_INSTANCE_READ_AUTHORIZATION))
                .searchProcessInstances(query));
  }

  public SearchQueryResult<ProcessInstanceEntity> search(
      final Function<ProcessInstanceQuery.Builder, ObjectBuilder<ProcessInstanceQuery>> fn,
      final CamundaAuthentication authentication) {
    return search(processInstanceSearchQuery(fn), authentication);
  }

  public List<ProcessFlowNodeStatisticsEntity> elementStatistics(
      final long processInstanceKey, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            processInstanceSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, PROCESS_INSTANCE_READ_AUTHORIZATION))
                .processInstanceFlowNodeStatistics(processInstanceKey));
  }

  public List<ProcessInstanceEntity> callHierarchy(
      final long processInstanceKey, final CamundaAuthentication authentication) {
    final var rootInstance = getByKey(processInstanceKey, authentication);

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

  public List<SequenceFlowEntity> sequenceFlows(
      final long processInstanceKey, final CamundaAuthentication authentication) {
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

  public ProcessInstanceEntity getByKey(
      final Long processInstanceKey, final CamundaAuthentication authentication) {
    return getByKey(
        processInstanceKey,
        securityContextProvider.provideSecurityContext(
            authentication,
            withAuthorization(
                PROCESS_INSTANCE_READ_AUTHORIZATION, ProcessInstanceEntity::processDefinitionId)));
  }

  private ProcessInstanceEntity getByKey(
      final Long processInstanceKey, final SecurityContext securityContext) {
    return executeSearchRequest(
        () ->
            processInstanceSearchClient
                .withSecurityContext(securityContext)
                .getProcessInstance(processInstanceKey));
  }

  public CompletableFuture<ProcessInstanceCreationRecord> createProcessInstance(
      final ProcessInstanceCreateRequest request, final CamundaAuthentication authentication) {
    final var brokerRequest =
        new BrokerCreateProcessInstanceRequest(maxVariableNameLength)
            .setBpmnProcessId(request.bpmnProcessId())
            .setKey(request.processDefinitionKey())
            .setVersion(request.version())
            .setTenantId(request.tenantId())
            .setVariables(getDocumentOrEmpty(request.variables()))
            .setStartInstructionsFromRecord(request.startInstructions())
            .setRuntimeInstructionsFromRecord(request.runtimeInstructions());

    if (request.tags() != null) {
      brokerRequest.setTags(request.tags());
    }

    if (request.businessId() != null) {
      brokerRequest.setBusinessId(request.businessId());
    }

    if (request.operationReference() != null) {
      brokerRequest.setOperationReference(request.operationReference());
    }
    if (request.businessId() != null) {
      return sendRequestWithRetryPartitions(brokerRequest, authentication);
    }
    return sendWithPerProcessRoundRobin(
        brokerRequest, request.bpmnProcessId(), authentication, null);
  }

  public CompletableFuture<ProcessInstanceResultRecord> createProcessInstanceWithResult(
      final ProcessInstanceCreateRequest request, final CamundaAuthentication authentication) {
    final var brokerRequest =
        new BrokerCreateProcessInstanceWithResultRequest(maxVariableNameLength)
            .setBpmnProcessId(request.bpmnProcessId())
            .setKey(request.processDefinitionKey())
            .setVersion(request.version())
            .setTenantId(request.tenantId())
            .setVariables(getDocumentOrEmpty(request.variables()))
            .setInstructions(request.startInstructions())
            .setFetchVariables(request.fetchVariables())
            .setTags(request.tags());

    if (request.businessId() != null) {
      brokerRequest.setBusinessId(request.businessId());
    }

    if (request.operationReference() != null) {
      brokerRequest.setOperationReference(request.operationReference());
    }

    if (request.businessId() != null) {
      if (request.requestTimeout() != null && request.requestTimeout() > 0) {
        return sendRequestWithRetryPartitions(
            brokerRequest, authentication, Duration.ofMillis(request.requestTimeout()));
      }
      return sendRequestWithRetryPartitions(brokerRequest, authentication);
    }

    final Duration timeout =
        request.requestTimeout() != null && request.requestTimeout() > 0
            ? Duration.ofMillis(request.requestTimeout())
            : null;
    return sendWithPerProcessRoundRobin(
        brokerRequest, request.bpmnProcessId(), authentication, timeout);
  }

  public CompletableFuture<ProcessInstanceRecord> cancelProcessInstance(
      final ProcessInstanceCancelRequest request, final CamundaAuthentication authentication) {
    final var brokerRequest =
        new BrokerCancelProcessInstanceRequest()
            .setProcessInstanceKey(request.processInstanceKey());

    if (request.operationReference() != null) {
      brokerRequest.setOperationReference(request.operationReference());
    }
    return sendBrokerRequest(brokerRequest, authentication);
  }

  public CompletableFuture<BatchOperationCreationRecord>
      cancelProcessInstanceBatchOperationWithResult(
          final ProcessInstanceFilter filter, final CamundaAuthentication authentication) {
    final var brokerRequest =
        new BrokerCreateBatchOperationRequest()
            .setFilter(filter)
            .setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE)
            .setAuthentication(authentication);

    return sendBrokerRequest(brokerRequest, authentication);
  }

  public CompletableFuture<BatchOperationCreationRecord> resolveProcessInstanceIncidents(
      final long processInstanceKey, final CamundaAuthentication authentication) {
    // internal read, no user permissions needed
    final var processInstance =
        getByKey(
            processInstanceKey,
            securityContextProvider.provideSecurityContext(CamundaAuthentication.anonymous()));

    final var brokerRequest =
        new BrokerCreateBatchOperationRequest()
            .setFilter(
                FilterBuilders.processInstance(f -> f.processInstanceKeys(processInstanceKey)))
            .setBatchOperationType(BatchOperationType.RESOLVE_INCIDENT)
            .setAuthentication(authentication)
            // the user only needs single instance update permission, not batch creation
            .setAuthorizationCheck(
                Authorization.withAuthorization(
                    PROCESS_INSTANCE_UPDATE_AUTHORIZATION, processInstance.processDefinitionId()));

    return sendBrokerRequest(brokerRequest, authentication);
  }

  public CompletableFuture<BatchOperationCreationRecord> resolveIncidentsBatchOperationWithResult(
      final ProcessInstanceFilter filter, final CamundaAuthentication authentication) {
    final var brokerRequest =
        new BrokerCreateBatchOperationRequest()
            .setFilter(filter)
            .setBatchOperationType(BatchOperationType.RESOLVE_INCIDENT)
            .setAuthentication(authentication);

    return sendBrokerRequest(brokerRequest, authentication);
  }

  public CompletableFuture<BatchOperationCreationRecord> migrateProcessInstancesBatchOperation(
      final ProcessInstanceMigrateBatchOperationRequest request,
      final CamundaAuthentication authentication) {
    final var migrationPlan = new BatchOperationProcessInstanceMigrationPlan();
    migrationPlan.setTargetProcessDefinitionKey(request.targetProcessDefinitionKey);
    request.mappingInstructions.forEach(migrationPlan::addMappingInstruction);

    final var brokerRequest =
        new BrokerCreateBatchOperationRequest()
            .setFilter(request.filter)
            .setMigrationPlan(migrationPlan)
            .setBatchOperationType(BatchOperationType.MIGRATE_PROCESS_INSTANCE)
            .setAuthentication(authentication);

    return sendBrokerRequest(brokerRequest, authentication);
  }

  public CompletableFuture<ProcessInstanceMigrationRecord> migrateProcessInstance(
      final ProcessInstanceMigrateRequest request, final CamundaAuthentication authentication) {
    final var brokerRequest =
        new BrokerMigrateProcessInstanceRequest()
            .setProcessInstanceKey(request.processInstanceKey())
            .setTargetProcessDefinitionKey(request.targetProcessDefinitionKey())
            .setMappingInstructions(request.mappingInstructions());

    if (request.operationReference() != null) {
      brokerRequest.setOperationReference(request.operationReference());
    }
    return sendBrokerRequest(brokerRequest, authentication);
  }

  public CompletableFuture<ProcessInstanceModificationRecord> modifyProcessInstance(
      final ProcessInstanceModifyRequest request, final CamundaAuthentication authentication) {
    final var brokerRequest =
        new BrokerModifyProcessInstanceRequest()
            .setProcessInstanceKey(request.processInstanceKey())
            .addActivationInstructions(request.activateInstructions())
            .addMovingInstructions(request.moveInstructions())
            .addTerminationInstructions(request.terminateInstructions());

    if (request.operationReference() != null) {
      brokerRequest.setOperationReference(request.operationReference());
    }
    return sendBrokerRequest(brokerRequest, authentication);
  }

  public CompletableFuture<BatchOperationCreationRecord> modifyProcessInstancesBatchOperation(
      final ProcessInstanceModifyBatchOperationRequest request,
      final CamundaAuthentication authentication) {
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

    return sendBrokerRequest(brokerRequest, authentication);
  }

  public CompletableFuture<BatchOperationCreationRecord> deleteProcessInstancesBatchOperation(
      final ProcessInstanceFilter filter, final CamundaAuthentication authentication) {
    final var brokerRequest =
        new BrokerCreateBatchOperationRequest()
            .setFilter(filter)
            .setBatchOperationType(BatchOperationType.DELETE_PROCESS_INSTANCE)
            .setAuthentication(authentication);
    return sendBrokerRequest(brokerRequest, authentication);
  }

  public CompletableFuture<HistoryDeletionRecord> deleteProcessInstance(
      final Long processInstanceKey,
      final Long operationReference,
      final CamundaAuthentication authentication) {

    // make sure process instance exists before deletion, otherwise return not found
    final var processInstance =
        getByKey(
            processInstanceKey,
            securityContextProvider.provideSecurityContext(CamundaAuthentication.anonymous()));

    // We pass along the process id and tenant id as the process instance won't exist in primary
    // storage anymore. We cannot rely on just the key.
    final var brokerRequest =
        new BrokerDeleteHistoryRequest()
            .setResourceKey(processInstanceKey)
            .setResourceType(HistoryDeletionType.PROCESS_INSTANCE)
            .setProcessId(processInstance.processDefinitionId())
            .setTenantId(processInstance.tenantId());

    if (operationReference != null) {
      brokerRequest.setOperationReference(operationReference);
    }

    return sendBrokerRequest(brokerRequest, authentication);
  }

  public SearchQueryResult<IncidentEntity> searchIncidents(
      final long processInstanceKey,
      final IncidentQuery query,
      final CamundaAuthentication authentication) {
    final var processInstance = getByKey(processInstanceKey, authentication);
    final var treePath = processInstance.treePath();

    return incidentServices.search(
        IncidentQuery.of(
            b ->
                b.filter(
                        query.filter().toBuilder()
                            .treePathOperations(Operation.like("*" + treePath + "*"))
                            .build())
                    .page(query.page())
                    .sort(query.sort())),
        authentication);
  }

  private <R> CompletableFuture<R> sendWithPerProcessRoundRobin(
      final BrokerRequest<R> brokerRequest,
      final String bpmnProcessId,
      final CamundaAuthentication authentication,
      final Duration requestTimeout) {
    final var handler = getRetryHandler(bpmnProcessId);
    return sendRequestWithRetryPartitions(brokerRequest, authentication, requestTimeout, handler)
        .whenComplete(
            (response, error) -> {
              if (error == null && bpmnProcessId != null) {
                processIdToRetryHandler.computeIfAbsent(
                    bpmnProcessId, ignored -> createRetryHandler());
              }
            });
  }

  private RequestRetryHandler getRetryHandler(final String bpmnProcessId) {
    if (bpmnProcessId == null) {
      return requestRetryHandler;
    }
    final var handler = processIdToRetryHandler.get(bpmnProcessId);
    return handler != null ? handler : requestRetryHandler;
  }

  private RequestRetryHandler createRetryHandler() {
    return new RequestRetryHandler(brokerClient, brokerClient.getTopologyManager());
  }

  private <R> CompletableFuture<R> sendRequestWithRetryPartitions(
      final BrokerRequest<R> brokerRequest, final CamundaAuthentication authentication) {
    return sendRequestWithRetryPartitions(brokerRequest, authentication, null, requestRetryHandler);
  }

  private <R> CompletableFuture<R> sendRequestWithRetryPartitions(
      final BrokerRequest<R> brokerRequest,
      final CamundaAuthentication authentication,
      final Duration requestTimeout) {
    return sendRequestWithRetryPartitions(
        brokerRequest, authentication, requestTimeout, requestRetryHandler);
  }

  private <R> CompletableFuture<R> sendRequestWithRetryPartitions(
      final BrokerRequest<R> brokerRequest,
      final CamundaAuthentication authentication,
      final Duration requestTimeout,
      final RequestRetryHandler handler) {
    final var brokerRequestAuthorization =
        brokerRequestAuthorizationConverter.convert(authentication);
    brokerRequest.setAuthorization(brokerRequestAuthorization);
    final CompletableFuture<R> responseFuture = new CompletableFuture<>();
    if (requestTimeout != null) {
      handler.sendRequest(
          brokerRequest,
          (key, response) -> responseFuture.complete(response),
          responseFuture::completeExceptionally,
          requestTimeout);
    } else {
      handler.sendRequest(
          brokerRequest,
          (key, response) -> responseFuture.complete(response),
          responseFuture::completeExceptionally);
    }
    return responseFuture.handleAsync(
        (response, error) -> {
          if (error != null) {
            throw ErrorMapper.mapError(error);
          }
          return response;
        },
        executor);
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
      List<String> fetchVariables,
      Set<String> tags,
      String businessId) {}

  public record ProcessInstanceCancelRequest(Long processInstanceKey, Long operationReference) {}

  public record ProcessInstanceMigrateRequest(
      Long processInstanceKey,
      Long targetProcessDefinitionKey,
      List<ProcessInstanceMigrationMappingInstruction> mappingInstructions,
      Long operationReference) {}

  public record ProcessInstanceModifyRequest(
      Long processInstanceKey,
      List<ProcessInstanceModificationActivateInstruction> activateInstructions,
      List<ProcessInstanceModificationMoveInstruction> moveInstructions,
      List<ProcessInstanceModificationTerminateInstruction> terminateInstructions,
      Long operationReference) {}

  public record ProcessInstanceMigrateBatchOperationRequest(
      ProcessInstanceFilter filter,
      Long targetProcessDefinitionKey,
      List<ProcessInstanceMigrationMappingInstruction> mappingInstructions) {}

  public record ProcessInstanceModifyBatchOperationRequest(
      ProcessInstanceFilter filter,
      List<ProcessInstanceModificationMoveInstruction> moveInstructions) {}
}
