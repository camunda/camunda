/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.authentication.ConditionalOnInternalUserManagement;
import io.camunda.gateway.protocol.model.UserRequest;
import io.camunda.gateway.protocol.model.UserSearchQueryRequest;
import io.camunda.gateway.protocol.model.UserSearchResult;
import io.camunda.gateway.protocol.model.UserUpdateRequest;
import io.camunda.search.query.UserQuery;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.UserServices;
import io.camunda.service.UserServices.UserDTO;
import io.camunda.zeebe.gateway.rest.annotation.CamundaDeleteMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPutMapping;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import io.camunda.zeebe.gateway.rest.mapper.RequestMapper;
import io.camunda.zeebe.gateway.rest.mapper.ResponseMapper;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.mapper.search.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.mapper.search.SearchQueryResponseMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/users")
@ConditionalOnInternalUserManagement()
public class UserController {
  private final UserServices userServices;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final SecurityConfiguration securityConfiguration;

  public UserController(
      final UserServices userServices,
      final CamundaAuthenticationProvider authenticationProvider,
      final SecurityConfiguration securityConfiguration) {
    this.userServices = userServices;
    this.authenticationProvider = authenticationProvider;
    this.securityConfiguration = securityConfiguration;
  }

  @CamundaPostMapping
  public CompletableFuture<ResponseEntity<Object>> createUser(
      @RequestBody final UserRequest userRequest) {
    return RequestMapper.toUserRequest(
            userRequest, securityConfiguration.getCompiledIdValidationPattern())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::createUser);
  }

  @CamundaGetMapping(path = "/{username}")
  @RequiresSecondaryStorage
  public ResponseEntity<Object> getUser(@PathVariable final String username) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toUser(
                  userServices
                      .withAuthentication(authenticationProvider.getCamundaAuthentication())
                      .getUser(username)));
    } catch (final Exception exception) {
      return RestErrorMapper.mapErrorToResponse(exception);
    }
  }

  @CamundaDeleteMapping(path = "/{username}")
  public CompletableFuture<ResponseEntity<Object>> deleteUser(@PathVariable final String username) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            userServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .deleteUser(username));
  }

  private CompletableFuture<ResponseEntity<Object>> createUser(final UserDTO request) {
    return RequestMapper.executeServiceMethod(
        () ->
            userServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .createUser(request),
        ResponseMapper::toUserCreateResponse);
  }

  @CamundaPutMapping(path = "/{username}")
  public CompletableFuture<ResponseEntity<Object>> updateUser(
      @PathVariable final String username, @RequestBody final UserUpdateRequest userUpdateRequest) {
    return RequestMapper.toUserUpdateRequest(userUpdateRequest, username)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::updateUser);
  }

  private CompletableFuture<ResponseEntity<Object>> updateUser(final UserDTO request) {
    return RequestMapper.executeServiceMethod(
        () ->
            userServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .updateUser(request),
        ResponseMapper::toUserUpdateResponse);
  }

  @CamundaPostMapping(path = "/search")
  @RequiresSecondaryStorage
  public ResponseEntity<UserSearchResult> searchUsers(
      @RequestBody(required = false) final UserSearchQueryRequest query) {
    return SearchQueryRequestMapper.toUserQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  private ResponseEntity<UserSearchResult> search(final UserQuery query) {
    try {
      final var result =
          userServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .search(query);
      return ResponseEntity.ok(SearchQueryResponseMapper.toUserSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
