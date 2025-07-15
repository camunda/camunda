/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.RestErrorMapper.mapErrorToResponse;

import io.camunda.search.query.BatchOperationQuery;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.BatchOperationServices;
import io.camunda.zeebe.gateway.protocol.rest.BatchOperationResponse;
import io.camunda.zeebe.gateway.protocol.rest.BatchOperationSearchQuery;
import io.camunda.zeebe.gateway.protocol.rest.BatchOperationSearchQueryResult;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPutMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

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
      @PathVariable("batchOperationKey") final String batchOperationKey) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toBatchOperation(
                  batchOperationServices
                      .withAuthentication(authenticationProvider.getCamundaAuthentication())
                      .getById(batchOperationKey)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<BatchOperationSearchQueryResult> searchBatchOperations(
      @RequestBody(required = false) final BatchOperationSearchQuery query) {
    return SearchQueryRequestMapper.toBatchOperationQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  @CamundaPutMapping(path = "/{batchOperationKey}/cancellation")
  public ResponseEntity<Object> cancelBatchOperation(@PathVariable final String batchOperationKey) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
            () ->
                batchOperationServices
                    .withAuthentication(authenticationProvider.getCamundaAuthentication())
                    .cancel(batchOperationKey))
        .join();
  }

  @CamundaPutMapping(path = "/{batchOperationKey}/suspension")
  public ResponseEntity<Object> suspendBatchOperation(
      @PathVariable final String batchOperationKey) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
            () ->
                batchOperationServices
                    .withAuthentication(authenticationProvider.getCamundaAuthentication())
                    .suspend(batchOperationKey))
        .join();
  }

  @CamundaPutMapping(path = "/{batchOperationKey}/resumption")
  public ResponseEntity<Object> resumeBatchOperation(@PathVariable final String batchOperationKey) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
            () ->
                batchOperationServices
                    .withAuthentication(authenticationProvider.getCamundaAuthentication())
                    .resume(batchOperationKey))
        .join();
  }

  private ResponseEntity<BatchOperationSearchQueryResult> search(final BatchOperationQuery query) {
    try {
      final var result =
          batchOperationServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .search(query);
      return ResponseEntity.ok(SearchQueryResponseMapper.toBatchOperationSearchQueryResult(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
