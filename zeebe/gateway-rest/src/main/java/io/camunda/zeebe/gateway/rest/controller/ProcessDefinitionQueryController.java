/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.zeebe.gateway.protocol.rest.ProcessDefinitionSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.ProcessDefinitionSearchQueryResponse;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import jakarta.validation.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestQueryController
@RequestMapping("/v2/process-definitions")
public class ProcessDefinitionQueryController {

  private final ProcessDefinitionServices processDefinitionServices;

  public ProcessDefinitionQueryController(
      final ProcessDefinitionServices processDefinitionServices) {
    this.processDefinitionServices = processDefinitionServices;
  }

  @PostMapping(
      path = "/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ProcessDefinitionSearchQueryResponse> searchProcessDefinitions(
      @RequestBody(required = false) final ProcessDefinitionSearchQueryRequest query) {
    return SearchQueryRequestMapper.toProcessDefinitionQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  private ResponseEntity<ProcessDefinitionSearchQueryResponse> search(
      final ProcessDefinitionQuery query) {
    try {
      final var result =
          processDefinitionServices
              .withAuthentication(RequestMapper.getAuthentication())
              .search(query);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toProcessDefinitionSearchQueryResponse(result));
    } catch (final ValidationException e) {
      final var problemDetail =
          RestErrorMapper.createProblemDetail(
              HttpStatus.BAD_REQUEST,
              e.getMessage(),
              "Validation failed for Process definition Search Query");
      return RestErrorMapper.mapProblemToResponse(problemDetail);
    } catch (final Exception e) {
      final var problemDetail =
          RestErrorMapper.createProblemDetail(
              HttpStatus.INTERNAL_SERVER_ERROR,
              e.getMessage(),
              "Failed to execute Process definition Search Query");
      return RestErrorMapper.mapProblemToResponse(problemDetail);
    }
  }
}
