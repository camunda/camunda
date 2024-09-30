/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import io.camunda.search.query.UserQuery;
import io.camunda.service.UserServices;
import io.camunda.zeebe.gateway.protocol.rest.UserSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserSearchResponse;
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
@RequestMapping("/v2/users")
public class UserQueryController {

  private final UserServices userServices;

  public UserQueryController(final UserServices userServices) {
    this.userServices = userServices;
  }

  @PostMapping(
      path = "/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<UserSearchResponse> searchUsers(
      @RequestBody(required = false) final UserSearchQueryRequest query) {
    return SearchQueryRequestMapper.toUserQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  private ResponseEntity<UserSearchResponse> search(final UserQuery query) {
    try {
      final var result = userServices.search(query);
      return ResponseEntity.ok(SearchQueryResponseMapper.toUserSearchQueryResponse(result));
    } catch (final Throwable e) {
      final var problemDetail =
          RestErrorMapper.createProblemDetail(
              HttpStatus.BAD_REQUEST, e.getMessage(), "Failed to execute User Search Query");
      return RestErrorMapper.mapProblemToResponse(problemDetail);
    }
  }
}
