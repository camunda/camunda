/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.adapter;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.BatchOperationItemSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.BatchOperationSearchQueryRequestContract;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.BatchOperationServices;
import io.camunda.zeebe.gateway.rest.controller.generated.BatchOperationServiceAdapter;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DefaultBatchOperationServiceAdapter implements BatchOperationServiceAdapter {

  private final BatchOperationServices batchOperationServices;

  public DefaultBatchOperationServiceAdapter(final BatchOperationServices batchOperationServices) {
    this.batchOperationServices = batchOperationServices;
  }

  @Override
  public ResponseEntity<Object> getBatchOperation(
      final String batchOperationKey, final CamundaAuthentication authentication) {
    try {
      final var result = batchOperationServices.getById(batchOperationKey, authentication);
      return ResponseEntity.ok(SearchQueryResponseMapper.toBatchOperation(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @Override
  public ResponseEntity<Object> searchBatchOperations(
      final BatchOperationSearchQueryRequestContract batchOperationSearchQueryStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toBatchOperationQueryStrict(batchOperationSearchQueryStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result = batchOperationServices.search(query, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toBatchOperationSearchQueryResult(result));
              } catch (final Exception e) {
                return mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Void> cancelBatchOperation(
      final String batchOperationKey, final CamundaAuthentication authentication) {
    return RequestExecutor.executeSync(
        () -> batchOperationServices.cancel(batchOperationKey, authentication));
  }

  @Override
  public ResponseEntity<Void> suspendBatchOperation(
      final String batchOperationKey, final CamundaAuthentication authentication) {
    return RequestExecutor.executeSync(
        () -> batchOperationServices.suspend(batchOperationKey, authentication));
  }

  @Override
  public ResponseEntity<Void> resumeBatchOperation(
      final String batchOperationKey, final CamundaAuthentication authentication) {
    return RequestExecutor.executeSync(
        () -> batchOperationServices.resume(batchOperationKey, authentication));
  }

  @Override
  public ResponseEntity<Object> searchBatchOperationItems(
      final BatchOperationItemSearchQueryRequestContract batchOperationItemSearchQueryStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toBatchOperationItemQueryStrict(
            batchOperationItemSearchQueryStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result = batchOperationServices.searchItems(query, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toBatchOperationItemSearchQueryResult(result));
              } catch (final Exception e) {
                return mapErrorToResponse(e);
              }
            });
  }
}
