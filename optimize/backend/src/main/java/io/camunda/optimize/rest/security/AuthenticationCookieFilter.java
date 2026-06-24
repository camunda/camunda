/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security;

import io.camunda.optimize.service.security.AuthCookieService;
import io.camunda.optimize.service.security.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;

public class AuthenticationCookieFilter extends AbstractPreAuthenticatedProcessingFilter {

  private final SessionService sessionService;

  public AuthenticationCookieFilter(
      final SessionService sessionService, final AuthenticationManager authenticationManager) {
    this.sessionService = sessionService;
    setAuthenticationManager(authenticationManager);
  }

  public AuthenticationCookieFilter(final SessionService sessionService) {
    this.sessionService = sessionService;
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
