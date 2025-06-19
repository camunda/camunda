/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.setup;

import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.service.RoleServices;
import io.camunda.service.UserServices;
import io.camunda.service.exception.ForbiddenException;
import io.camunda.zeebe.gateway.protocol.rest.UserRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.ResponseMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import io.camunda.zeebe.protocol.record.value.DefaultRole;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
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

  public SetupController(
      final UserServices userServices,
      final RoleServices roleServices,
      final SecurityConfiguration securityConfiguration) {
    this.userServices = userServices;
    this.roleServices = roleServices;
    this.securityConfiguration = securityConfiguration;
  }

  @CamundaPostMapping(path = "/user")
  public CompletableFuture<ResponseEntity<Object>> createAdminUser(
      @RequestBody final UserRequest request) {
    if (securityConfiguration.getAuthentication().getMethod() != AuthenticationMethod.BASIC) {
      final var exception = new ForbiddenException(WRONG_AUTHENTICATION_METHOD_ERROR_MESSAGE);
      return RestErrorMapper.mapProblemToCompletedResponse(
          RestErrorMapper.mapForbiddenExceptionToProblem(exception));
    }

    if (roleServices
        .withAuthentication(RequestMapper.getAnonymousAuthentication())
        .hasMembersOfType(DefaultRole.ADMIN.getId(), EntityType.USER)) {
      final var exception = new ForbiddenException(ADMIN_EXISTS_ERROR_MESSAGE);
      return RestErrorMapper.mapProblemToCompletedResponse(
          RestErrorMapper.mapForbiddenExceptionToProblem(exception));
    }

    return RequestMapper.toUserDTO(request)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            dto ->
                RequestMapper.executeServiceMethod(
                    () ->
                        userServices
                            .withAuthentication(RequestMapper.getAnonymousAuthentication())
                            .createInitialAdminUser(dto),
                    ResponseMapper::toUserCreateResponse));
  }
}
