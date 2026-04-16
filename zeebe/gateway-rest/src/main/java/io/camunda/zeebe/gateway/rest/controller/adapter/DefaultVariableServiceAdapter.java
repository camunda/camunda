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
import io.camunda.gateway.protocol.model.VariableSearchQuery;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.VariableServices;
import io.camunda.zeebe.gateway.rest.controller.generated.VariableServiceAdapter;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DefaultVariableServiceAdapter implements VariableServiceAdapter {

  private final VariableServices variableServices;

  public DefaultVariableServiceAdapter(final VariableServices variableServices) {
    this.variableServices = variableServices;
  }

  @Override
  public ResponseEntity<Object> searchVariables(
      final Boolean truncateValues,
      final VariableSearchQuery queryStrict,
      final CamundaAuthentication authentication) {
    final boolean truncate = truncateValues == null || truncateValues;
    return SearchQueryRequestMapper.toVariableQueryStrict(queryStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            q -> {
              try {
                final var result = variableServices.search(q, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toVariableSearchQueryResponse(result, truncate));
              } catch (final Exception e) {
                return mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> getVariable(
      final Long variableKey, final CamundaAuthentication authentication) {
    try {
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toVariableItem(
              variableServices.getByKey(variableKey, authentication)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
