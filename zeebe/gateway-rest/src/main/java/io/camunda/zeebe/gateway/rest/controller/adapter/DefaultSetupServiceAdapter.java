/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.adapter;

import io.camunda.gateway.mapping.http.GatewayErrorMapper;
import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.mapping.http.mapper.UserMapper;
import io.camunda.gateway.mapping.http.validator.UserRequestValidator;
import io.camunda.gateway.protocol.model.UserRequest;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.security.validation.UserValidator;
import io.camunda.service.RoleServices;
import io.camunda.service.UserServices;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.zeebe.gateway.rest.controller.generated.SetupServiceAdapter;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import io.camunda.zeebe.protocol.record.value.DefaultRole;
import io.camunda.zeebe.protocol.record.value.EntityType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DefaultSetupServiceAdapter implements SetupServiceAdapter {

  public static final String WRONG_AUTHENTICATION_METHOD_ERROR_MESSAGE =
      "The initial admin user can only be created with basic authentication.";
  public static final String ADMIN_EXISTS_ERROR_MESSAGE =
      "Expected to create an initial admin user, but found existing admin users. Please ask your admin to create a new user with the '%s' role."
          .formatted(DefaultRole.ADMIN.getId());

  private final UserServices userServices;
  private final RoleServices roleServices;
  private final SecurityConfiguration securityConfiguration;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final UserMapper userMapper;

  public DefaultSetupServiceAdapter(
      final UserServices userServices,
      final RoleServices roleServices,
      final SecurityConfiguration securityConfiguration,
      final CamundaAuthenticationProvider authenticationProvider,
      final IdentifierValidator identifierValidator) {
    this.userServices = userServices;
    this.roleServices = roleServices;
    this.securityConfiguration = securityConfiguration;
    this.authenticationProvider = authenticationProvider;
    userMapper = new UserMapper(new UserRequestValidator(new UserValidator(identifierValidator)));
  }

  @Override
  public ResponseEntity<Object> createAdminUser(
      final UserRequest requestStrict, final CamundaAuthentication authentication) {
    if (securityConfiguration.getAuthentication().getMethod() != AuthenticationMethod.BASIC) {
      final var exception =
          new ServiceException(WRONG_AUTHENTICATION_METHOD_ERROR_MESSAGE, Status.FORBIDDEN);
      return RestErrorMapper.mapProblemToResponse(GatewayErrorMapper.mapErrorToProblem(exception));
    }

    final var anonymousAuth = authenticationProvider.getAnonymousCamundaAuthentication();
    if (roleServices.hasMembersOfType(DefaultRole.ADMIN.getId(), EntityType.USER, anonymousAuth)) {
      final var exception = new ServiceException(ADMIN_EXISTS_ERROR_MESSAGE, Status.FORBIDDEN);
      return RestErrorMapper.mapProblemToResponse(GatewayErrorMapper.mapErrorToProblem(exception));
    }

    return userMapper
        .toUserRequest(requestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            dto ->
                RequestExecutor.executeSync(
                    () -> userServices.createInitialAdminUser(dto, anonymousAuth),
                    ResponseMapper::toUserCreateResponse,
                    HttpStatus.CREATED));
  }
}
