/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.service.FlownodeInstanceServices;
import io.camunda.service.search.query.FlownodeInstanceQuery;
import io.camunda.zeebe.gateway.protocol.rest.FlownodeInstanceSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.FlownodeInstanceSearchQueryResponse;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestQueryController
@RequestMapping("/v2/flownode-instances")
public class FlownodeInstanceQueryController {

  private final FlownodeInstanceServices flownodeInstanceServices;

  public FlownodeInstanceQueryController(final FlownodeInstanceServices flownodeInstanceServices) {
    this.flownodeInstanceServices = flownodeInstanceServices;
  }

  @PostMapping(
      path = "/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<FlownodeInstanceSearchQueryResponse> searchFlownodeInstances(
      @RequestBody(required = false) final FlownodeInstanceSearchQueryRequest query) {
    return SearchQueryRequestMapper.toFlownodeInstanceQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  private ResponseEntity<FlownodeInstanceSearchQueryResponse> search(
      final FlownodeInstanceQuery query) {
    try {
      final var result =
          flownodeInstanceServices
              .withAuthentication(RequestMapper.getAuthentication())
              .search(query);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toFlownodeInstanceSearchQueryResponse(result));
    } catch (final Throwable e) {
      final var problemDetail =
          RestErrorMapper.createProblemDetail(
              HttpStatus.BAD_REQUEST,
              e.getMessage(),
              "Failed to execute Flownode Instance Search Query");
      return RestErrorMapper.mapProblemToResponse(problemDetail);
    }
  }
}
