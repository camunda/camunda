/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.RestErrorMapper.mapErrorToResponse;

import io.camunda.search.query.BatchOperationItemQuery;
import io.camunda.service.BatchOperationServices;
import io.camunda.zeebe.gateway.protocol.rest.BatchOperationItemSearchQuery;
import io.camunda.zeebe.gateway.protocol.rest.BatchOperationItemSearchQueryResult;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/batch-operation-items")
public class BatchOperationItemsController {

  private final BatchOperationServices batchOperationServices;

  public BatchOperationItemsController(final BatchOperationServices batchOperationServices) {
    this.batchOperationServices = batchOperationServices;
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
              .withAuthentication(RequestMapper.getAuthentication())
              .searchItems(query);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toBatchOperationItemSearchQueryResult(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
