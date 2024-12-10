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
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;

public abstract class AbstractAuthenticationService {

  protected final SessionService sessionService;
  protected final AuthCookieService authCookieService;

  public AbstractAuthenticationService(
      final SessionService sessionService, final AuthCookieService authCookieService) {
    this.sessionService = sessionService;
    this.authCookieService = authCookieService;
  }

  public abstract void authenticateUser(CredentialsRequestDto credentials);

  public abstract void loginCallback(
      final AuthCodeDto authCode, final URI uri, final HttpServletResponse response)
      throws IOException;

  public abstract void logout(final Cookie[] cookies, final HttpServletResponse response);

  public String testAuthentication() {
    return "OK";
  }
}
