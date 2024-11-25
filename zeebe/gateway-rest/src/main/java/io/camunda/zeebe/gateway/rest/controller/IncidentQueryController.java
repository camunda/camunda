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
import io.camunda.zeebe.gateway.protocol.rest.IncidentItem;
import io.camunda.zeebe.gateway.protocol.rest.IncidentSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.IncidentSearchQueryResponse;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import jakarta.validation.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestQueryController
@RequestMapping("/v2/incidents")
public class IncidentQueryController {

  private final IncidentServices incidentServices;

  public IncidentQueryController(final IncidentServices incidentServices) {
    this.incidentServices = incidentServices;
  }

  @PostMapping(
      path = "/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<IncidentSearchQueryResponse> searchIncidents(
      @RequestBody(required = false) final IncidentSearchQueryRequest query) {
    return SearchQueryRequestMapper.toIncidentQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  @GetMapping(
      path = "/{incidentKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<IncidentItem> getByKey(
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

  private ResponseEntity<IncidentSearchQueryResponse> search(final IncidentQuery query) {
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
