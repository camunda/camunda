/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.protocol.model.CancelProcessInstanceRequest;
import io.camunda.gateway.protocol.model.DeleteProcessInstanceRequest;
import io.camunda.gateway.protocol.model.IncidentSearchQuery;
import io.camunda.gateway.protocol.model.IncidentSearchQueryResult;
import io.camunda.gateway.protocol.model.ProcessInstanceCancellationBatchOperationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceDeletionBatchOperationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceIncidentResolutionBatchOperationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceMigrationBatchOperationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceMigrationInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceModificationBatchOperationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceModificationInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceSearchQuery;
import io.camunda.gateway.protocol.model.ProcessInstanceSearchQueryResult;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.config.MultiTenancyConfiguration;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCancelRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCreateRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceMigrateBatchOperationRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceMigrateRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceModifyBatchOperationRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceModifyRequest;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
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

  public ProcessInstanceController(
      final ServiceRegistry serviceRegistry,
      final MultiTenancyConfiguration multiTenancyCfg,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceRegistry = serviceRegistry;
    this.multiTenancyCfg = multiTenancyCfg;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping
  public CompletableFuture<ResponseEntity<Object>> createProcessInstance(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody final ProcessInstanceCreationInstruction request) {
    return RequestMapper.toCreateProcessInstance(request, multiTenancyCfg.isChecksEnabled())
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            mapped ->
                createProcessInstance(
                    serviceRegistry.processInstanceServices(physicalTenantId), mapped));
  }

  @CamundaPostMapping(path = "/{processInstanceKey}/cancellation")
  public CompletableFuture<ResponseEntity<Object>> cancelProcessInstance(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final long processInstanceKey,
      @RequestBody(required = false) final CancelProcessInstanceRequest cancelRequest) {
    return RequestMapper.toCancelProcessInstance(processInstanceKey, cancelRequest)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            mapped ->
                cancelProcessInstance(
                    serviceRegistry.processInstanceServices(physicalTenantId), mapped));
  }

  @CamundaPostMapping(path = "/{processInstanceKey}/migration")
  public CompletableFuture<ResponseEntity<Object>> migrateProcessInstance(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final long processInstanceKey,
      @RequestBody final ProcessInstanceMigrationInstruction migrationRequest) {
    return RequestMapper.toMigrateProcessInstance(processInstanceKey, migrationRequest)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            mapped ->
                migrateProcessInstance(
                    serviceRegistry.processInstanceServices(physicalTenantId), mapped));
  }

  @CamundaPostMapping(path = "/{processInstanceKey}/modification")
  public CompletableFuture<ResponseEntity<Object>> modifyProcessInstance(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final long processInstanceKey,
      @RequestBody final ProcessInstanceModificationInstruction modifyRequest) {
    return RequestMapper.toModifyProcessInstance(processInstanceKey, modifyRequest)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            mapped ->
                modifyProcessInstance(
                    serviceRegistry.processInstanceServices(physicalTenantId), mapped));
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
        .fold(
            RestErrorMapper::mapProblemToResponse,
            mapped -> search(serviceRegistry.processInstanceServices(physicalTenantId), mapped));
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{processInstanceKey}")
  public ResponseEntity<Object> getByKey(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("processInstanceKey") final Long processInstanceKey) {
    try {
      // Success case: Return the left side with the ProcessInstanceItem wrapped in ResponseEntity
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toProcessInstance(
                  serviceRegistry
                      .processInstanceServices(physicalTenantId)
                      .getByKey(
                          processInstanceKey, authenticationProvider.getCamundaAuthentication())));
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
    return RequestMapper.toRequiredProcessInstanceFilter(request.getFilter())
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            filter ->
                batchOperationCancellation(
                    serviceRegistry.processInstanceServices(physicalTenantId), filter));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/incident-resolution")
  public CompletableFuture<ResponseEntity<Object>> resolveIncidentsBatchOperation(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody final ProcessInstanceIncidentResolutionBatchOperationRequest request) {
    return RequestMapper.toRequiredProcessInstanceFilter(request.getFilter())
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            filter ->
                batchOperationResolveIncidents(
                    serviceRegistry.processInstanceServices(physicalTenantId), filter));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/migration")
  public CompletableFuture<ResponseEntity<Object>> migrateProcessInstancesBatchOperation(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody final ProcessInstanceMigrationBatchOperationRequest request) {
    return RequestMapper.toProcessInstanceMigrationBatchOperationRequest(request)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            mapped ->
                batchOperationMigrate(
                    serviceRegistry.processInstanceServices(physicalTenantId), mapped));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/modification")
  public CompletableFuture<ResponseEntity<Object>> modifyProcessInstancesBatchOperation(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody final ProcessInstanceModificationBatchOperationRequest request) {
    return RequestMapper.toProcessInstanceModifyBatchOperationRequest(request)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            mapped ->
                batchOperationModify(
                    serviceRegistry.processInstanceServices(physicalTenantId), mapped));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/deletion")
  public CompletableFuture<ResponseEntity<Object>> deleteProcessInstancesBatchOperation(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody final ProcessInstanceDeletionBatchOperationRequest request) {
    return RequestMapper.toRequiredProcessInstanceFilter(request.getFilter())
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            filter ->
                batchOperationDeletion(
                    serviceRegistry.processInstanceServices(physicalTenantId), filter));
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
            incidentQuery ->
                searchIncidents(
                    serviceRegistry.processInstanceServices(physicalTenantId),
                    processInstanceKey,
                    incidentQuery));
  }

  private ResponseEntity<ProcessInstanceSearchQueryResult> search(
      final ProcessInstanceServices processInstanceServices, final ProcessInstanceQuery query) {
    try {
      final var result =
          processInstanceServices.search(query, authenticationProvider.getCamundaAuthentication());
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toProcessInstanceSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<IncidentSearchQueryResult> searchIncidents(
      final ProcessInstanceServices processInstanceServices,
      final long processInstanceKey,
      final IncidentQuery query) {
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
      final ProcessInstanceServices processInstanceServices,
      final io.camunda.search.filter.ProcessInstanceFilter filter) {
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices.cancelProcessInstanceBatchOperationWithResult(
                filter, authenticationProvider.getCamundaAuthentication()),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> batchOperationResolveIncidents(
      final ProcessInstanceServices processInstanceServices,
      final io.camunda.search.filter.ProcessInstanceFilter filter) {
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices.resolveIncidentsBatchOperationWithResult(
                filter, authenticationProvider.getCamundaAuthentication()),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> batchOperationMigrate(
      final ProcessInstanceServices processInstanceServices,
      final ProcessInstanceMigrateBatchOperationRequest request) {
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices.migrateProcessInstancesBatchOperation(
                request, authenticationProvider.getCamundaAuthentication()),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> batchOperationModify(
      final ProcessInstanceServices processInstanceServices,
      final ProcessInstanceModifyBatchOperationRequest request) {
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices.modifyProcessInstancesBatchOperation(
                request, authenticationProvider.getCamundaAuthentication()),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> batchOperationDeletion(
      final ProcessInstanceServices processInstanceServices,
      final io.camunda.search.filter.ProcessInstanceFilter filter) {
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices.deleteProcessInstancesBatchOperation(
                filter, authenticationProvider.getCamundaAuthentication()),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> createProcessInstance(
      final ProcessInstanceServices processInstanceServices,
      final ProcessInstanceCreateRequest request) {
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
      final ProcessInstanceServices processInstanceServices,
      final ProcessInstanceCancelRequest request) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            processInstanceServices.cancelProcessInstance(
                request, authenticationProvider.getCamundaAuthentication()));
  }

  private CompletableFuture<ResponseEntity<Object>> migrateProcessInstance(
      final ProcessInstanceServices processInstanceServices,
      final ProcessInstanceMigrateRequest request) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            processInstanceServices.migrateProcessInstance(
                request, authenticationProvider.getCamundaAuthentication()));
  }

  private CompletableFuture<ResponseEntity<Object>> modifyProcessInstance(
      final ProcessInstanceServices processInstanceServices,
      final ProcessInstanceModifyRequest request) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            processInstanceServices.modifyProcessInstance(
                request, authenticationProvider.getCamundaAuthentication()));
  }
}
