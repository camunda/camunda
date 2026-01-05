/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.protocol.model.DecisionRequirementsResult;
import io.camunda.gateway.protocol.model.DecisionRequirementsSearchQuery;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.DecisionRequirementsServices;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.mapper.search.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.mapper.search.SearchQueryResponseMapper;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequiresSecondaryStorage
@RequestMapping("/v2/decision-requirements")
public class DecisionRequirementsController {

  private final DecisionRequirementsServices decisionRequirementsServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public DecisionRequirementsController(
      final DecisionRequirementsServices decisionRequirementsServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.decisionRequirementsServices = decisionRequirementsServices;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<Object> searchDecisionRequirements(
      @RequestBody(required = false) final DecisionRequirementsSearchQuery query) {
    return SearchQueryRequestMapper.toDecisionRequirementsQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  private ResponseEntity<Object> search(final DecisionRequirementsQuery query) {
    try {
      final var result =
          decisionRequirementsServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .search(query);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toDecisionRequirementsSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @CamundaGetMapping(path = "/{decisionRequirementsKey}")
  public ResponseEntity<DecisionRequirementsResult> getByKey(
      @PathVariable("decisionRequirementsKey") final Long decisionRequirementsKey) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toDecisionRequirements(
                  decisionRequirementsServices
                      .withAuthentication(authenticationProvider.getCamundaAuthentication())
                      .getByKey(decisionRequirementsKey)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @CamundaGetMapping(
      path = "/{decisionRequirementsKey}/xml",
      produces = {MediaType.TEXT_XML_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<String> getDecisionRequirementsXml(
      @PathVariable("decisionRequirementsKey") final Long decisionRequirementsKey) {
    try {
      return ResponseEntity.ok()
          .contentType(new MediaType(MediaType.TEXT_XML, StandardCharsets.UTF_8))
          .body(
              decisionRequirementsServices
                  .withAuthentication(authenticationProvider.getCamundaAuthentication())
                  .getDecisionRequirementsXml(decisionRequirementsKey));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
