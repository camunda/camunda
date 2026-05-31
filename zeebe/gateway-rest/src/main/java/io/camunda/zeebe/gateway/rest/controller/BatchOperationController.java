/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.protocol.model.BatchOperationResponse;
import io.camunda.gateway.protocol.model.BatchOperationSearchQuery;
import io.camunda.gateway.protocol.model.BatchOperationSearchQueryResult;
import io.camunda.search.query.BatchOperationQuery;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.BatchOperationServices;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Batch operations are unavailable when secondary storage is disabled.
 *
 * <p>This is an intentional product decision for no-db mode, so the whole controller is guarded
 * with {@link RequiresSecondaryStorage}.
 *
 * <p>Although lifecycle endpoints such as cancel, suspend, and resume do not directly query
 * secondary storage in their own request path, the batch operation feature depends on secondary
 * storage overall. Without secondary storage, batch operations cannot be meaningfully created or
 * used, so all endpoints in this controller are disabled.
 */
@RequiresSecondaryStorage
@CamundaRestController
@RequestMapping("/v2/batch-operations")
public class BatchOperationController {

  private final ServiceRegistry serviceRegistry;
  private final CamundaAuthenticationProvider authenticationProvider;

  public BatchOperationController(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceRegistry = serviceRegistry;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaGetMapping(path = "/{batchOperationKey}")
  public ResponseEntity<BatchOperationResponse> getById(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("batchOperationKey") final String batchOperationKey) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toBatchOperation(
                  serviceRegistry
                      .batchOperationServices(physicalTenantId)
                      .getById(batchOperationKey, authentication)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<BatchOperationSearchQueryResult> searchBatchOperations(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody(required = false) final BatchOperationSearchQuery query) {
    return SearchQueryRequestMapper.toBatchOperationQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            q -> search(serviceRegistry.batchOperationServices(physicalTenantId), q));
  }

  @CamundaPostMapping(
      path = "/{batchOperationKey}/cancellation",
      consumes = {})
  public ResponseEntity<Object> cancelBatchOperation(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String batchOperationKey) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
            () ->
                serviceRegistry
                    .batchOperationServices(physicalTenantId)
                    .cancel(batchOperationKey, authentication))
        .join();
  }

  @CamundaPostMapping(
      path = "/{batchOperationKey}/suspension",
      consumes = {})
  public ResponseEntity<Object> suspendBatchOperation(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String batchOperationKey) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
            () ->
                serviceRegistry
                    .batchOperationServices(physicalTenantId)
                    .suspend(batchOperationKey, authentication))
        .join();
  }

  @CamundaPostMapping(
      path = "/{batchOperationKey}/resumption",
      consumes = {})
  public ResponseEntity<Object> resumeBatchOperation(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String batchOperationKey) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
            () ->
                serviceRegistry
                    .batchOperationServices(physicalTenantId)
                    .resume(batchOperationKey, authentication))
        .join();
  }

  private ResponseEntity<BatchOperationSearchQueryResult> search(
      final BatchOperationServices batchOperationServices, final BatchOperationQuery query) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result = batchOperationServices.search(query, authentication);
      return ResponseEntity.ok(SearchQueryResponseMapper.toBatchOperationSearchQueryResult(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
