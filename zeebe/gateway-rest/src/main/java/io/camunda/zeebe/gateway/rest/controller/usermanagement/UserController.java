/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import static io.camunda.zeebe.gateway.rest.RestErrorMapper.mapErrorToResponse;

import io.camunda.search.query.UserQuery;
import io.camunda.service.RoleServices;
import io.camunda.service.UserServices;
import io.camunda.service.UserServices.UserDTO;
import io.camunda.zeebe.gateway.protocol.rest.UserRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserSearchResult;
import io.camunda.zeebe.gateway.protocol.rest.UserUpdateRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.ResponseMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaDeleteMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPutMapping;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/users")
public class UserController {
  private final UserServices userServices;
  private final RoleServices roleServices;

  public UserController(final UserServices userServices, final RoleServices roleServices) {
    this.userServices = userServices;
    this.roleServices = roleServices;
  }

  @CamundaPostMapping
  public CompletableFuture<ResponseEntity<Object>> createUser(
      @RequestBody final UserRequest userRequest) {
    return RequestMapper.toUserDTO(userRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::createUser);
  }

  @CamundaDeleteMapping(path = "/{username}")
  public CompletableFuture<ResponseEntity<Object>> deleteUser(@PathVariable final String username) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            userServices
                .withAuthentication(RequestMapper.getAuthentication())
                .deleteUser(username));
  }

  private CompletableFuture<ResponseEntity<Object>> createUser(final UserDTO request) {
    return RequestMapper.executeServiceMethod(
        () ->
            userServices.withAuthentication(RequestMapper.getAuthentication()).createUser(request),
        ResponseMapper::toUserCreateResponse);
  }

  @CamundaPutMapping(path = "/{username}")
  public CompletableFuture<ResponseEntity<Object>> updateUser(
      @PathVariable final String username, @RequestBody final UserUpdateRequest userUpdateRequest) {
    return RequestMapper.toUserUpdateRequest(userUpdateRequest, username)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::updateUser);
  }

  private CompletableFuture<ResponseEntity<Object>> updateUser(final UserDTO request) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            userServices.withAuthentication(RequestMapper.getAuthentication()).updateUser(request));
  }

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<UserSearchResult> searchUsers(
      @RequestBody(required = false) final UserSearchQueryRequest query) {
    return SearchQueryRequestMapper.toUserQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  private ResponseEntity<UserSearchResult> search(final UserQuery query) {
    try {
      final var result =
          userServices.withAuthentication(RequestMapper.getAuthentication()).search(query);
      return ResponseEntity.ok(SearchQueryResponseMapper.toUserSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
