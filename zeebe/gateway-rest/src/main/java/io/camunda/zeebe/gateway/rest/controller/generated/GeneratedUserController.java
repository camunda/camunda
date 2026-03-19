/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.generated;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserUpdateRequestStrictContract;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import jakarta.annotation.Generated;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@CamundaRestController
@RequestMapping("/v2")
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public class GeneratedUserController {

  private final UserServiceAdapter serviceAdapter;
  private final CamundaAuthenticationProvider authenticationProvider;

  public GeneratedUserController(
      final UserServiceAdapter serviceAdapter,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceAdapter = serviceAdapter;
    this.authenticationProvider = authenticationProvider;
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/users",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> createUser(
      @RequestBody final GeneratedUserRequestStrictContract userRequest) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.createUser(userRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/users/search",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> searchUsers(
      @RequestBody final GeneratedUserSearchQueryRequestStrictContract userSearchQueryRequest) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.searchUsers(userSearchQueryRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/users/{username}",
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> getUser(@PathVariable("username") final String username) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.getUser(username, authentication);
  }

  @RequestMapping(
      method = RequestMethod.PUT,
      value = "/users/{username}",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> updateUser(
      @PathVariable("username") final String username,
      @RequestBody final GeneratedUserUpdateRequestStrictContract userUpdateRequest) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.updateUser(username, userUpdateRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.DELETE,
      value = "/users/{username}",
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Void> deleteUser(@PathVariable("username") final String username) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.deleteUser(username, authentication);
  }
}
