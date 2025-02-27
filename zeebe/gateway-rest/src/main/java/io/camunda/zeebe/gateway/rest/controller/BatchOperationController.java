/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.RestErrorMapper.mapErrorToResponse;

import io.camunda.service.BatchOperationServices;
import io.camunda.zeebe.gateway.protocol.rest.BatchOperationSearchQueryResult;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/batch-operations")
public class BatchOperationController {

  private final BatchOperationServices batchOperationServices;

  public BatchOperationController(final BatchOperationServices batchOperationServices) {
    this.batchOperationServices = batchOperationServices;
  }

  @CamundaGetMapping(path = "/search")
  public ResponseEntity<BatchOperationSearchQueryResult> searchBatchOperations() {
    return search();
  }

  private ResponseEntity<BatchOperationSearchQueryResult> search() {
    try {
      final var result = batchOperationServices.search();
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toBatchOperationSearchQueryResult(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

}
