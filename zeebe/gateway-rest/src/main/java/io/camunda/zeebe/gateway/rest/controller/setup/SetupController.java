/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.setup;

import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.service.RoleServices;
import io.camunda.service.UserServices;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.zeebe.gateway.protocol.rest.UserRequest;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import io.camunda.zeebe.gateway.rest.mapper.RequestMapper;
import io.camunda.zeebe.gateway.rest.mapper.ResponseMapper;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import io.camunda.zeebe.protocol.record.value.DefaultRole;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.concurrent.CompletableFuture;
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

  public SetupController(
      final UserServices userServices,
      final RoleServices roleServices,
      final SecurityConfiguration securityConfiguration,
      final CamundaAuthenticationProvider authenticationProvider,
      final SecurityConfiguration securityConfig) {
    this.userServices = userServices;
    this.roleServices = roleServices;
    this.securityConfiguration = securityConfiguration;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping(path = "/user")
  public CompletableFuture<ResponseEntity<Object>> createAdminUser(
      @RequestBody final UserRequest request) {
    if (securityConfiguration.getAuthentication().getMethod() != AuthenticationMethod.BASIC) {
      final var exception =
          new ServiceException(WRONG_AUTHENTICATION_METHOD_ERROR_MESSAGE, Status.FORBIDDEN);
      return RestErrorMapper.mapProblemToCompletedResponse(
          RestErrorMapper.mapErrorToProblem(exception));
    }

    if (roleServices
        .withAuthentication(authenticationProvider.getAnonymousCamundaAuthentication())
        .hasMembersOfType(DefaultRole.ADMIN.getId(), EntityType.USER)) {
      final var exception = new ServiceException(ADMIN_EXISTS_ERROR_MESSAGE, Status.FORBIDDEN);
      return RestErrorMapper.mapProblemToCompletedResponse(
          RestErrorMapper.mapErrorToProblem(exception));
    }

    return RequestMapper.toUserRequest(
            request, securityConfiguration.getCompiledIdValidationPattern())
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            dto ->
                RequestMapper.executeServiceMethod(
                    () ->
                        userServices
                            .withAuthentication(
                                authenticationProvider.getAnonymousCamundaAuthentication())
                            .createInitialAdminUser(dto),
                    ResponseMapper::toUserCreateResponse));
  }
}
