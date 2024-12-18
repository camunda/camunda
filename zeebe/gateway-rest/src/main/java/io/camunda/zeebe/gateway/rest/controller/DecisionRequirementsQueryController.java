/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.service.DecisionRequirementsServices;
import io.camunda.service.search.query.DecisionRequirementsQuery;
import io.camunda.zeebe.gateway.protocol.rest.DecisionRequirementsSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.DecisionRequirementsSearchQueryResponse;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import jakarta.validation.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestQueryController
@RequestMapping("/v2/decision-requirements")
public class DecisionRequirementsQueryController {

  @Autowired private DecisionRequirementsServices decisionRequirementsServices;

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<DecisionRequirementsSearchQueryResponse> searchUserTasks(
      @RequestBody(required = false) final DecisionRequirementsSearchQueryRequest query) {
    return SearchQueryRequestMapper.toDecisionRequirementsQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  private ResponseEntity<DecisionRequirementsSearchQueryResponse> search(
      final DecisionRequirementsQuery query) {
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
}
