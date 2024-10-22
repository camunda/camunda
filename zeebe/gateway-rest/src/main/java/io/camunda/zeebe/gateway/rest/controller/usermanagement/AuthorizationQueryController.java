/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import io.camunda.search.query.AuthorizationQuery;
import io.camunda.service.AuthorizationServices;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationSearchResponse;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestQueryController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestQueryController
@RequestMapping("/v2/authorizations")
public class AuthorizationQueryController {
  private final AuthorizationServices authorizationServices;

  public AuthorizationQueryController(final AuthorizationServices authorizationServices) {
    this.authorizationServices = authorizationServices;
  }

  @PostMapping(
      path = "/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<AuthorizationSearchResponse> searchUsers(
      @RequestBody(required = false) final AuthorizationSearchQueryRequest query) {
    return SearchQueryRequestMapper.toAuthorizationQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  private ResponseEntity<AuthorizationSearchResponse> search(final AuthorizationQuery query) {
    try {
      final var result = authorizationServices.search(query);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toAuthorizationSearchQueryResponse(result));
    } catch (final Throwable e) {
      final var problemDetail =
          RestErrorMapper.createProblemDetail(
              HttpStatus.BAD_REQUEST, e.getMessage(), "Failed to execute User Search Query");
      return RestErrorMapper.mapProblemToResponse(problemDetail);
    }
  }
}
