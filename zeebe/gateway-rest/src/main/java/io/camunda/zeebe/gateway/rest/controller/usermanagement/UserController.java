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
import io.camunda.zeebe.gateway.protocol.rest.UserSearchResponse;
import io.camunda.zeebe.gateway.protocol.rest.UserUpdateRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RequestMapper.UpdateUserRequest;
import io.camunda.zeebe.gateway.rest.ResponseMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

  @PostMapping(
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> createUser(
      @RequestBody final UserRequest userRequest) {
    return RequestMapper.toUserDTO(null, userRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::createUser);
  }

  @DeleteMapping(
      path = "/{key}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public CompletableFuture<ResponseEntity<Object>> deleteUser(@PathVariable final long key) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () -> userServices.withAuthentication(RequestMapper.getAuthentication()).deleteUser(key));
  }

  private CompletableFuture<ResponseEntity<Object>> createUser(final UserDTO request) {
    return RequestMapper.executeServiceMethod(
        () ->
            userServices.withAuthentication(RequestMapper.getAuthentication()).createUser(request),
        ResponseMapper::toUserCreateResponse);
  }

  @PatchMapping(
      path = "/{userKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> updateUser(
      @PathVariable final long userKey, @RequestBody final UserUpdateRequest userUpdateRequest) {
    return RequestMapper.toUserUpdateRequest(userUpdateRequest, userKey)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::updateUser);
  }

  private CompletableFuture<ResponseEntity<Object>> updateUser(final UpdateUserRequest request) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            userServices
                .withAuthentication(RequestMapper.getAuthentication())
                .updateUser(
                    new UserDTO(
                        request.userKey(),
                        "",
                        request.name(),
                        request.email(),
                        request.password())));
  }

  @PutMapping(
      path = "/{userKey}/roles/{roleKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> addRole(
      @PathVariable final long userKey, @PathVariable final long roleKey) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            roleServices
                .withAuthentication(RequestMapper.getAuthentication())
                .addMember(roleKey, EntityType.USER, userKey));
  }

  @DeleteMapping(
      path = "/{userKey}/roles/{roleKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> removeRole(
      @PathVariable final long userKey, @PathVariable final long roleKey) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            roleServices
                .withAuthentication(RequestMapper.getAuthentication())
                .removeMember(roleKey, EntityType.USER, userKey));
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
      final var result =
          userServices.withAuthentication(RequestMapper.getAuthentication()).search(query);
      return ResponseEntity.ok(SearchQueryResponseMapper.toUserSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
