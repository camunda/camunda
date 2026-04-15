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
import io.camunda.gateway.mapping.http.search.contract.generated.ElementInstanceSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.IncidentSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.SetVariableRequestContract;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.ElementInstanceServices;
import io.camunda.zeebe.gateway.rest.controller.generated.ElementInstanceServiceAdapter;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DefaultElementInstanceServiceAdapter implements ElementInstanceServiceAdapter {

  private final ElementInstanceServices elementInstanceServices;

  public DefaultElementInstanceServiceAdapter(
      final ElementInstanceServices elementInstanceServices) {
    this.elementInstanceServices = elementInstanceServices;
  }

  @Override
  public ResponseEntity<Object> searchElementInstances(
      final ElementInstanceSearchQueryRequestContract elementInstanceSearchQueryStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toElementInstanceQueryStrict(elementInstanceSearchQueryStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result = elementInstanceServices.search(query, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toElementInstanceSearchQueryResponse(result));
              } catch (final Exception e) {
                return mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> getElementInstance(
      final Long elementInstanceKey, final CamundaAuthentication authentication) {
    try {
      final var result = elementInstanceServices.getByKey(elementInstanceKey, authentication);
      return ResponseEntity.ok(SearchQueryResponseMapper.toElementInstance(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @Override
  public ResponseEntity<Void> createElementInstanceVariables(
      final Long elementInstanceKey,
      final SetVariableRequestContract setVariableRequestStrict,
      final CamundaAuthentication authentication) {
    return RequestExecutor.executeSync(
        () ->
            elementInstanceServices.setVariables(
                new ElementInstanceServices.SetVariablesRequest(
                    elementInstanceKey,
                    setVariableRequestStrict.variables(),
                    setVariableRequestStrict.local(),
                    setVariableRequestStrict.operationReference()),
                authentication));
  }

  @Override
  public ResponseEntity<Object> searchElementInstanceIncidents(
      final Long elementInstanceKey,
      final IncidentSearchQueryRequestContract incidentSearchQueryStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toIncidentQueryStrict(incidentSearchQueryStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result =
                    elementInstanceServices.searchIncidents(
                        elementInstanceKey, query, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toIncidentSearchQueryResponse(result));
              } catch (final Exception e) {
                return mapErrorToResponse(e);
              }
            });
  }
}
