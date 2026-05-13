/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.security.spring.security.CamundaSecurityFilterChainConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Echoes the active {@link CsrfToken} as an {@code X-CSRF-TOKEN} response header on every
 * authenticated webapp / API GET response. The host SPAs (notably the admin webapp) cache the token
 * from this response header into {@code sessionStorage} and use it on subsequent state-changing
 * requests. Without this filter the SPA only has the {@code X-CSRF-TOKEN} cookie to read, and on
 * cold-tab navigation it races the cookie read against the first POST, surfacing as 401 "Invalid
 * CSRF Token 'null'" from the v2 API.
 *
 * <p>Mirrors the {@code csrfHeaderFilter} OC carried before adopting the camunda-security-library
 * filter chains. CSL's chains only set the header on login-success / sso-callback paths; this
 * filter restores the per-request echo OC's SPAs depend on. Lifting the behaviour into CSL is
 * tracked at <a href="https://github.com/camunda/camunda-security-library/issues/202">CSL #202</a>;
 * once that ships this filter can go away.
 *
 * <p>Registered as a global servlet filter ordered after Spring Security's chain (see {@code
 * WebSecurityConfig#csrfResponseHeaderFilterRegistration}) so the {@code CsrfToken} request
 * attribute is populated by {@code CsrfFilter} before this filter's post-chain logic reads it.
 */
public class CsrfResponseHeaderFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {
    filterChain.doFilter(request, response);
    if (shouldAddCsrf(request)) {
      final CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
      if (token != null && !response.isCommitted()) {
        response.setHeader(CamundaSecurityFilterChainConstants.X_CSRF_TOKEN, token.getToken());
      }
    }
  }

  private static boolean shouldAddCsrf(final HttpServletRequest request) {
    final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      return false;
    }
    if (!"GET".equalsIgnoreCase(request.getMethod())) {
      return false;
    }
    final String path = request.getRequestURI();
    return path == null || !path.contains(CamundaSecurityFilterChainConstants.LOGOUT_URL);
  }
}
