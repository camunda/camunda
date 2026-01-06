/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.search.query.IncidentQuery;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.IncidentServices;
import io.camunda.zeebe.gateway.protocol.rest.IncidentProcessInstanceStatisticsByDefinitionQuery;
import io.camunda.zeebe.gateway.protocol.rest.IncidentProcessInstanceStatisticsByDefinitionQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.IncidentProcessInstanceStatisticsByErrorQuery;
import io.camunda.zeebe.gateway.protocol.rest.IncidentProcessInstanceStatisticsByErrorQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.IncidentResolutionRequest;
import io.camunda.zeebe.gateway.protocol.rest.IncidentResult;
import io.camunda.zeebe.gateway.protocol.rest.IncidentSearchQuery;
import io.camunda.zeebe.gateway.protocol.rest.IncidentSearchQueryResult;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.mapper.RequestMapper;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.mapper.search.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.mapper.search.SearchQueryResponseMapper;
import jakarta.validation.ValidationException;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("v2/incidents")
public class IncidentController {

  private final IncidentServices incidentServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public IncidentController(
      final IncidentServices incidentServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.incidentServices = incidentServices;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping(path = "/{incidentKey}/resolution")
  public CompletableFuture<ResponseEntity<Object>> incidentResolution(
      @PathVariable final long incidentKey,
      @RequestBody(required = false) final IncidentResolutionRequest incidentResolutionRequest) {
    final Long operationReference =
        incidentResolutionRequest == null
            ? null
            : incidentResolutionRequest.getOperationReference();
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            incidentServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .resolveIncident(incidentKey, operationReference));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search")
  public ResponseEntity<IncidentSearchQueryResult> searchIncidents(
      @RequestBody(required = false) final IncidentSearchQuery query) {
    return SearchQueryRequestMapper.toIncidentQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{incidentKey}")
  public ResponseEntity<IncidentResult> getByKey(
      @PathVariable("incidentKey") final Long incidentKey) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toIncident(
                  incidentServices
                      .withAuthentication(authenticationProvider.getCamundaAuthentication())
                      .getByKey(incidentKey)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/statistics/process-instances-by-error")
  public ResponseEntity<IncidentProcessInstanceStatisticsByErrorQueryResult>
      processInstanceStatisticsByError(
          @RequestBody(required = false)
              final IncidentProcessInstanceStatisticsByErrorQuery query) {
    return SearchQueryRequestMapper.toIncidentProcessInstanceStatisticsByErrorQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            this::getIncidentProcessInstanceStatisticsByError);
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/statistics/process-instances-by-definition")
  public ResponseEntity<IncidentProcessInstanceStatisticsByDefinitionQueryResult>
      incidentProcessInstanceStatisticsByDefinition(
          @RequestBody() final IncidentProcessInstanceStatisticsByDefinitionQuery query) {
    return SearchQueryRequestMapper.toIncidentProcessInstanceStatisticsByDefinitionQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            this::searchIncidentProcessInstanceStatisticsByDefinition);
  }

  private ResponseEntity<IncidentSearchQueryResult> search(final IncidentQuery query) {
    try {
      final var result =
          incidentServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .search(query);
      return ResponseEntity.ok(SearchQueryResponseMapper.toIncidentSearchQueryResponse(result));
    } catch (final ValidationException e) {
      final var problemDetail =
          RestErrorMapper.createProblemDetail(
              HttpStatus.BAD_REQUEST,
              e.getMessage(),
              "Validation failed for Incident Search Query");
      return RestErrorMapper.mapProblemToResponse(problemDetail);
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<IncidentProcessInstanceStatisticsByErrorQueryResult>
      getIncidentProcessInstanceStatisticsByError(
          final io.camunda.search.query.IncidentProcessInstanceStatisticsByErrorQuery query) {
    try {
      final var result =
          incidentServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .incidentProcessInstanceStatisticsByError(query);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toIncidentProcessInstanceStatisticsByErrorResult(result));
    } catch (final ValidationException e) {
      final var problemDetail =
          RestErrorMapper.createProblemDetail(
              HttpStatus.BAD_REQUEST,
              e.getMessage(),
              "Validation failed for Incident Statistics Query");
      return RestErrorMapper.mapProblemToResponse(problemDetail);
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<IncidentProcessInstanceStatisticsByDefinitionQueryResult>
      searchIncidentProcessInstanceStatisticsByDefinition(
          final io.camunda.search.query.IncidentProcessInstanceStatisticsByDefinitionQuery query) {
    try {
      final var result =
          incidentServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .searchIncidentProcessInstanceStatisticsByDefinition(query);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toIncidentProcessInstanceStatisticsByDefinitionQueryResult(
              result));
    } catch (final ValidationException e) {
      final var problemDetail =
          RestErrorMapper.createProblemDetail(
              HttpStatus.BAD_REQUEST,
              e.getMessage(),
              "Validation failed for Incident Process Instance Statistics By Definition Query");
      return RestErrorMapper.mapProblemToResponse(problemDetail);
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
