/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.Loggers.REST_LOGGER;
import static io.camunda.zeebe.gateway.rest.RestErrorMapper.mapErrorToResponse;

import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.service.DecisionDefinitionServices;
import io.camunda.zeebe.gateway.protocol.rest.DecisionDefinitionItem;
import io.camunda.zeebe.gateway.protocol.rest.DecisionDefinitionSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.DecisionDefinitionSearchQueryResponse;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestQueryController
@RequestMapping("/v2/decision-definitions")
public class DecisionDefinitionQueryController {

  private final DecisionDefinitionServices decisionDefinitionServices;

  public DecisionDefinitionQueryController(
      final DecisionDefinitionServices decisionDefinitionServices) {
    this.decisionDefinitionServices = decisionDefinitionServices;
  }

  @PostMapping(
      path = "/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<DecisionDefinitionSearchQueryResponse> searchDecisionDefinitions(
      @RequestBody(required = false) final DecisionDefinitionSearchQueryRequest query) {
    return SearchQueryRequestMapper.toDecisionDefinitionQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  @GetMapping(
      path = "/{decisionDefinitionKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<DecisionDefinitionItem> getDecisionDefinitionByKey(
      @PathVariable("decisionDefinitionKey") final long decisionDefinitionKey) {
    try {
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toDecisionDefinition(
              decisionDefinitionServices
                  .withAuthentication(RequestMapper.getAuthentication())
                  .getByKey(decisionDefinitionKey)));
    } catch (final Exception e) {
      REST_LOGGER.debug("An exception occurred in getDecisionDefinition.", e);
      return mapErrorToResponse(e);
    }
  }

  @GetMapping(
      path = "/{decisionDefinitionKey}/xml",
      produces = {MediaType.TEXT_XML_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<String> getDecisionDefinitionXml(
      @PathVariable("decisionDefinitionKey") final long decisionDefinitionKey) {
    try {
      return ResponseEntity.ok()
          .contentType(new MediaType(MediaType.TEXT_XML, StandardCharsets.UTF_8))
          .body(
              decisionDefinitionServices
                  .withAuthentication(RequestMapper.getAuthentication())
                  .getDecisionDefinitionXml(decisionDefinitionKey));
    } catch (final Exception e) {
      REST_LOGGER.debug("An exception occurred in getDecisionDefinitionXml.", e);
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<DecisionDefinitionSearchQueryResponse> search(
      final DecisionDefinitionQuery query) {
    try {
      final var result =
          decisionDefinitionServices
              .withAuthentication(RequestMapper.getAuthentication())
              .search(query);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toDecisionDefinitionSearchQueryResponse(result));
    } catch (final Exception e) {
      REST_LOGGER.debug("An exception occurred in searchDecisionDefinitions.", e);
      return mapErrorToResponse(e);
    }
  }
}
