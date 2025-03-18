/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.search.query.SearchQueryBuilders.batchOperationQuery;
import static io.camunda.zeebe.gateway.rest.RestErrorMapper.mapErrorToResponse;

import io.camunda.service.BatchOperationServices;
import io.camunda.zeebe.gateway.protocol.rest.BatchOperationItemSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.BatchOperationResponse;
import io.camunda.zeebe.gateway.protocol.rest.BatchOperationSearchQueryResult;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPutMapping;
import java.util.concurrent.ExecutionException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/batch-operations")
public class BatchOperationController {

  private final BatchOperationServices batchOperationServices;

  public BatchOperationController(final BatchOperationServices batchOperationServices) {
    this.batchOperationServices = batchOperationServices;
  }

  @CamundaGetMapping(path = "/{batchOperationKey}")
  public ResponseEntity<BatchOperationResponse> getByKey(
      @PathVariable("batchOperationKey") final Long batchOperationKey) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toBatchOperation(
                  batchOperationServices
                      .withAuthentication(RequestMapper.getAuthentication())
                      .getByKey(batchOperationKey)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @CamundaGetMapping(path = "/{batchOperationKey}/items")
  public ResponseEntity<BatchOperationItemSearchQueryResult> getItemsByKey(
      @PathVariable("batchOperationKey") final Long batchOperationKey) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toBatchOperationItemSearchQueryResult(
                  batchOperationServices
                      .withAuthentication(RequestMapper.getAuthentication())
                      .getItemsByKey(batchOperationKey)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @CamundaGetMapping(path = "/search")
  public ResponseEntity<BatchOperationSearchQueryResult> searchBatchOperations() {
    return search();
  }

  @CamundaPutMapping(path = "/{key}/cancel")
  public ResponseEntity<Object> cancelBatchOperation(@PathVariable final long key) {
    // TODO better return value
    try {
      final var result =
          batchOperationServices
              .withAuthentication(RequestMapper.getAuthentication())
              .cancel(key)
              .get();
    } catch (final InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }

    return ResponseEntity.noContent().build();
  }

  @CamundaPutMapping(path = "/{key}/pause")
  public ResponseEntity<Object> PauseBatchOperation(@PathVariable final long key) {
    // TODO better return value
    try {
      final var result =
          batchOperationServices
              .withAuthentication(RequestMapper.getAuthentication())
              .pause(key)
              .get();
    } catch (final InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }

    return ResponseEntity.noContent().build();
  }

  @CamundaPutMapping(path = "/{key}/resume")
  public ResponseEntity<Object> resumeBatchOperation(@PathVariable final long key) {
    // TODO better return value
    try {
      final var result =
          batchOperationServices
              .withAuthentication(RequestMapper.getAuthentication())
              .resume(key)
              .get();
    } catch (final InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }

    return ResponseEntity.noContent().build();
  }

  private ResponseEntity<BatchOperationSearchQueryResult> search() {
    try {
      // TODO use query
      final var result = batchOperationServices.search(batchOperationQuery().build());
      return ResponseEntity.ok(SearchQueryResponseMapper.toBatchOperationSearchQueryResult(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
