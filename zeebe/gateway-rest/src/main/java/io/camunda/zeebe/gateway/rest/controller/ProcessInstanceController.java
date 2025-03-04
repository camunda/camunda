/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.RestErrorMapper.mapErrorToResponse;

import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCancelRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCreateRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceMigrateRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceModifyRequest;
import io.camunda.zeebe.gateway.protocol.rest.CancelProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceCreationInstruction;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceFilter;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceMigrationInstruction;
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

  public ProcessInstanceController(
      final ProcessInstanceServices processInstanceServices,
      final MultiTenancyConfiguration multiTenancyCfg) {
    this.processInstanceServices = processInstanceServices;
    this.multiTenancyCfg = multiTenancyCfg;
  }

  @CamundaPostMapping
  public CompletableFuture<ResponseEntity<Object>> createProcessInstance(
      @RequestBody final ProcessInstanceCreationInstruction request) {
    return RequestMapper.toCreateProcessInstance(request, multiTenancyCfg.isEnabled())
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
                      .withAuthentication(RequestMapper.getAuthentication())
                      .getByKey(processInstanceKey)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @CamundaPostMapping(path = "/batch-operations/cancellation")
  public CompletableFuture<ResponseEntity<Object>> cancelProcessInstanceBatchOperation(
      @RequestBody(required = false) final ProcessInstanceFilter filter) {

    // TODO with Either and ProblemDetail
    return batchOperationCancellation(
        SearchQueryRequestMapper.toProcessInstanceFilter(filter));
  }

  private ResponseEntity<ProcessInstanceSearchQueryResult> search(
      final ProcessInstanceQuery query) {
    try {
      final var result =
          processInstanceServices
              .withAuthentication(RequestMapper.getAuthentication())
              .search(query);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toProcessInstanceSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private CompletableFuture<ResponseEntity<Object>> batchOperationCancellation(
      final io.camunda.search.filter.ProcessInstanceFilter filter) {
    return RequestMapper.executeServiceMethod(
        () ->
            processInstanceServices
                .withAuthentication(RequestMapper.getAuthentication())
                .cancelProcessInstanceBatchOperationWithResult(filter),
        ResponseMapper::toCancelProcessInstanceBatchOperationWithResultResponse); // TODO better response
  }

  private CompletableFuture<ResponseEntity<Object>> createProcessInstance(
      final ProcessInstanceCreateRequest request) {
    if (request.awaitCompletion()) {
      return RequestMapper.executeServiceMethod(
          () ->
              processInstanceServices
                  .withAuthentication(RequestMapper.getAuthentication())
                  .createProcessInstanceWithResult(request),
          ResponseMapper::toCreateProcessInstanceWithResultResponse);
    }
    return RequestMapper.executeServiceMethod(
        () ->
            processInstanceServices
                .withAuthentication(RequestMapper.getAuthentication())
                .createProcessInstance(request),
        ResponseMapper::toCreateProcessInstanceResponse);
  }

  private CompletableFuture<ResponseEntity<Object>> cancelProcessInstance(
      final ProcessInstanceCancelRequest request) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            processInstanceServices
                .withAuthentication(RequestMapper.getAuthentication())
                .cancelProcessInstance(request));
  }

  private CompletableFuture<ResponseEntity<Object>> migrateProcessInstance(
      final ProcessInstanceMigrateRequest request) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            processInstanceServices
                .withAuthentication(RequestMapper.getAuthentication())
                .migrateProcessInstance(request));
  }

  private CompletableFuture<ResponseEntity<Object>> modifyProcessInstance(
      final ProcessInstanceModifyRequest request) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            processInstanceServices
                .withAuthentication(RequestMapper.getAuthentication())
                .modifyProcessInstance(request));
  }
}
