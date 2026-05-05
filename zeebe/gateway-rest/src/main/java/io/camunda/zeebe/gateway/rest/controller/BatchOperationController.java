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
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.BatchOperationServices;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenant;
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

  private final BatchOperationServices batchOperationServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public BatchOperationController(
      final BatchOperationServices batchOperationServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.batchOperationServices = batchOperationServices;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaGetMapping(path = "/{batchOperationKey}")
  public ResponseEntity<BatchOperationResponse> getById(
      @PathVariable("batchOperationKey") final String batchOperationKey,
      @PhysicalTenant final String physicalTenantId) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toBatchOperation(
                  batchOperationServices.getById(
                      batchOperationKey, authentication, physicalTenantId)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<BatchOperationSearchQueryResult> searchBatchOperations(
      @RequestBody(required = false) final BatchOperationSearchQuery query,
      @PhysicalTenant final String physicalTenantId) {
    return SearchQueryRequestMapper.toBatchOperationQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, q -> search(q, physicalTenantId));
  }

  @CamundaPostMapping(
      path = "/{batchOperationKey}/cancellation",
      consumes = {})
  public ResponseEntity<Object> cancelBatchOperation(
      @PathVariable final String batchOperationKey, @PhysicalTenant final String physicalTenantId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
            () ->
                batchOperationServices.cancel(batchOperationKey, authentication, physicalTenantId))
        .join();
  }

  @CamundaPostMapping(
      path = "/{batchOperationKey}/suspension",
      consumes = {})
  public ResponseEntity<Object> suspendBatchOperation(
      @PathVariable final String batchOperationKey, @PhysicalTenant final String physicalTenantId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
            () ->
                batchOperationServices.suspend(batchOperationKey, authentication, physicalTenantId))
        .join();
  }

  @CamundaPostMapping(
      path = "/{batchOperationKey}/resumption",
      consumes = {})
  public ResponseEntity<Object> resumeBatchOperation(
      @PathVariable final String batchOperationKey, @PhysicalTenant final String physicalTenantId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
            () ->
                batchOperationServices.resume(batchOperationKey, authentication, physicalTenantId))
        .join();
  }

  private ResponseEntity<BatchOperationSearchQueryResult> search(
      final BatchOperationQuery query, final String physicalTenantId) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result = batchOperationServices.search(query, authentication, physicalTenantId);
      return ResponseEntity.ok(SearchQueryResponseMapper.toBatchOperationSearchQueryResult(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
