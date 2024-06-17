/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security.authentication;

import io.camunda.identity.sdk.authentication.dto.AuthCodeDto;
import io.camunda.optimize.dto.optimize.query.security.CredentialsRequestDto;
import io.camunda.optimize.service.security.AuthCookieService;
import io.camunda.optimize.service.security.SessionService;
import io.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import jakarta.ws.rs.NotSupportedException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(CCSaaSCondition.class)
@Slf4j
public class CCSaaSAuthenticationService extends AbstractAuthenticationService {

  public static final String INVALID_ENDPOINT_MESSAGE =
      "Requests to this endpoint are not valid in Cloud mode";

  public CCSaaSAuthenticationService(
      final SessionService sessionService, final AuthCookieService authCookieService) {
    super(sessionService, authCookieService);
  }

  @Override
  public Response authenticateUser(
      final ContainerRequestContext requestContext, final CredentialsRequestDto credentials) {
    throw new NotSupportedException(INVALID_ENDPOINT_MESSAGE);
  }

  @Override
  public Response loginCallback(
      final ContainerRequestContext requestContext, final AuthCodeDto authCode) {
    throw new NotSupportedException(INVALID_ENDPOINT_MESSAGE);
  }

  @Override
  public Response logout(final ContainerRequestContext requestContext) {
    throw new NotSupportedException(INVALID_ENDPOINT_MESSAGE);
  }
}
