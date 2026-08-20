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
import io.camunda.optimize.rest.exceptions.NotSupportedException;
import io.camunda.optimize.service.security.AuthCookieService;
import io.camunda.optimize.service.security.SessionService;
import io.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(CCSaaSCondition.class)
public class CCSaaSAuthenticationService extends AbstractAuthenticationService {

  public static final String INVALID_ENDPOINT_MESSAGE =
      "Requests to this endpoint are not valid in Cloud mode";
  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(CCSaaSAuthenticationService.class);

  public CCSaaSAuthenticationService(
      final SessionService sessionService, final AuthCookieService authCookieService) {
    super(sessionService, authCookieService);
  }

  @Override
  public void authenticateUser(final CredentialsRequestDto credentials) {
    throw new NotSupportedException(INVALID_ENDPOINT_MESSAGE);
  }

  @Override
  public void loginCallback(
      final AuthCodeDto authCode, final URI uri, final HttpServletResponse response)
      throws IOException {
    throw new NotSupportedException(INVALID_ENDPOINT_MESSAGE);
  }

  @Override
  public void logout(final Cookie[] cookies, final HttpServletResponse response) {
    throw new NotSupportedException(INVALID_ENDPOINT_MESSAGE);
  }
}
