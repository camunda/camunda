/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.GatewayErrorMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.protocol.api.IncidentApi;
import io.camunda.gateway.protocol.model.IncidentProcessInstanceStatisticsByDefinitionQuery;
import io.camunda.gateway.protocol.model.IncidentProcessInstanceStatisticsByDefinitionQueryResult;
import io.camunda.gateway.protocol.model.IncidentProcessInstanceStatisticsByErrorQuery;
import io.camunda.gateway.protocol.model.IncidentProcessInstanceStatisticsByErrorQueryResult;
import io.camunda.gateway.protocol.model.IncidentResolutionRequest;
import io.camunda.gateway.protocol.model.IncidentResult;
import io.camunda.gateway.protocol.model.IncidentSearchQuery;
import io.camunda.gateway.protocol.model.IncidentSearchQueryResult;
import io.camunda.search.query.IncidentQuery;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.IncidentServices;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import jakarta.validation.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2")
public class IncidentController implements IncidentApi {

  private final IncidentServices incidentServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public IncidentController(
      final IncidentServices incidentServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.incidentServices = incidentServices;
    this.authenticationProvider = authenticationProvider;
  }

  @Override
  public ResponseEntity<Void> resolveIncident(
      final String incidentKey,
      final IncidentResolutionRequest incidentResolutionRequest) {
    final Long operationReference =
        incidentResolutionRequest == null
            ? null
            : incidentResolutionRequest.getOperationReference();
    return RequestExecutor.executeSync(
        () ->
            incidentServices.resolveIncident(
                Long.parseLong(incidentKey),
                operationReference,
                authenticationProvider.getCamundaAuthentication()));
  }

  @Override
  @RequiresSecondaryStorage
  public ResponseEntity<IncidentSearchQueryResult> searchIncidents(
      final IncidentSearchQuery incidentSearchQuery) {
    return SearchQueryRequestMapper.toIncidentQuery(incidentSearchQuery)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  @Override
  @RequiresSecondaryStorage
  public ResponseEntity<IncidentResult> getIncident(final String incidentKey) {
    try {
      return responseOk(
          SearchQueryResponseMapper.toIncident(
              incidentServices.getByKey(
                  Long.parseLong(incidentKey),
                  authenticationProvider.getCamundaAuthentication())));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @Override
  @RequiresSecondaryStorage
  public ResponseEntity<IncidentProcessInstanceStatisticsByErrorQueryResult>
      getProcessInstanceStatisticsByError(
          final IncidentProcessInstanceStatisticsByErrorQuery
              incidentProcessInstanceStatisticsByErrorQuery) {
    return SearchQueryRequestMapper.toIncidentProcessInstanceStatisticsByErrorQuery(
            incidentProcessInstanceStatisticsByErrorQuery)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            this::executeIncidentProcessInstanceStatisticsByError);
  }

  @Override
  @RequiresSecondaryStorage
  public ResponseEntity<IncidentProcessInstanceStatisticsByDefinitionQueryResult>
      getProcessInstanceStatisticsByDefinition(
          final IncidentProcessInstanceStatisticsByDefinitionQuery
              incidentProcessInstanceStatisticsByDefinitionQuery) {
    return SearchQueryRequestMapper.toIncidentProcessInstanceStatisticsByDefinitionQuery(
            incidentProcessInstanceStatisticsByDefinitionQuery)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            this::executeIncidentProcessInstanceStatisticsByDefinition);
  }

  private ResponseEntity<IncidentSearchQueryResult> search(final IncidentQuery query) {
    try {
      final var result =
          incidentServices.search(query, authenticationProvider.getCamundaAuthentication());
      return responseOk(SearchQueryResponseMapper.toIncidentSearchQueryResponse(result));
    } catch (final ValidationException e) {
      final var problemDetail =
          GatewayErrorMapper.createProblemDetail(
              HttpStatus.BAD_REQUEST,
              e.getMessage(),
              "Validation failed for Incident Search Query");
      return RestErrorMapper.mapProblemToResponse(problemDetail);
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<IncidentProcessInstanceStatisticsByErrorQueryResult>
      executeIncidentProcessInstanceStatisticsByError(
          final io.camunda.search.query.IncidentProcessInstanceStatisticsByErrorQuery query) {
    try {
      final var result =
          incidentServices.incidentProcessInstanceStatisticsByError(
              query, authenticationProvider.getCamundaAuthentication());
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toIncidentProcessInstanceStatisticsByErrorResult(result));
    } catch (final ValidationException e) {
      final var problemDetail =
          GatewayErrorMapper.createProblemDetail(
              HttpStatus.BAD_REQUEST,
              e.getMessage(),
              "Validation failed for Incident Statistics Query");
      return RestErrorMapper.mapProblemToResponse(problemDetail);
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<IncidentProcessInstanceStatisticsByDefinitionQueryResult>
      executeIncidentProcessInstanceStatisticsByDefinition(
          final io.camunda.search.query.IncidentProcessInstanceStatisticsByDefinitionQuery query) {
    try {
      final var result =
          incidentServices.searchIncidentProcessInstanceStatisticsByDefinition(
              query, authenticationProvider.getCamundaAuthentication());
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toIncidentProcessInstanceStatisticsByDefinitionQueryResult(
              result));
    } catch (final ValidationException e) {
      final var problemDetail =
          GatewayErrorMapper.createProblemDetail(
              HttpStatus.BAD_REQUEST,
              e.getMessage(),
              "Validation failed for Incident Process Instance Statistics By Definition Query");
      return RestErrorMapper.mapProblemToResponse(problemDetail);
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  /**
   * Type-erasing helper: the slice-2 mapper returns strict-contract records, while the generated
   * API interface declares protocol-model return types. At runtime Jackson serializes the actual
   * body regardless of the generic type, so the unchecked cast is safe. This bridge will be removed
   * once the response types are unified.
   */
  @SuppressWarnings("unchecked")
  private static <T> ResponseEntity<T> responseOk(final Object body) {
    return (ResponseEntity<T>) ResponseEntity.ok(body);
  }
}
