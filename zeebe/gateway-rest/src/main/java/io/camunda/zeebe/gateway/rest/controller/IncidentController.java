/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.RestErrorMapper.mapErrorToResponse;

import io.camunda.search.query.IncidentQuery;
import io.camunda.service.IncidentServices;
import io.camunda.zeebe.gateway.protocol.rest.IncidentResult;
import io.camunda.zeebe.gateway.protocol.rest.IncidentSearchQuery;
import io.camunda.zeebe.gateway.protocol.rest.IncidentSearchQueryResult;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
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

  public IncidentController(final IncidentServices incidentServices) {
    this.incidentServices = incidentServices;
  }

  @CamundaPostMapping(path = "/{incidentKey}/resolution")
  public CompletableFuture<ResponseEntity<Object>> incidentResolution(
      @PathVariable final long incidentKey) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            incidentServices
                .withAuthentication(RequestMapper.getAuthentication())
                .resolveIncident(incidentKey));
  }

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<IncidentSearchQueryResult> searchIncidents(
      @RequestBody(required = false) final IncidentSearchQuery query) {
    return SearchQueryRequestMapper.toIncidentQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  @CamundaGetMapping(path = "/{incidentKey}")
  public ResponseEntity<IncidentResult> getByKey(
      @PathVariable("incidentKey") final Long incidentKey) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toIncident(
                  incidentServices
                      .withAuthentication(RequestMapper.getAuthentication())
                      .getByKey(incidentKey)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<IncidentSearchQueryResult> search(final IncidentQuery query) {
    try {
      final var result =
          incidentServices.withAuthentication(RequestMapper.getAuthentication()).search(query);
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
}
