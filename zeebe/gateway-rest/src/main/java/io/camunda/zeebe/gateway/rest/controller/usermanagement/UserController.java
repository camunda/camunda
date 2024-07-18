/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import io.camunda.identity.automation.usermanagement.CamundaUserWithPassword;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.CamundaServices;
import io.camunda.service.IdentityServices;
import io.camunda.service.UserServices;
import io.camunda.service.search.filter.AuthorizationFilter;
import io.camunda.service.search.filter.UserFilter;
import io.camunda.service.search.query.AuthorizationQuery;
import io.camunda.service.search.query.UserQuery;
import io.camunda.service.search.sort.AuthorizationSort;
import io.camunda.service.search.sort.UserSort;
import io.camunda.zeebe.gateway.protocol.rest.CamundaUserWithPasswordRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.controller.ZeebeRestController;
import io.camunda.zeebe.protocol.impl.record.value.identity.UserRecord;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@ZeebeRestController
@RequestMapping("/v2/users")
public class UserController {

  private final IdentityServices<UserRecord> identityServices;
  private final UserServices userServices;
  private final AuthorizationServices authorizationServices;
  // private final UserService userService;
  private final PasswordEncoder passwordEncoder;

  public UserController(
      final CamundaServices camundaServices, final PasswordEncoder passwordEncoder) {

    identityServices = camundaServices.identityServices();
    userServices = camundaServices.userServices();
    authorizationServices = camundaServices.authorizationServices();

    this.passwordEncoder = passwordEncoder;
  }

  @PostMapping(
      path = "new",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> createUserNew(
      @RequestBody final CamundaUserWithPasswordRequest userWithPasswordDto) {

    final var dto = RequestMapper.toUserWithPassword(userWithPasswordDto);
    return createNewUser(dto);
  }

  private CompletableFuture<ResponseEntity<Object>> createNewUser(
      final CamundaUserWithPassword request) {
    return RequestMapper.executeServiceMethodWithNoContenResult(
        () ->
            identityServices
                .withAuthentication(RequestMapper.getAuthentication())
                .createUser(
                    request.getUsername(),
                    request.getName(),
                    request.getEmail(),
                    passwordEncoder.encode(request.getPassword())));
  }

  @PostMapping(
      path = "/{username}/authorization",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> createAuthorizationForUser(
      @PathVariable("username") final String username,
      @RequestBody final UserAuthorizationRequest userAuthorizationRequest,
      @RequestParam("user") final String user) {
    return RequestMapper.executeServiceMethodWithNoContenResult(
        () ->
            identityServices
                .withAuthentication(RequestMapper.getAuthentication(user))
                .createAuthorization(
                    username,
                    userAuthorizationRequest.getResourceKey(),
                    userAuthorizationRequest.getResourceType(),
                    userAuthorizationRequest.getPermissions()));
  }

  @PostMapping(
      path = "/new-search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Object> searchUsers() {
    try {
      final var userQuery =
          new UserQuery.Builder()
              .filter(new UserFilter.Builder().build())
              .sort(new UserSort.Builder().build())
              .build();
      final var result = userServices.search(userQuery);
      return ResponseEntity.ok(SearchQueryResponseMapper.toUserSearchQueryResponse(result).get());
    } catch (final Exception e) {
      final var problemDetail =
          RestErrorMapper.createProblemDetail(
              HttpStatus.BAD_REQUEST,
              e.getMessage(),
              "Failed to execute User Instance Search Query");
      return ResponseEntity.of(problemDetail)
          .headers(httpHeaders -> httpHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON))
          .build();
    }
  }

  @PostMapping(
      path = "/{username}/authorizations/search",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Object> searchAuthorizationsForUser(@PathVariable final String username) {
    try {
      final var authorizationQuery =
          new AuthorizationQuery.Builder()
              .filter(new AuthorizationFilter.Builder().username(username).build())
              .sort(new AuthorizationSort.Builder().build())
              .build();
      final var result = authorizationServices.search(authorizationQuery);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toAuthorizationSearchQueryResponse(result).get());
    } catch (final Exception e) {
      final var problemDetail =
          RestErrorMapper.createProblemDetail(
              HttpStatus.BAD_REQUEST,
              e.getMessage(),
              "Failed to execute Authorization Search Query");
      return ResponseEntity.of(problemDetail)
          .headers(httpHeaders -> httpHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON))
          .build();
    }
  }

  public static class UserAuthorizationRequest {
    private String resourceKey;
    private String resourceType;
    private List<String> permissions;

    public String getResourceKey() {
      return resourceKey;
    }

    public void setResourceKey(final String resourceKey) {
      this.resourceKey = resourceKey;
    }

    public String getResourceType() {
      return resourceType;
    }

    public void setResourceType(final String resourceType) {
      this.resourceType = resourceType;
    }

    public List<String> getPermissions() {
      return permissions;
    }

    public void setPermissions(final List<String> permissions) {
      this.permissions = permissions;
    }
  }
}
