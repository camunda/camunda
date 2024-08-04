/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.search.query.ProcessInstanceQuery;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceSearchQueryResponse;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestQueryController
@RequestMapping("/v2/process-instances")
public class ProcessInstanceQueryController {

  @Autowired private ProcessInstanceServices processInstanceServices;

  @PostMapping(
      path = "/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ProcessInstanceSearchQueryResponse> searchProcessInstances(
      @RequestBody(required = false) final ProcessInstanceSearchQueryRequest query) {
    return SearchQueryRequestMapper.toProcessInstanceQuery(query)
        .fold(this::search, RestErrorMapper::mapProblemToResponse);
  }

  private ResponseEntity<ProcessInstanceSearchQueryResponse> search(
      final ProcessInstanceQuery query) {
    try {
      final var result =
          processInstanceServices
              .withAuthentication(RequestMapper.getAuthentication())
              .search(query);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toProcessInstanceSearchQueryResponse(result));
    } catch (final Throwable e) {
      final var problemDetail =
          RestErrorMapper.createProblemDetail(
              HttpStatus.BAD_REQUEST,
              e.getMessage(),
              "Failed to execute Process Instance Search Query");
      return RestErrorMapper.mapProblemToResponse(problemDetail);
    }
  }
}
