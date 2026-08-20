/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.security.spring.spi.OidcAuthenticationEntryPoint;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

/**
 * Entry point for the CSL OIDC webapp chain that restores Optimize's legacy "API returns 401,
 * navigation redirects to login" contract. See <a
 * href="https://github.com/camunda/camunda-security-library/blob/main/docs/adr/0038-optimize-reuses-stateful-oidc-webapp-chain.md">ADR-0038</a>.
 *
 * <p>CSL's built-in default only distinguishes bearer API calls (an {@code Authorization} header
 * yields 401) from browser navigations (a 302 to the IdP). Optimize's single-page app authenticates
 * its XHR calls with the server-side session cookie and sends no {@code Authorization} header, so
 * those calls would receive a 302 to the OIDC authorization endpoint. The browser follows that
 * redirect cross-origin to the IdP as a {@code fetch}, CORS blocks it, and the app hangs on a
 * loading spinner (for example after logout, when every follow-up XHR is unauthenticated).
 *
 * <p>This entry point returns 401 for {@code /api/**} (the SPA's XHR surface) so the frontend's
 * {@code PrivateRoute} handler reloads the page, which then navigates to the login. Every other
 * unauthenticated request (SPA route loads, static resources) keeps the 302-to-IdP redirect.
 */
public final class OptimizeOidcAuthenticationEntryPoint implements OidcAuthenticationEntryPoint {

  private static final String API_PATH = "/api";

  private final AuthenticationEntryPoint apiEntryPoint =
      new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
  private final AuthenticationEntryPoint navigationEntryPoint;

  public OptimizeOidcAuthenticationEntryPoint(final String loginRedirectTarget) {
    navigationEntryPoint = new LoginUrlAuthenticationEntryPoint(loginRedirectTarget);
  }

  @Override
  public void commence(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final AuthenticationException authException)
      throws IOException, ServletException {
    final String path = request.getRequestURI().substring(request.getContextPath().length());
    // Match the bearer/API surface exactly like Spring's "/api/**" does, i.e. "/api" and
    // "/api/...".
    if (path.equals(API_PATH) || path.startsWith(API_PATH + "/")) {
      apiEntryPoint.commence(request, response, authException);
    } else {
      navigationEntryPoint.commence(request, response, authException);
    }
  }
}
