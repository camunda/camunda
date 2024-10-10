/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.search.query.VariableQuery;
import io.camunda.service.VariableServices;
import io.camunda.zeebe.gateway.protocol.rest.VariableSearchQueryRequest;
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
@RequestMapping("/v2/variables")
public class VariableQueryController {

  private final VariableServices variableServices;

  public VariableQueryController(final VariableServices variableServices) {
    this.variableServices = variableServices;
  }

  @PostMapping(
      path = "/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Object> searchVariables(
      @RequestBody(required = false) final VariableSearchQueryRequest query) {
    return SearchQueryRequestMapper.toVariableQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  private ResponseEntity<Object> search(final VariableQuery query) {
    try {
      final var result =
          variableServices.withAuthentication(RequestMapper.getAuthentication()).search(query);
      return ResponseEntity.ok(SearchQueryResponseMapper.toVariableSearchQueryResponse(result));
    } catch (final ValidationException e) {
      final var problemDetail =
          RestErrorMapper.createProblemDetail(
              HttpStatus.BAD_REQUEST,
              e.getMessage(),
              "Validation failed for UserTask Search Query");
      return RestErrorMapper.mapProblemToResponse(problemDetail);
    } catch (final Exception e) {
      final var problemDetail =
          RestErrorMapper.createProblemDetail(
              HttpStatus.INTERNAL_SERVER_ERROR,
              e.getMessage(),
              "Failed to execute UserTask Search Query");
      return RestErrorMapper.mapProblemToResponse(problemDetail);
    }
  }

  @GetMapping(
      path = "/{variableKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<Object> getByKey(@PathVariable("variableKey") final Long variableKey) {
    try {
      // Success case: Return the left side with the VariableItem wrapped in ResponseEntity
      return ResponseEntity.ok()
          .body(SearchQueryResponseMapper.toVariable(variableServices.getByKey(variableKey)));
    } catch (final Exception exc) {
      // Error case: Return the right side with ProblemDetail
      final var problemDetail =
          RestErrorMapper.mapErrorToProblem(exc, RestErrorMapper.DEFAULT_REJECTION_MAPPER);
      return RestErrorMapper.mapProblemToResponse(problemDetail);
    }
  }
}
