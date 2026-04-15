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
import io.camunda.gateway.mapping.http.search.contract.generated.IncidentProcessInstanceStatisticsByDefinitionQuerySearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.IncidentProcessInstanceStatisticsByErrorQueryContract;
import io.camunda.gateway.mapping.http.search.contract.generated.IncidentResolutionRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.IncidentSearchQueryRequestContract;
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
      final IncidentSearchQueryRequestContract incidentSearchQueryStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toIncidentQueryStrict(incidentSearchQueryStrict)
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
      final IncidentResolutionRequestContract incidentResolutionRequestStrict,
      final CamundaAuthentication authentication) {
    return RequestExecutor.executeSync(
        () ->
            incidentServices.resolveIncident(
                incidentKey,
                incidentResolutionRequestStrict == null
                    ? null
                    : incidentResolutionRequestStrict.operationReference(),
                authentication));
  }

  @Override
  public ResponseEntity<Object> getProcessInstanceStatisticsByError(
      final IncidentProcessInstanceStatisticsByErrorQueryContract
          incidentProcessInstanceStatisticsByErrorQueryStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toIncidentProcessInstanceStatisticsByErrorQuery(
            incidentProcessInstanceStatisticsByErrorQueryStrict)
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
      final IncidentProcessInstanceStatisticsByDefinitionQuerySearchQueryRequestContract
          incidentProcessInstanceStatisticsByDefinitionQueryStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toIncidentProcessInstanceStatisticsByDefinitionQuery(
            incidentProcessInstanceStatisticsByDefinitionQueryStrict)
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
