/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.security;

import lombok.AllArgsConstructor;
import org.camunda.optimize.service.security.AuthCookieService;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

@AllArgsConstructor
public class AuthenticationCookieFilter extends AbstractPreAuthenticatedProcessingFilter {

  private final SessionService sessionService;

  public AuthenticationCookieFilter(final SessionService sessionService,
                                    final AuthenticationManager authenticationManager) {
    this.sessionService = sessionService;
    setAuthenticationManager(authenticationManager);
  }

  @Override
  protected Object getPreAuthenticatedPrincipal(final HttpServletRequest request) {
    return getJwtAuthenticationToken(request)
      .filter(sessionService::isValidToken)
      .flatMap(AuthCookieService::getTokenSubject)
      .orElse(null);
  }

  @Override
  protected Object getPreAuthenticatedCredentials(final HttpServletRequest request) {
    return getJwtAuthenticationToken(request).orElse(null);
  }

  private Optional<String> getJwtAuthenticationToken(final HttpServletRequest request) {
    return AuthCookieService.getAuthCookieToken(request);
  }
}
