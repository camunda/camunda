/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.adapter;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.protocol.model.DecisionInstanceDeletionBatchOperationRequest;
import io.camunda.gateway.protocol.model.DecisionInstanceSearchQuery;
import io.camunda.gateway.protocol.model.DeleteDecisionInstanceRequest;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.DecisionInstanceServices;
import io.camunda.zeebe.gateway.rest.controller.generated.DecisionInstanceServiceAdapter;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DefaultDecisionInstanceServiceAdapter implements DecisionInstanceServiceAdapter {

  private final DecisionInstanceServices decisionInstanceServices;

  public DefaultDecisionInstanceServiceAdapter(
      final DecisionInstanceServices decisionInstanceServices) {
    this.decisionInstanceServices = decisionInstanceServices;
  }

  @Override
  public ResponseEntity<Object> searchDecisionInstances(
      final DecisionInstanceSearchQuery queryStrict, final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toDecisionInstanceQueryStrict(queryStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            q -> {
              try {
                final var result = decisionInstanceServices.search(q, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toDecisionInstanceSearchQueryResponse(result));
              } catch (final Exception e) {
                return mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> getDecisionInstance(
      final String decisionEvaluationInstanceKey, final CamundaAuthentication authentication) {
    try {
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toDecisionInstanceGetQueryResponse(
              decisionInstanceServices.getById(decisionEvaluationInstanceKey, authentication)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @Override
  public ResponseEntity<Void> deleteDecisionInstance(
      final Long decisionInstanceKey,
      final DeleteDecisionInstanceRequest requestStrict,
      final CamundaAuthentication authentication) {
    return RequestExecutor.executeSync(
        () ->
            decisionInstanceServices.deleteDecisionInstance(
                decisionInstanceKey,
                Objects.nonNull(requestStrict)
                    ? requestStrict.getOperationReference().orElse(null)
                    : null,
                authentication));
  }

  @Override
  public ResponseEntity<Object> deleteDecisionInstancesBatchOperation(
      final DecisionInstanceDeletionBatchOperationRequest requestStrict,
      final CamundaAuthentication authentication) {
    return RequestMapper.toRequiredDecisionInstanceFilter(requestStrict.getFilter())
        .fold(
            RestErrorMapper::mapProblemToResponse,
            filter ->
                RequestExecutor.executeSync(
                    () ->
                        decisionInstanceServices.deleteDecisionInstancesBatchOperation(
                            filter, authentication),
                    ResponseMapper::toBatchOperationCreatedWithResultResponse,
                    HttpStatus.OK));
  }
}
