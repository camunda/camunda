/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.Loggers.REST_LOGGER;

import io.camunda.service.DecisionDefinitionServices;
import io.camunda.service.exception.NotFoundException;
import io.camunda.service.search.query.DecisionDefinitionQuery;
import io.camunda.zeebe.gateway.protocol.rest.DecisionDefinitionSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.DecisionDefinitionSearchQueryResponse;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.annotation.PostMappingStringKeys;
import io.camunda.zeebe.protocol.record.RejectionType;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestQueryController
@RequestMapping("/v2/decision-definitions")
public class DecisionDefinitionQueryController {

  @Autowired private DecisionDefinitionServices decisionDefinitionServices;

  @PostMappingStringKeys(path = "/search")
  public ResponseEntity<DecisionDefinitionSearchQueryResponse> searchDecisionDefinitions(
      @RequestBody(required = false) final DecisionDefinitionSearchQueryRequest query) {
    return SearchQueryRequestMapper.toDecisionDefinitionQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  @GetMapping(
      path = "/{decisionKey}/xml",
      produces = {MediaType.TEXT_XML_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<String> getDecisionDefinitionXml(
      @PathVariable("decisionKey") final Long decisionKey) {
    try {
      return ResponseEntity.ok()
          .contentType(new MediaType(MediaType.TEXT_XML, StandardCharsets.UTF_8))
          .body(decisionDefinitionServices.getDecisionDefinitionXml(decisionKey));
    } catch (final NotFoundException nfe) {
      final var problemDetail =
          RestErrorMapper.createProblemDetail(
              HttpStatus.NOT_FOUND, nfe.getMessage(), RejectionType.NOT_FOUND.name());
      return RestErrorMapper.mapProblemToResponse(problemDetail);
    } catch (final Exception e) {
      REST_LOGGER.warn("An exception occurred in getDecisionDefinitionXml.", e);
      final var problemDetail =
          RestErrorMapper.createProblemDetail(
              HttpStatus.INTERNAL_SERVER_ERROR,
              e.getMessage(),
              "Failed to execute Get Decision Definition XML Query.");
      return RestErrorMapper.mapProblemToResponse(problemDetail);
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
      REST_LOGGER.warn("An exception occurred in searchDecisionDefinitions.", e);
      final var problemDetail =
          RestErrorMapper.createProblemDetail(
              HttpStatus.INTERNAL_SERVER_ERROR,
              e.getMessage(),
              "Failed to execute Decision Definition Search Query.");
      return RestErrorMapper.mapProblemToResponse(problemDetail);
    }
  }
}
