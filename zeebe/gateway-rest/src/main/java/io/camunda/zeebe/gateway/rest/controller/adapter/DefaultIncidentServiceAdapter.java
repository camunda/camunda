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
import io.camunda.gateway.protocol.model.IncidentProcessInstanceStatisticsByDefinitionQuery;
import io.camunda.gateway.protocol.model.IncidentProcessInstanceStatisticsByErrorQuery;
import io.camunda.gateway.protocol.model.IncidentResolutionRequest;
import io.camunda.gateway.protocol.model.IncidentSearchQuery;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.IncidentServices;
import io.camunda.zeebe.gateway.rest.controller.generated.IncidentServiceAdapter;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DefaultIncidentServiceAdapter implements IncidentServiceAdapter {

  private final IncidentServices incidentServices;

  public DefaultIncidentServiceAdapter(final IncidentServices incidentServices) {
    this.incidentServices = incidentServices;
  }

  @Override
  public ResponseEntity<Object> searchIncidents(
      final IncidentSearchQuery incidentSearchQuery, final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toIncidentQuery(incidentSearchQuery)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result = incidentServices.search(query, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toIncidentSearchQueryResponse(result));
              } catch (final Exception e) {
                return mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> getIncident(
      final Long incidentKey, final CamundaAuthentication authentication) {
    try {
      final var result = incidentServices.getByKey(incidentKey, authentication);
      return ResponseEntity.ok(SearchQueryResponseMapper.toIncident(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @Override
  public ResponseEntity<Void> resolveIncident(
      final Long incidentKey,
      final IncidentResolutionRequest incidentResolutionRequest,
      final CamundaAuthentication authentication) {
    return RequestExecutor.executeSync(
        () ->
            incidentServices.resolveIncident(
                incidentKey,
                incidentResolutionRequest == null
                    ? null
                    : incidentResolutionRequest.getOperationReference().orElse(null),
                authentication));
  }

  @Override
  public ResponseEntity<Object> getProcessInstanceStatisticsByError(
      final IncidentProcessInstanceStatisticsByErrorQuery
          incidentProcessInstanceStatisticsByErrorQuery,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toIncidentProcessInstanceStatisticsByErrorQuery(
            incidentProcessInstanceStatisticsByErrorQuery)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result =
                    incidentServices.incidentProcessInstanceStatisticsByError(
                        query, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toIncidentProcessInstanceStatisticsByErrorResult(
                        result));
              } catch (final Exception e) {
                return mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> getProcessInstanceStatisticsByDefinition(
      final IncidentProcessInstanceStatisticsByDefinitionQuery
          incidentProcessInstanceStatisticsByDefinitionQuery,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toIncidentProcessInstanceStatisticsByDefinitionQuery(
            incidentProcessInstanceStatisticsByDefinitionQuery)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result =
                    incidentServices.searchIncidentProcessInstanceStatisticsByDefinition(
                        query, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper
                        .toIncidentProcessInstanceStatisticsByDefinitionQueryResult(result));
              } catch (final Exception e) {
                return mapErrorToResponse(e);
              }
            });
  }
}
