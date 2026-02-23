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
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/process-instances")
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
      @RequestBody final ProcessInstanceCreationInstruction request) {
    return RequestMapper.toCreateProcessInstance(request, multiTenancyCfg.isChecksEnabled())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::createProcessInstance);
  }

  @CamundaPostMapping(path = "/{processInstanceKey}/cancellation")
  public CompletableFuture<ResponseEntity<Object>> cancelProcessInstance(
      @PathVariable final long processInstanceKey,
      @RequestBody(required = false) final CancelProcessInstanceRequest cancelRequest) {
    return RequestMapper.toCancelProcessInstance(processInstanceKey, cancelRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::cancelProcessInstance);
  }

  @CamundaPostMapping(path = "/{processInstanceKey}/migration")
  public CompletableFuture<ResponseEntity<Object>> migrateProcessInstance(
      @PathVariable final long processInstanceKey,
      @RequestBody final ProcessInstanceMigrationInstruction migrationRequest) {
    return RequestMapper.toMigrateProcessInstance(processInstanceKey, migrationRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::migrateProcessInstance);
  }

  @CamundaPostMapping(path = "/{processInstanceKey}/modification")
  public CompletableFuture<ResponseEntity<Object>> modifyProcessInstance(
      @PathVariable final long processInstanceKey,
      @RequestBody final ProcessInstanceModificationInstruction modifyRequest) {
    return RequestMapper.toModifyProcessInstance(processInstanceKey, modifyRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::modifyProcessInstance);
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{processInstanceKey}/incident-resolution")
  public CompletableFuture<ResponseEntity<Object>> resolveProcessInstanceIncidents(
      @PathVariable final long processInstanceKey) {
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .resolveProcessInstanceIncidents(processInstanceKey),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search")
  public ResponseEntity<ProcessInstanceSearchQueryResult> searchProcessInstances(
      @RequestBody(required = false) final ProcessInstanceSearchQuery query) {
    return SearchQueryRequestMapper.toProcessInstanceQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{processInstanceKey}")
  public ResponseEntity<Object> getByKey(
      @PathVariable("processInstanceKey") final Long processInstanceKey) {
    try {
      // Success case: Return the left side with the ProcessInstanceItem wrapped in ResponseEntity
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toProcessInstance(
                  processInstanceServices
                      .withAuthentication(authenticationProvider.getCamundaAuthentication())
                      .getByKey(processInstanceKey)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{processInstanceKey}/deletion")
  public CompletableFuture<ResponseEntity<Object>> deleteProcessInstance(
      @PathVariable("processInstanceKey") final Long processInstanceKey,
      @RequestBody(required = false) final DeleteProcessInstanceRequest request) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            processInstanceServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .deleteProcessInstance(
                    processInstanceKey,
                    Objects.nonNull(request) ? request.getOperationReference() : null));
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{processInstanceKey}/call-hierarchy")
  public ResponseEntity<Object> getCallHierarchy(
      @PathVariable("processInstanceKey") final Long processInstanceKey) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toProcessInstanceCallHierarchyEntries(
                  processInstanceServices
                      .withAuthentication(authenticationProvider.getCamundaAuthentication())
                      .callHierarchy(processInstanceKey)));

    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{processInstanceKey}/statistics/element-instances")
  public ResponseEntity<Object> elementStatistics(
      @PathVariable("processInstanceKey") final Long processInstanceKey) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toProcessInstanceElementStatisticsResult(
                  processInstanceServices
                      .withAuthentication(authenticationProvider.getCamundaAuthentication())
                      .elementStatistics(processInstanceKey)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{processInstanceKey}/sequence-flows")
  public ResponseEntity<Object> sequenceFlows(
      @PathVariable("processInstanceKey") final Long processInstanceKey) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toSequenceFlowsResult(
                  processInstanceServices
                      .withAuthentication(authenticationProvider.getCamundaAuthentication())
                      .sequenceFlows(processInstanceKey)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/cancellation")
  public CompletableFuture<ResponseEntity<Object>> cancelProcessInstancesBatchOperation(
      @RequestBody final ProcessInstanceCancellationBatchOperationRequest request) {
    return RequestMapper.toRequiredProcessInstanceFilter(request.getFilter())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::batchOperationCancellation);
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/incident-resolution")
  public CompletableFuture<ResponseEntity<Object>> resolveIncidentsBatchOperation(
      @RequestBody final ProcessInstanceIncidentResolutionBatchOperationRequest request) {
    return RequestMapper.toRequiredProcessInstanceFilter(request.getFilter())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::batchOperationResolveIncidents);
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/migration")
  public CompletableFuture<ResponseEntity<Object>> migrateProcessInstancesBatchOperation(
      @RequestBody final ProcessInstanceMigrationBatchOperationRequest request) {
    return RequestMapper.toProcessInstanceMigrationBatchOperationRequest(request)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::batchOperationMigrate);
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/modification")
  public CompletableFuture<ResponseEntity<Object>> modifyProcessInstancesBatchOperation(
      @RequestBody final ProcessInstanceModificationBatchOperationRequest request) {
    return RequestMapper.toProcessInstanceModifyBatchOperationRequest(request)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::batchOperationModify);
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/deletion")
  public CompletableFuture<ResponseEntity<Object>> deleteProcessInstancesBatchOperation(
      @RequestBody final ProcessInstanceDeletionBatchOperationRequest request) {
    return RequestMapper.toRequiredProcessInstanceFilter(request.getFilter())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::batchOperationDeletion);
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/{processInstanceKey}/incidents/search")
  public ResponseEntity<IncidentSearchQueryResult> searchIncidents(
      @PathVariable("processInstanceKey") final long processInstanceKey,
      @RequestBody(required = false) final IncidentSearchQuery query) {
    return SearchQueryRequestMapper.toIncidentQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            incidentQuery -> searchIncidents(processInstanceKey, incidentQuery));
  }

  private ResponseEntity<ProcessInstanceSearchQueryResult> search(
      final ProcessInstanceQuery query) {
    try {
      final var result =
          processInstanceServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .search(query);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toProcessInstanceSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<IncidentSearchQueryResult> searchIncidents(
      final long processInstanceKey, final IncidentQuery query) {
    try {
      final var result =
          processInstanceServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .searchIncidents(processInstanceKey, query);
      return ResponseEntity.ok(SearchQueryResponseMapper.toIncidentSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private CompletableFuture<ResponseEntity<Object>> batchOperationCancellation(
      final io.camunda.search.filter.ProcessInstanceFilter filter) {
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .cancelProcessInstanceBatchOperationWithResult(filter),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> batchOperationResolveIncidents(
      final io.camunda.search.filter.ProcessInstanceFilter filter) {
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .resolveIncidentsBatchOperationWithResult(filter),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> batchOperationMigrate(
      final ProcessInstanceMigrateBatchOperationRequest request) {
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .migrateProcessInstancesBatchOperation(request),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> batchOperationModify(
      final ProcessInstanceModifyBatchOperationRequest request) {
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .modifyProcessInstancesBatchOperation(request),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> batchOperationDeletion(
      final io.camunda.search.filter.ProcessInstanceFilter filter) {
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .deleteProcessInstancesBatchOperation(filter),
        ResponseMapper::toBatchOperationCreatedWithResultResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> createProcessInstance(
      final ProcessInstanceCreateRequest request) {
    if (request.awaitCompletion()) {
      return RequestExecutor.executeServiceMethod(
          () ->
              processInstanceServices
                  .withAuthentication(authenticationProvider.getCamundaAuthentication())
                  .createProcessInstanceWithResult(request),
          ResponseMapper::toCreateProcessInstanceWithResultResponse,
          HttpStatus.OK);
    }
    return RequestExecutor.executeServiceMethod(
        () ->
            processInstanceServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .createProcessInstance(request),
        ResponseMapper::toCreateProcessInstanceResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> cancelProcessInstance(
      final ProcessInstanceCancelRequest request) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            processInstanceServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .cancelProcessInstance(request));
  }

  private CompletableFuture<ResponseEntity<Object>> migrateProcessInstance(
      final ProcessInstanceMigrateRequest request) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            processInstanceServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .migrateProcessInstance(request));
  }

  private CompletableFuture<ResponseEntity<Object>> modifyProcessInstance(
      final ProcessInstanceModifyRequest request) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            processInstanceServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .modifyProcessInstance(request));
  }
}
