/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.Loggers.REST_LOGGER;

import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.service.DecisionRequirementsServices;
import io.camunda.zeebe.gateway.protocol.rest.DecisionRequirementsItem;
import io.camunda.zeebe.gateway.protocol.rest.DecisionRequirementsSearchQueryRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import jakarta.validation.ValidationException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestQueryController
@RequestMapping("/v2/decision-requirements")
public class DecisionRequirementsQueryController {

  private final DecisionRequirementsServices decisionRequirementsServices;

  public DecisionRequirementsQueryController(
      final DecisionRequirementsServices decisionRequirementsServices) {
    this.decisionRequirementsServices = decisionRequirementsServices;
  }

  @PostMapping(
      path = "/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Object> searchUserTasks(
      @RequestBody(required = false) final DecisionRequirementsSearchQueryRequest query) {
    return SearchQueryRequestMapper.toDecisionRequirementsQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  private ResponseEntity<Object> search(final DecisionRequirementsQuery query) {
    try {
      final var result =
          decisionRequirementsServices
              .withAuthentication(RequestMapper.getAuthentication())
              .search(query);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toDecisionRequirementsSearchQueryResponse(result));
    } catch (final ValidationException e) {
      final var problemDetail =
          RestErrorMapper.createProblemDetail(
              HttpStatus.BAD_REQUEST,
              e.getMessage(),
              "Validation failed for Decision Requirement Search Query");
      return RestErrorMapper.mapProblemToResponse(problemDetail);
    } catch (final Exception e) {
      final var problemDetail =
          RestErrorMapper.createProblemDetail(
              HttpStatus.INTERNAL_SERVER_ERROR,
              e.getMessage(),
              "Failed to execute Decision Requirement Search Query");
      return RestErrorMapper.mapProblemToResponse(problemDetail);
    }
  }

  @GetMapping(
      path = "/{decisionRequirementsKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<DecisionRequirementsItem> getByKey(
      @PathVariable("decisionRequirementsKey") final Long decisionRequirementsKey) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toDecisionRequirements(
                  decisionRequirementsServices.getByKey(decisionRequirementsKey)));
    } catch (final Exception exc) {
      REST_LOGGER.warn("An exception occurred in get Form by key.", exc);
      final var problemDetail =
          RestErrorMapper.mapErrorToProblem(exc, RestErrorMapper.DEFAULT_REJECTION_MAPPER);
      return RestErrorMapper.mapProblemToResponse(problemDetail);
    }
  }

  @GetMapping(
      path = "/{decisionRequirementsKey}/xml",
      produces = {MediaType.TEXT_XML_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<String> getDecisionRequirementsXml(
      @PathVariable("decisionRequirementsKey") final Long decisionRequirementsKey) {
    try {
      return ResponseEntity.ok()
          .contentType(new MediaType(MediaType.TEXT_XML, StandardCharsets.UTF_8))
          .body(decisionRequirementsServices.getDecisionRequirementsXml(decisionRequirementsKey));
    } catch (final Exception exc) {
      REST_LOGGER.warn("An exception occurred in getDecisionRequirementsXml.", exc);
      final var problemDetail =
          RestErrorMapper.mapErrorToProblem(exc, RestErrorMapper.DEFAULT_REJECTION_MAPPER);
      return RestErrorMapper.mapProblemToResponse(problemDetail);
    }
  }
}
