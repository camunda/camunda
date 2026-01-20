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
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCancelRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCreateRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceMigrateBatchOperationRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceMigrateRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceModifyBatchOperationRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceModifyRequest;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@CamundaRestController("/v2/process-instances")
public class ProcessInstanceController {

  private final ProcessInstanceServices processInstanceServices;
  private final MultiTenancyConfiguration multiTenancyCfg;
  private final CamundaAuthenticationProvider authenticationProvider;

  public ProcessInstanceController(
      final ProcessInstanceServices processInstanceServices,
      final MultiTenancyConfiguration multiTenancyCfg,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.processInstanceServices = processInstanceServices;
    this.multiTenancyCfg = multiTenancyCfg;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping
  public CompletableFuture<ResponseEntity<Object>> createProcessInstance(
      @PathVariable(name = "engineName", required = false) final String engineName,
      @RequestBody final ProcessInstanceCreationInstruction request) {
    return RequestMapper.toCreateProcessInstance(request, multiTenancyCfg.isChecksEnabled())
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> createProcessInstance(req, engineName));
  }

  @CamundaPostMapping(path = "/{processInstanceKey}/cancellation")
  public CompletableFuture<ResponseEntity<Object>> cancelProcessInstance(
      @PathVariable(name = "engineName", required = false) final String engineName,
      @PathVariable final long processInstanceKey,
      @RequestBody(required = false) final CancelProcessInstanceRequest cancelRequest) {
    return RequestMapper.toCancelProcessInstance(processInstanceKey, cancelRequest)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> cancelProcessInstance(req, engineName));
  }

  @CamundaPostMapping(path = "/{processInstanceKey}/migration")
  public CompletableFuture<ResponseEntity<Object>> migrateProcessInstance(
      @PathVariable(name = "engineName", required = false) final String engineName,
      @PathVariable final long processInstanceKey,
      @RequestBody final ProcessInstanceMigrationInstruction migrationRequest) {
    return RequestMapper.toMigrateProcessInstance(processInstanceKey, migrationRequest)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> migrateProcessInstance(req, engineName));
  }

  @CamundaPostMapping(path = "/{processInstanceKey}/modification")
  public CompletableFuture<ResponseEntity<Object>> modifyProcessInstance(
      @PathVariable(name = "engineName", required = false) final String engineName,
      @PathVariable final long processInstanceKey,
      @RequestBody final ProcessInstanceModificationInstruction modifyRequest) {
    return RequestMapper.toModifyProcessInstance(processInstanceKey, modifyRequest)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> modifyProcessInstance(req, engineName));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{processInstanceKey}/incident-resolution")
  public CompletableFuture<ResponseEntity<Object>> resolveProcessInstanceIncidents(
      @PathVariable(name = "engineName", required = false) final String engineName,
      @PathVariable final long processInstanceKey) {
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .withEngineName(engineName)
                .resolveProcessInstanceIncidents(processInstanceKey),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search")
  public ResponseEntity<ProcessInstanceSearchQueryResult> searchProcessInstances(
      @PathVariable(name = "engineName", required = false) final String engineName,
      @RequestBody(required = false) final ProcessInstanceSearchQuery query) {
    return SearchQueryRequestMapper.toProcessInstanceQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, q -> search(q, engineName));
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{processInstanceKey}")
  public ResponseEntity<Object> getByKey(
      @PathVariable(name = "engineName", required = false) final String engineName,
      @PathVariable("processInstanceKey") final Long processInstanceKey) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toProcessInstance(
                  processInstanceServices
                      .withAuthentication(authenticationProvider.getCamundaAuthentication())
                      .withEngineName(engineName)
                      .getByKey(processInstanceKey)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{processInstanceKey}/deletion")
  public CompletableFuture<ResponseEntity<Object>> deleteProcessInstance(
      @PathVariable(name = "engineName", required = false) final String engineName,
      @PathVariable("processInstanceKey") final Long processInstanceKey,
      @RequestBody(required = false) final DeleteProcessInstanceRequest request) {
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .withEngineName(engineName)
                .deleteProcessInstance(
                    processInstanceKey,
                    Objects.nonNull(request) ? request.getOperationReference() : null),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{processInstanceKey}/call-hierarchy")
  public ResponseEntity<Object> getCallHierarchy(
      @PathVariable(name = "engineName", required = false) final String engineName,
      @PathVariable("processInstanceKey") final Long processInstanceKey) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toProcessInstanceCallHierarchyEntries(
                  processInstanceServices
                      .withAuthentication(authenticationProvider.getCamundaAuthentication())
                      .withEngineName(engineName)
                      .callHierarchy(processInstanceKey)));

    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{processInstanceKey}/statistics/element-instances")
  public ResponseEntity<Object> elementStatistics(
      @PathVariable(name = "engineName", required = false) final String engineName,
      @PathVariable("processInstanceKey") final Long processInstanceKey) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toProcessInstanceElementStatisticsResult(
                  processInstanceServices
                      .withAuthentication(authenticationProvider.getCamundaAuthentication())
                      .withEngineName(engineName)
                      .elementStatistics(processInstanceKey)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{processInstanceKey}/sequence-flows")
  public ResponseEntity<Object> sequenceFlows(
      @PathVariable(name = "engineName", required = false) final String engineName,
      @PathVariable("processInstanceKey") final Long processInstanceKey) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toSequenceFlowsResult(
                  processInstanceServices
                      .withAuthentication(authenticationProvider.getCamundaAuthentication())
                      .withEngineName(engineName)
                      .sequenceFlows(processInstanceKey)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/cancellation")
  public CompletableFuture<ResponseEntity<Object>> cancelProcessInstancesBatchOperation(
      @PathVariable(name = "engineName", required = false) final String engineName,
      @RequestBody final ProcessInstanceCancellationBatchOperationRequest request) {
    return RequestMapper.toRequiredProcessInstanceFilter(request.getFilter())
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            filter -> batchOperationCancellation(filter, engineName));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/incident-resolution")
  public CompletableFuture<ResponseEntity<Object>> resolveIncidentsBatchOperation(
      @PathVariable(name = "engineName", required = false) final String engineName,
      @RequestBody final ProcessInstanceIncidentResolutionBatchOperationRequest request) {
    return RequestMapper.toRequiredProcessInstanceFilter(request.getFilter())
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            filter -> batchOperationResolveIncidents(filter, engineName));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/migration")
  public CompletableFuture<ResponseEntity<Object>> migrateProcessInstancesBatchOperation(
      @PathVariable(name = "engineName", required = false) final String engineName,
      @RequestBody final ProcessInstanceMigrationBatchOperationRequest request) {
    return RequestMapper.toProcessInstanceMigrationBatchOperationRequest(request)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> batchOperationMigrate(req, engineName));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/modification")
  public CompletableFuture<ResponseEntity<Object>> modifyProcessInstancesBatchOperation(
      @PathVariable(name = "engineName", required = false) final String engineName,
      @RequestBody final ProcessInstanceModificationBatchOperationRequest request) {
    return RequestMapper.toProcessInstanceModifyBatchOperationRequest(request)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> batchOperationModify(req, engineName));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/deletion")
  public CompletableFuture<ResponseEntity<Object>> deleteProcessInstancesBatchOperation(
      @PathVariable(name = "engineName", required = false) final String engineName,
      @RequestBody final ProcessInstanceDeletionBatchOperationRequest request) {
    return RequestMapper.toRequiredProcessInstanceFilter(request.getFilter())
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            filter -> batchOperationDeletion(filter, engineName));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{processInstanceKey}/incidents/search")
  public ResponseEntity<IncidentSearchQueryResult> searchIncidents(
      @PathVariable(name = "engineName", required = false) final String engineName,
      @PathVariable("processInstanceKey") final long processInstanceKey,
      @RequestBody(required = false) final IncidentSearchQuery query) {
    return SearchQueryRequestMapper.toIncidentQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            incidentQuery -> searchIncidents(processInstanceKey, incidentQuery, engineName));
  }

  private ResponseEntity<ProcessInstanceSearchQueryResult> search(
      final ProcessInstanceQuery query, final String engineName) {
    try {
      final var result =
          processInstanceServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .withEngineName(engineName)
              .search(query);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toProcessInstanceSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<IncidentSearchQueryResult> searchIncidents(
      final long processInstanceKey, final IncidentQuery query, final String engineName) {
    try {
      final var result =
          processInstanceServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .withEngineName(engineName)
              .searchIncidents(processInstanceKey, query);
      return ResponseEntity.ok(SearchQueryResponseMapper.toIncidentSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private CompletableFuture<ResponseEntity<Object>> batchOperationCancellation(
      final io.camunda.search.filter.ProcessInstanceFilter filter, final String engineName) {
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .withEngineName(engineName)
                .cancelProcessInstanceBatchOperationWithResult(filter),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> batchOperationResolveIncidents(
      final io.camunda.search.filter.ProcessInstanceFilter filter, final String engineName) {
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .withEngineName(engineName)
                .resolveIncidentsBatchOperationWithResult(filter),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> batchOperationMigrate(
      final ProcessInstanceMigrateBatchOperationRequest request, final String engineName) {
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .withEngineName(engineName)
                .migrateProcessInstancesBatchOperation(request),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> batchOperationModify(
      final ProcessInstanceModifyBatchOperationRequest request, final String engineName) {
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .withEngineName(engineName)
                .modifyProcessInstancesBatchOperation(request),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> batchOperationDeletion(
      final io.camunda.search.filter.ProcessInstanceFilter filter, final String engineName) {
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .withEngineName(engineName)
                .deleteProcessInstancesBatchOperation(filter),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> createProcessInstance(
      final ProcessInstanceCreateRequest request, final String engineName) {
    if (request.awaitCompletion()) {
      return RequestExecutor.executeServiceMethod(
          () ->
              processInstanceServices
                  .withAuthentication(authenticationProvider.getCamundaAuthentication())
                  .withEngineName(engineName)
                  .createProcessInstanceWithResult(request),
          ResponseMapper::toCreateProcessInstanceWithResultResponse,
          HttpStatus.OK);
    }
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .withEngineName(engineName)
                .createProcessInstance(request),
        ResponseMapper::toCreateProcessInstanceResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> cancelProcessInstance(
      final ProcessInstanceCancelRequest request, final String engineName) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            processInstanceServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .withEngineName(engineName)
                .cancelProcessInstance(request));
  }

  private CompletableFuture<ResponseEntity<Object>> migrateProcessInstance(
      final ProcessInstanceMigrateRequest request, final String engineName) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            processInstanceServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .withEngineName(engineName)
                .migrateProcessInstance(request));
  }

  private CompletableFuture<ResponseEntity<Object>> modifyProcessInstance(
      final ProcessInstanceModifyRequest request, final String engineName) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            processInstanceServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .withEngineName(engineName)
                .modifyProcessInstance(request));
  }
}
