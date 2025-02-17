/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.service.DecisionInstanceServices;
import io.camunda.zeebe.gateway.protocol.rest.DecisionInstanceGetQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.DecisionInstanceSearchQuery;
import io.camunda.zeebe.gateway.protocol.rest.DecisionInstanceSearchQueryResult;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/decision-instances")
public class DecisionInstanceController {

  @Autowired private DecisionInstanceServices decisionInstanceServices;

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<DecisionInstanceSearchQueryResult> searchDecisionInstances(
      @RequestBody(required = false) final DecisionInstanceSearchQuery query) {
    return SearchQueryRequestMapper.toDecisionInstanceQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  @CamundaGetMapping(path = "/{decisionInstanceKey}")
  public ResponseEntity<DecisionInstanceGetQueryResult> getDecisionInstanceById(
      @PathVariable("decisionInstanceKey") final String decisionInstanceId) {
    try {
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toDecisionInstanceGetQueryResponse(
              decisionInstanceServices
                  .withAuthentication(RequestMapper.getAuthentication())
                  .getById(decisionInstanceId)));
    } catch (final Exception e) {
      return RestErrorMapper.mapErrorToResponse(e);
    }
  }

  private ResponseEntity<DecisionInstanceSearchQueryResult> search(
      final DecisionInstanceQuery query) {
    try {
      final var decisionInstances =
          decisionInstanceServices
              .withAuthentication(RequestMapper.getAuthentication())
              .search(query);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toDecisionInstanceSearchQueryResponse(decisionInstances));
    } catch (final Exception e) {
      return RestErrorMapper.mapErrorToResponse(e);
    }
  }
}
