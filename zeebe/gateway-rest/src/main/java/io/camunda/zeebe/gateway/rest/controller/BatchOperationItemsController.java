/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.protocol.model.BatchOperationItemSearchQuery;
import io.camunda.gateway.protocol.model.BatchOperationItemSearchQueryResult;
import io.camunda.search.query.BatchOperationItemQuery;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.BatchOperationServices;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.mapper.search.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.mapper.search.SearchQueryResponseMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequiresSecondaryStorage
@RequestMapping("/v2/batch-operation-items")
public class BatchOperationItemsController {

  private final BatchOperationServices batchOperationServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public BatchOperationItemsController(
      final BatchOperationServices batchOperationServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.batchOperationServices = batchOperationServices;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<BatchOperationItemSearchQueryResult> searchBatchOperationItems(
      @RequestBody(required = false) final BatchOperationItemSearchQuery query) {
    return SearchQueryRequestMapper.toBatchOperationItemQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  private ResponseEntity<BatchOperationItemSearchQueryResult> search(
      final BatchOperationItemQuery query) {
    try {
      final var result =
          batchOperationServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .searchItems(query);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toBatchOperationItemSearchQueryResult(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
