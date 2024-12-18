/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.Loggers.REST_LOGGER;

import io.camunda.service.FlowNodeInstanceServices;
import io.camunda.service.search.query.FlowNodeInstanceQuery;
import io.camunda.zeebe.gateway.protocol.rest.FlowNodeInstanceSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.FlowNodeInstanceSearchQueryResponse;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import jakarta.validation.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestQueryController
@RequestMapping("/v2/flownode-instances")
public class FlowNodeInstanceQueryController {

  private final FlowNodeInstanceServices flownodeInstanceServices;

  public FlowNodeInstanceQueryController(final FlowNodeInstanceServices flownodeInstanceServices) {
    this.flownodeInstanceServices = flownodeInstanceServices;
  }

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<FlowNodeInstanceSearchQueryResponse> searchFlownodeInstances(
      @RequestBody(required = false) final FlowNodeInstanceSearchQueryRequest query) {
    return SearchQueryRequestMapper.toFlownodeInstanceQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  private ResponseEntity<FlowNodeInstanceSearchQueryResponse> search(
      final FlowNodeInstanceQuery query) {
    try {
      final var result =
          flownodeInstanceServices
              .withAuthentication(RequestMapper.getAuthentication())
              .search(query);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toFlownodeInstanceSearchQueryResponse(result));
    } catch (final ValidationException e) {
      final var problemDetail =
          RestErrorMapper.createProblemDetail(
              HttpStatus.BAD_REQUEST,
              e.getMessage(),
              "Validation failed for Flownode Instance Search Query");
      REST_LOGGER.warn("An exception occurred in searchFlowNodeInstances.", e);
      return RestErrorMapper.mapProblemToResponse(problemDetail);
    } catch (final Throwable e) {
      final var problemDetail =
          RestErrorMapper.createProblemDetail(
              HttpStatus.INTERNAL_SERVER_ERROR,
              e.getMessage(),
              "Failed to execute Flownode Instance Search Query");
      return RestErrorMapper.mapProblemToResponse(problemDetail);
    }
  }
}
