/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.filters;

import static java.time.temporal.ChronoUnit.MILLIS;

import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.AuthenticationConfiguration;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.web.filter.OncePerRequestFilter;

public class SessionAuthenticationRefreshFilter extends OncePerRequestFilter {
  private static final String LAST_REFRESH_ATTR = "AUTH_LAST_REFRESH";
  private final CamundaAuthenticationProvider authenticationProvider;
  private final Duration authenticationRefreshInterval;

  public SessionAuthenticationRefreshFilter(
      final CamundaAuthenticationProvider authenticationProvider,
      final AuthenticationConfiguration authenticationConfiguration) {
    this.authenticationProvider = authenticationProvider;
    authenticationRefreshInterval =
        Duration.parse(authenticationConfiguration.getAuthenticationRefreshInterval());
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {
    final var session = request.getSession(false);
    if (session != null && authenticationProvider.getCamundaAuthentication() != null) {
      handleSessionRefresh(session);
    }
    filterChain.doFilter(request, response);
  }

  private void handleSessionRefresh(final HttpSession session) {
    final Instant now = Instant.now();
    Instant lastRefresh = (Instant) session.getAttribute(LAST_REFRESH_ATTR);

    // Initialize if first access
    if (lastRefresh == null) {
      session.setAttribute(LAST_REFRESH_ATTR, now);
      session.setAttribute(LAST_REFRESH_ATTR + "_LOCK", new Object());
      return;
    }

    // Check if refresh needed
    if (isRefreshRequired(lastRefresh, now)) {
      synchronized (session.getAttribute(LAST_REFRESH_ATTR + "_LOCK")) {
        lastRefresh = (Instant) session.getAttribute(LAST_REFRESH_ATTR);
        // Double check if refresh still needed
        if (isRefreshRequired(lastRefresh, now)) {
          authenticationProvider.refresh();
          session.setAttribute(LAST_REFRESH_ATTR, now);
        }
      }
    }
  }

  private boolean isRefreshRequired(final Instant lastRefresh, final Instant now) {
    return MILLIS.between(lastRefresh, now) >= authenticationRefreshInterval.toMillis();
  }
}
