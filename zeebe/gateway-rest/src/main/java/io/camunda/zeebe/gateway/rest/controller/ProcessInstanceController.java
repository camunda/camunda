/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.RestErrorMapper.mapErrorToResponse;

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
import io.camunda.zeebe.gateway.protocol.rest.CancelProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.rest.IncidentSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceCreationInstruction;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceFilter;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceIncidentSearchQuery;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceMigrationBatchOperationRequest;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceMigrationInstruction;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceModificationBatchOperationRequest;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceModificationInstruction;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceSearchQuery;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceSearchQueryResult;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.ResponseMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.validator.RequestValidator;
import java.util.concurrent.CompletableFuture;
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

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<ProcessInstanceSearchQueryResult> searchProcessInstances(
      @RequestBody(required = false) final ProcessInstanceSearchQuery query) {
    return SearchQueryRequestMapper.toProcessInstanceQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

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

  @CamundaPostMapping(path = "/cancellation")
  public CompletableFuture<ResponseEntity<Object>> cancelProcessInstancesBatchOperation(
      @RequestBody(required = false) final ProcessInstanceFilter filter) {

    return SearchQueryRequestMapper.toProcessInstanceFilter(filter)
        .fold(
            (errors) ->
                RestErrorMapper.mapProblemToCompletedResponse(
                    RequestValidator.createProblemDetail(errors).get()),
            this::batchOperationCancellation);
  }

  @CamundaPostMapping(path = "/incident-resolution")
  public CompletableFuture<ResponseEntity<Object>> resolveIncidentsBatchOperation(
      @RequestBody(required = false) final ProcessInstanceFilter filter) {

    return SearchQueryRequestMapper.toProcessInstanceFilter(filter)
        .fold(
            (errors) ->
                RestErrorMapper.mapProblemToCompletedResponse(
                    RequestValidator.createProblemDetail(errors).get()),
            this::batchOperationResolveIncidents);
  }

  @CamundaPostMapping(path = "/migration")
  public CompletableFuture<ResponseEntity<Object>> migrateProcessInstancesBatchOperation(
      @RequestBody final ProcessInstanceMigrationBatchOperationRequest request) {
    return RequestMapper.toProcessInstanceMigrationBatchOperationRequest(request)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::batchOperationModify);
  }

  @CamundaPostMapping(path = "/modification")
  public CompletableFuture<ResponseEntity<Object>> modifyProcessInstancesBatchOperation(
      @RequestBody final ProcessInstanceModificationBatchOperationRequest request) {
    return RequestMapper.toProcessInstanceModifyBatchOperationRequest(request)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::batchOperationModify);
  }

  @CamundaPostMapping(path = "/{processInstanceKey}/incidents/search")
  public ResponseEntity<IncidentSearchQueryResult> searchIncidents(
      @PathVariable("processInstanceKey") final long processInstanceKey,
      @RequestBody(required = false) final ProcessInstanceIncidentSearchQuery query) {
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
    return RequestMapper.executeServiceMethod(
        () ->
            processInstanceServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .cancelProcessInstanceBatchOperationWithResult(filter),
        ResponseMapper::toBatchOperationCreatedWithResultResponse);
  }

  private CompletableFuture<ResponseEntity<Object>> batchOperationResolveIncidents(
      final io.camunda.search.filter.ProcessInstanceFilter filter) {
    return RequestMapper.executeServiceMethod(
        () ->
            processInstanceServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .resolveIncidentsBatchOperationWithResult(filter),
        ResponseMapper::toBatchOperationCreatedWithResultResponse);
  }

  private CompletableFuture<ResponseEntity<Object>> batchOperationModify(
      final ProcessInstanceMigrateBatchOperationRequest request) {
    return RequestMapper.executeServiceMethod(
        () ->
            processInstanceServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .migrateProcessInstancesBatchOperation(request),
        ResponseMapper::toBatchOperationCreatedWithResultResponse);
  }

  private CompletableFuture<ResponseEntity<Object>> batchOperationModify(
      final ProcessInstanceModifyBatchOperationRequest request) {
    return RequestMapper.executeServiceMethod(
        () ->
            processInstanceServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .modifyProcessInstancesBatchOperation(request),
        ResponseMapper::toBatchOperationCreatedWithResultResponse);
  }

  private CompletableFuture<ResponseEntity<Object>> createProcessInstance(
      final ProcessInstanceCreateRequest request) {
    if (request.awaitCompletion()) {
      return RequestMapper.executeServiceMethod(
          () ->
              processInstanceServices
                  .withAuthentication(authenticationProvider.getCamundaAuthentication())
                  .createProcessInstanceWithResult(request),
          ResponseMapper::toCreateProcessInstanceWithResultResponse);
    }
    return RequestMapper.executeServiceMethod(
        () ->
            processInstanceServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .createProcessInstance(request),
        ResponseMapper::toCreateProcessInstanceResponse);
  }

  private CompletableFuture<ResponseEntity<Object>> cancelProcessInstance(
      final ProcessInstanceCancelRequest request) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            processInstanceServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .cancelProcessInstance(request));
  }

  private CompletableFuture<ResponseEntity<Object>> migrateProcessInstance(
      final ProcessInstanceMigrateRequest request) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            processInstanceServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .migrateProcessInstance(request));
  }

  private CompletableFuture<ResponseEntity<Object>> modifyProcessInstance(
      final ProcessInstanceModifyRequest request) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            processInstanceServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .modifyProcessInstance(request));
  }
}
