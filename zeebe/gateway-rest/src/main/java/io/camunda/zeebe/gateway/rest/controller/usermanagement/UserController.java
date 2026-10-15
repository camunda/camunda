/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.mapping.http.mapper.UserMapper;
import io.camunda.gateway.mapping.http.validator.UserRequestValidator;
import io.camunda.gateway.protocol.model.UserRequest;
import io.camunda.gateway.protocol.model.UserUpdateRequest;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.spring.annotation.ConditionalOnInternalUserManagement;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.security.validation.UserValidator;
import io.camunda.service.UserServices.UserDTO;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaDeleteMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPutMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/users")
@ConditionalOnInternalUserManagement()
public class UserController {
  private final ServiceRegistry serviceRegistry;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final UserMapper userMapper;

  public UserController(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider,
      final IdentifierValidator identifierValidator) {
    this.serviceRegistry = serviceRegistry;
    this.authenticationProvider = authenticationProvider;
    userMapper = new UserMapper(new UserRequestValidator(new UserValidator(identifierValidator)));
  }

  @CamundaPostMapping
  public CompletableFuture<ResponseEntity<Object>> createUser(
      @PhysicalTenantId final String physicalTenantId, @RequestBody final UserRequest userRequest) {
    return userMapper
        .toUserRequest(userRequest)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> createUser(physicalTenantId, request));
  }

  @CamundaDeleteMapping(path = "/{username}")
  public CompletableFuture<ResponseEntity<Object>> deleteUser(
      @PhysicalTenantId final String physicalTenantId, @PathVariable final String username) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () -> serviceRegistry.userServices(physicalTenantId).deleteUser(username, authentication));
  }

  private CompletableFuture<ResponseEntity<Object>> createUser(
      final String physicalTenantId, final UserDTO request) {
    final var userServices = serviceRegistry.userServices(physicalTenantId);
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> userServices.createUser(request, authentication),
        ResponseMapper::toUserCreateResponse,
        HttpStatus.CREATED);
  }

  @CamundaPutMapping(path = "/{username}")
  public CompletableFuture<ResponseEntity<Object>> updateUser(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final String username,
      @RequestBody final UserUpdateRequest userUpdateRequest) {
    return userMapper
        .toUserUpdateRequest(userUpdateRequest, username)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> updateUser(physicalTenantId, request));
  }

  private CompletableFuture<ResponseEntity<Object>> updateUser(
      final String physicalTenantId, final UserDTO request) {
    final var userServices = serviceRegistry.userServices(physicalTenantId);
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> userServices.updateUser(request, authentication),
        ResponseMapper::toUserUpdateResponse,
        HttpStatus.OK);
  }
}
