/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.search.entities.AuditLogEntity.AuditLogEntityType.PROCESS_INSTANCE;
import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.mapping.http.mapper.ProcessInstanceMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.protocol.model.CancelProcessInstanceRequest;
import io.camunda.gateway.protocol.model.DeleteProcessInstanceRequest;
import io.camunda.gateway.protocol.model.IncidentSearchQuery;
import io.camunda.gateway.protocol.model.IncidentSearchQueryResult;
import io.camunda.gateway.protocol.model.ProcessInstanceBusinessIdAssignmentInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceCancellationBatchOperationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceDeletionBatchOperationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceIncidentResolutionBatchOperationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceMigrationBatchOperationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceMigrationInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceModificationBatchOperationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceModificationInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceResumptionBatchOperationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceSearchQuery;
import io.camunda.gateway.protocol.model.ProcessInstanceSearchQueryResult;
import io.camunda.gateway.protocol.model.ProcessInstanceSuspensionBatchOperationRequest;
import io.camunda.gateway.protocol.model.ResumeProcessInstanceRequest;
import io.camunda.gateway.protocol.model.SuspendProcessInstanceRequest;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.config.MultiTenancyConfiguration;
import io.camunda.service.ProcessInstanceServices.AssignProcessInstanceBusinessIdRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCancelRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCreateRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceMigrateBatchOperationRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceMigrateRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceModifyBatchOperationRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceModifyRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceResumeRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceSuspendRequest;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.mapper.UpdateMetadataMapper;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/process-instances")
public class ProcessInstanceController {

  private final ServiceRegistry serviceRegistry;
  private final MultiTenancyConfiguration multiTenancyCfg;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final ProcessInstanceMapper processInstanceMapper;

  public ProcessInstanceController(
      final ServiceRegistry serviceRegistry,
      final MultiTenancyConfiguration multiTenancyCfg,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceRegistry = serviceRegistry;
    this.multiTenancyCfg = multiTenancyCfg;
    this.authenticationProvider = authenticationProvider;
    processInstanceMapper = new ProcessInstanceMapper();
  }

  @CamundaPostMapping
  public CompletableFuture<ResponseEntity<Object>> createProcessInstance(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody final ProcessInstanceCreationInstruction request) {
    return processInstanceMapper
        .toCreateProcessInstance(request, multiTenancyCfg.isChecksEnabled())
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            mapped -> createProcessInstance(physicalTenantId, mapped));
  }

  @CamundaPostMapping(path = "/{processInstanceKey}/cancellation")
  public CompletableFuture<ResponseEntity<Object>> cancelProcessInstance(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final long processInstanceKey,
      @RequestBody(required = false) final CancelProcessInstanceRequest cancelRequest) {
    return processInstanceMapper
        .toCancelProcessInstance(processInstanceKey, cancelRequest)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            mapped -> cancelProcessInstance(physicalTenantId, mapped));
  }

  @CamundaPostMapping(path = "/{processInstanceKey}/suspension")
  public CompletableFuture<ResponseEntity<Object>> suspendProcessInstance(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final long processInstanceKey,
      @RequestBody(required = false) final SuspendProcessInstanceRequest suspendRequest) {
    return processInstanceMapper
        .toSuspendProcessInstance(processInstanceKey, suspendRequest)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            mapped -> suspendProcessInstance(physicalTenantId, mapped));
  }

  @CamundaPostMapping(path = "/{processInstanceKey}/resumption")
  public CompletableFuture<ResponseEntity<Object>> resumeProcessInstance(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final long processInstanceKey,
      @RequestBody(required = false) final ResumeProcessInstanceRequest resumeRequest) {
    return processInstanceMapper
        .toResumeProcessInstance(processInstanceKey, resumeRequest)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            mapped -> resumeProcessInstance(physicalTenantId, mapped));
  }

  @CamundaPostMapping(path = "/{processInstanceKey}/migration")
  public CompletableFuture<ResponseEntity<Object>> migrateProcessInstance(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final long processInstanceKey,
      @RequestBody final ProcessInstanceMigrationInstruction migrationRequest) {
    return processInstanceMapper
        .toMigrateProcessInstance(processInstanceKey, migrationRequest)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            mapped -> migrateProcessInstance(physicalTenantId, mapped));
  }

  @CamundaPostMapping(path = "/{processInstanceKey}/business-id-assignment")
  public CompletableFuture<ResponseEntity<Object>> assignProcessInstanceBusinessId(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final long processInstanceKey,
      @RequestBody final ProcessInstanceBusinessIdAssignmentInstruction assignmentRequest) {
    return processInstanceMapper
        .toAssignProcessInstanceBusinessId(processInstanceKey, assignmentRequest)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            mapped -> assignProcessInstanceBusinessId(physicalTenantId, mapped));
  }

  @CamundaPostMapping(path = "/{processInstanceKey}/modification")
  public CompletableFuture<ResponseEntity<Object>> modifyProcessInstance(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final long processInstanceKey,
      @RequestBody final ProcessInstanceModificationInstruction modifyRequest) {
    return processInstanceMapper
        .toModifyProcessInstance(processInstanceKey, modifyRequest)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            mapped -> modifyProcessInstance(physicalTenantId, mapped));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{processInstanceKey}/incident-resolution")
  public CompletableFuture<ResponseEntity<Object>> resolveProcessInstanceIncidents(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final long processInstanceKey) {
    return RequestExecutor.executeServiceMethod(
        () ->
            serviceRegistry
                .processInstanceServices(physicalTenantId)
                .resolveProcessInstanceIncidents(
                    processInstanceKey, authenticationProvider.getCamundaAuthentication()),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search")
  public ResponseEntity<ProcessInstanceSearchQueryResult> searchProcessInstances(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody(required = false) final ProcessInstanceSearchQuery query) {
    return SearchQueryRequestMapper.toProcessInstanceQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, mapped -> search(physicalTenantId, mapped));
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{processInstanceKey}")
  public ResponseEntity<Object> getByKey(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("processInstanceKey") final Long processInstanceKey) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var response =
          SearchQueryResponseMapper.toProcessInstance(
              serviceRegistry
                  .processInstanceServices(physicalTenantId)
                  .getByKey(processInstanceKey, authentication));
      UpdateMetadataMapper.addUpdateMetadata(
          response,
          io.camunda.gateway.protocol.model.ProcessInstanceResult::getProcessInstanceKey,
          PROCESS_INSTANCE,
          serviceRegistry.auditLogServices(physicalTenantId),
          authentication,
          io.camunda.gateway.protocol.model.ProcessInstanceResult::setUpdatedBy,
          io.camunda.gateway.protocol.model.ProcessInstanceResult::setUpdatedAt);
      // Success case: Return the left side with the ProcessInstanceItem wrapped in ResponseEntity
      return ResponseEntity.ok().body(response);
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{processInstanceKey}/deletion")
  public CompletableFuture<ResponseEntity<Object>> deleteProcessInstance(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("processInstanceKey") final Long processInstanceKey,
      @RequestBody(required = false) final DeleteProcessInstanceRequest request) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            serviceRegistry
                .processInstanceServices(physicalTenantId)
                .deleteProcessInstance(
                    processInstanceKey,
                    Objects.nonNull(request) ? request.getOperationReference() : null,
                    authenticationProvider.getCamundaAuthentication()));
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{processInstanceKey}/call-hierarchy")
  public ResponseEntity<Object> getCallHierarchy(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("processInstanceKey") final Long processInstanceKey) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toProcessInstanceCallHierarchyEntries(
                  serviceRegistry
                      .processInstanceServices(physicalTenantId)
                      .callHierarchy(
                          processInstanceKey, authenticationProvider.getCamundaAuthentication())));

    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{processInstanceKey}/statistics/element-instances")
  public ResponseEntity<Object> elementStatistics(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("processInstanceKey") final Long processInstanceKey) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toProcessInstanceElementStatisticsResult(
                  serviceRegistry
                      .processInstanceServices(physicalTenantId)
                      .elementStatistics(
                          processInstanceKey, authenticationProvider.getCamundaAuthentication())));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{processInstanceKey}/statistics/wait-states")
  public ResponseEntity<Object> waitStateStatistics(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("processInstanceKey") final Long processInstanceKey) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toProcessInstanceWaitStateStatisticsResult(
                  serviceRegistry
                      .processInstanceServices(physicalTenantId)
                      .waitStateStatistics(
                          processInstanceKey, authenticationProvider.getCamundaAuthentication())));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{processInstanceKey}/sequence-flows")
  public ResponseEntity<Object> sequenceFlows(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("processInstanceKey") final Long processInstanceKey) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toSequenceFlowsResult(
                  serviceRegistry
                      .processInstanceServices(physicalTenantId)
                      .sequenceFlows(
                          processInstanceKey, authenticationProvider.getCamundaAuthentication())));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/cancellation")
  public CompletableFuture<ResponseEntity<Object>> cancelProcessInstancesBatchOperation(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody final ProcessInstanceCancellationBatchOperationRequest request) {
    return processInstanceMapper
        .toRequiredProcessInstanceFilter(request.getFilter())
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            filter -> batchOperationCancellation(physicalTenantId, filter));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/suspension")
  public CompletableFuture<ResponseEntity<Object>> suspendProcessInstancesBatchOperation(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody final ProcessInstanceSuspensionBatchOperationRequest request) {
    return processInstanceMapper
        .toRequiredProcessInstanceFilter(request.getFilter())
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            filter -> batchOperationSuspension(physicalTenantId, filter));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/resumption")
  public CompletableFuture<ResponseEntity<Object>> resumeProcessInstancesBatchOperation(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody final ProcessInstanceResumptionBatchOperationRequest request) {
    return processInstanceMapper
        .toRequiredProcessInstanceFilter(request.getFilter())
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            filter -> batchOperationResumption(physicalTenantId, filter));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/incident-resolution")
  public CompletableFuture<ResponseEntity<Object>> resolveIncidentsBatchOperation(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody final ProcessInstanceIncidentResolutionBatchOperationRequest request) {
    return processInstanceMapper
        .toRequiredProcessInstanceFilter(request.getFilter())
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            filter -> batchOperationResolveIncidents(physicalTenantId, filter));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/migration")
  public CompletableFuture<ResponseEntity<Object>> migrateProcessInstancesBatchOperation(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody final ProcessInstanceMigrationBatchOperationRequest request) {
    return processInstanceMapper
        .toProcessInstanceMigrationBatchOperationRequest(request)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            mapped -> batchOperationMigrate(physicalTenantId, mapped));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/modification")
  public CompletableFuture<ResponseEntity<Object>> modifyProcessInstancesBatchOperation(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody final ProcessInstanceModificationBatchOperationRequest request) {
    return processInstanceMapper
        .toProcessInstanceModifyBatchOperationRequest(request)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            mapped -> batchOperationModify(physicalTenantId, mapped));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/deletion")
  public CompletableFuture<ResponseEntity<Object>> deleteProcessInstancesBatchOperation(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody final ProcessInstanceDeletionBatchOperationRequest request) {
    return processInstanceMapper
        .toRequiredProcessInstanceFilter(request.getFilter())
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            filter -> batchOperationDeletion(physicalTenantId, filter));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{processInstanceKey}/incidents/search")
  public ResponseEntity<IncidentSearchQueryResult> searchIncidents(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("processInstanceKey") final long processInstanceKey,
      @RequestBody(required = false) final IncidentSearchQuery query) {
    return SearchQueryRequestMapper.toIncidentQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            incidentQuery -> searchIncidents(physicalTenantId, processInstanceKey, incidentQuery));
  }

  private ResponseEntity<ProcessInstanceSearchQueryResult> search(
      final String physicalTenantId, final ProcessInstanceQuery query) {
    final var processInstanceServices = serviceRegistry.processInstanceServices(physicalTenantId);
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result = processInstanceServices.search(query, authentication);
      final var response = SearchQueryResponseMapper.toProcessInstanceSearchQueryResponse(result);
      UpdateMetadataMapper.addUpdateMetadata(
          response.getItems(),
          io.camunda.gateway.protocol.model.ProcessInstanceResult::getProcessInstanceKey,
          PROCESS_INSTANCE,
          serviceRegistry.auditLogServices(physicalTenantId),
          authentication,
          io.camunda.gateway.protocol.model.ProcessInstanceResult::setUpdatedBy,
          io.camunda.gateway.protocol.model.ProcessInstanceResult::setUpdatedAt);
      return ResponseEntity.ok(response);
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<IncidentSearchQueryResult> searchIncidents(
      final String physicalTenantId, final long processInstanceKey, final IncidentQuery query) {
    final var processInstanceServices = serviceRegistry.processInstanceServices(physicalTenantId);
    try {
      final var result =
          processInstanceServices.searchIncidents(
              processInstanceKey, query, authenticationProvider.getCamundaAuthentication());
      return ResponseEntity.ok(SearchQueryResponseMapper.toIncidentSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private CompletableFuture<ResponseEntity<Object>> batchOperationCancellation(
      final String physicalTenantId, final io.camunda.search.filter.ProcessInstanceFilter filter) {
    final var processInstanceServices = serviceRegistry.processInstanceServices(physicalTenantId);
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices.cancelProcessInstanceBatchOperationWithResult(
                filter, authenticationProvider.getCamundaAuthentication()),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> batchOperationSuspension(
      final String physicalTenantId, final io.camunda.search.filter.ProcessInstanceFilter filter) {
    final var processInstanceServices = serviceRegistry.processInstanceServices(physicalTenantId);
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices.suspendProcessInstanceBatchOperationWithResult(
                filter, authenticationProvider.getCamundaAuthentication()),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> batchOperationResumption(
      final String physicalTenantId, final io.camunda.search.filter.ProcessInstanceFilter filter) {
    final var processInstanceServices = serviceRegistry.processInstanceServices(physicalTenantId);
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices.resumeProcessInstanceBatchOperationWithResult(
                filter, authenticationProvider.getCamundaAuthentication()),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> batchOperationResolveIncidents(
      final String physicalTenantId, final io.camunda.search.filter.ProcessInstanceFilter filter) {
    final var processInstanceServices = serviceRegistry.processInstanceServices(physicalTenantId);
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices.resolveIncidentsBatchOperationWithResult(
                filter, authenticationProvider.getCamundaAuthentication()),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> batchOperationMigrate(
      final String physicalTenantId, final ProcessInstanceMigrateBatchOperationRequest request) {
    final var processInstanceServices = serviceRegistry.processInstanceServices(physicalTenantId);
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices.migrateProcessInstancesBatchOperation(
                request, authenticationProvider.getCamundaAuthentication()),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> batchOperationModify(
      final String physicalTenantId, final ProcessInstanceModifyBatchOperationRequest request) {
    final var processInstanceServices = serviceRegistry.processInstanceServices(physicalTenantId);
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices.modifyProcessInstancesBatchOperation(
                request, authenticationProvider.getCamundaAuthentication()),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> batchOperationDeletion(
      final String physicalTenantId, final io.camunda.search.filter.ProcessInstanceFilter filter) {
    final var processInstanceServices = serviceRegistry.processInstanceServices(physicalTenantId);
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices.deleteProcessInstancesBatchOperation(
                filter, authenticationProvider.getCamundaAuthentication()),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> createProcessInstance(
      final String physicalTenantId, final ProcessInstanceCreateRequest request) {
    final var processInstanceServices = serviceRegistry.processInstanceServices(physicalTenantId);
    if (request.awaitCompletion()) {
      return RequestExecutor.executeServiceMethod(
          () ->
              processInstanceServices.createProcessInstanceWithResult(
                  request, authenticationProvider.getCamundaAuthentication()),
          ResponseMapper::toCreateProcessInstanceWithResultResponse,
          HttpStatus.OK);
    }
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices.createProcessInstance(
                request, authenticationProvider.getCamundaAuthentication()),
        ResponseMapper::toCreateProcessInstanceResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> cancelProcessInstance(
      final String physicalTenantId, final ProcessInstanceCancelRequest request) {
    final var processInstanceServices = serviceRegistry.processInstanceServices(physicalTenantId);
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            processInstanceServices.cancelProcessInstance(
                request, authenticationProvider.getCamundaAuthentication()));
  }

  private CompletableFuture<ResponseEntity<Object>> suspendProcessInstance(
      final String physicalTenantId, final ProcessInstanceSuspendRequest request) {
    final var processInstanceServices = serviceRegistry.processInstanceServices(physicalTenantId);
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            processInstanceServices.suspendProcessInstance(
                request, authenticationProvider.getCamundaAuthentication()));
  }

  private CompletableFuture<ResponseEntity<Object>> resumeProcessInstance(
      final String physicalTenantId, final ProcessInstanceResumeRequest request) {
    final var processInstanceServices = serviceRegistry.processInstanceServices(physicalTenantId);
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            processInstanceServices.resumeProcessInstance(
                request, authenticationProvider.getCamundaAuthentication()));
  }

  private CompletableFuture<ResponseEntity<Object>> migrateProcessInstance(
      final String physicalTenantId, final ProcessInstanceMigrateRequest request) {
    final var processInstanceServices = serviceRegistry.processInstanceServices(physicalTenantId);
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            processInstanceServices.migrateProcessInstance(
                request, authenticationProvider.getCamundaAuthentication()));
  }

  private CompletableFuture<ResponseEntity<Object>> assignProcessInstanceBusinessId(
      final String physicalTenantId, final AssignProcessInstanceBusinessIdRequest request) {
    final var processInstanceServices = serviceRegistry.processInstanceServices(physicalTenantId);
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            processInstanceServices.assignProcessInstanceBusinessId(
                request, authenticationProvider.getCamundaAuthentication()));
  }

  private CompletableFuture<ResponseEntity<Object>> modifyProcessInstance(
      final String physicalTenantId, final ProcessInstanceModifyRequest request) {
    final var processInstanceServices = serviceRegistry.processInstanceServices(physicalTenantId);
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            processInstanceServices.modifyProcessInstance(
                request, authenticationProvider.getCamundaAuthentication()));
  }
}
