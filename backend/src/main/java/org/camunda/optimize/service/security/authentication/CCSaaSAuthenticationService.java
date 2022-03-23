/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security.authentication;

import io.camunda.iam.sdk.authentication.dto.AuthCodeDto;
import io.camunda.iam.sdk.authentication.dto.LogoutRequestDto;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.security.CredentialsRequestDto;
import org.camunda.optimize.service.security.AuthCookieService;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotSupportedException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Response;

@Component
@Conditional(CCSaaSCondition.class)
@Slf4j
public class CCSaaSAuthenticationService extends AbstractAuthenticationService {

  public static final String INVALID_ENDPOINT_MESSAGE = "Requests to this endpoint are not valid in Cloud mode";

  public CCSaaSAuthenticationService(final SessionService sessionService,
                                     final AuthCookieService authCookieService) {
    super(sessionService, authCookieService);
  }

  @Override
  public Response authenticateUser(final ContainerRequestContext requestContext,
                                   final CredentialsRequestDto credentials) {
    throw new NotSupportedException(INVALID_ENDPOINT_MESSAGE);
  }

  @Override
  public Response loginCallback(final ContainerRequestContext requestContext,
                                final AuthCodeDto authCode) {
    throw new NotSupportedException(INVALID_ENDPOINT_MESSAGE);
  }

  @Override
  public Response logoutCallback(final ContainerRequestContext requestContext,
                                 final LogoutRequestDto logoutRequestDto) {
    throw new NotSupportedException(INVALID_ENDPOINT_MESSAGE);
  }

  @Override
  public Response logout(final ContainerRequestContext requestContext) {
    throw new NotSupportedException(INVALID_ENDPOINT_MESSAGE);
  }

}
