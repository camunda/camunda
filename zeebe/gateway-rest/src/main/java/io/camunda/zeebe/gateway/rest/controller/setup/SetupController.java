/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.setup;

import io.camunda.auth.domain.model.AuthenticationMethod;
import io.camunda.auth.domain.spi.CamundaAuthenticationProvider;
import io.camunda.gateway.mapping.http.GatewayErrorMapper;
import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.validator.RequestValidator;
import io.camunda.gateway.protocol.model.UserCreateResult;
import io.camunda.gateway.protocol.model.UserRequest;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.security.validation.UserValidator;
import io.camunda.service.RoleServices;
import io.camunda.service.UserServices;
import io.camunda.service.UserServices.UserDTO;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import io.camunda.zeebe.protocol.record.value.DefaultRole;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequiresSecondaryStorage
@RequestMapping("/v2/setup")
public class SetupController {

  public static final String WRONG_AUTHENTICATION_METHOD_ERROR_MESSAGE =
      "The initial admin user can only be created with basic authentication.";
  public static final String ADMIN_EXISTS_ERROR_MESSAGE =
      "Expected to create an initial admin user, but found existing admin users. Please ask your admin to create a new user with the '%s' role."
          .formatted(DefaultRole.ADMIN.getId());
  private final UserServices userServices;
  private final RoleServices roleServices;
  private final SecurityConfiguration securityConfiguration;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final UserValidator userValidator;

  public SetupController(
      final UserServices userServices,
      final RoleServices roleServices,
      final SecurityConfiguration securityConfiguration,
      final CamundaAuthenticationProvider authenticationProvider,
      final IdentifierValidator identifierValidator) {
    this.userServices = userServices;
    this.roleServices = roleServices;
    this.securityConfiguration = securityConfiguration;
    this.authenticationProvider = authenticationProvider;
    this.userValidator = new UserValidator(identifierValidator);
  }

  @CamundaPostMapping(path = "/user")
  public CompletableFuture<ResponseEntity<Object>> createAdminUser(
      @RequestBody final UserRequest request) {
    if (securityConfiguration.getAuthentication().getMethod() != AuthenticationMethod.BASIC) {
      final var exception =
          new ServiceException(WRONG_AUTHENTICATION_METHOD_ERROR_MESSAGE, Status.FORBIDDEN);
      return RestErrorMapper.mapProblemToCompletedResponse(
          GatewayErrorMapper.mapErrorToProblem(exception));
    }

    if (roleServices
        .withAuthentication(authenticationProvider.getAnonymousCamundaAuthentication())
        .hasMembersOfType(DefaultRole.ADMIN.getId(), EntityType.USER)) {
      final var exception = new ServiceException(ADMIN_EXISTS_ERROR_MESSAGE, Status.FORBIDDEN);
      return RestErrorMapper.mapProblemToCompletedResponse(
          GatewayErrorMapper.mapErrorToProblem(exception));
    }

    return RequestMapper.getResult(
            RequestValidator.validate(
                () ->
                    userValidator.validateCreateRequest(
                        request.getUsername(),
                        request.getPassword(),
                        request.getName(),
                        request.getEmail())),
            () ->
                new UserDTO(
                    request.getUsername(),
                    request.getName(),
                    request.getEmail(),
                    request.getPassword()))
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            dto ->
                RequestExecutor.executeServiceMethod(
                    () ->
                        userServices
                            .withAuthentication(
                                authenticationProvider.getAnonymousCamundaAuthentication())
                            .createInitialAdminUser(dto),
                    record ->
                        new UserCreateResult()
                            .username(record.getUsername())
                            .email(record.getEmail())
                            .name(record.getName()),
                    HttpStatus.CREATED));
  }
}
