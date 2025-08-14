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
  public static final String LAST_REFRESH_ATTR = "AUTH_LAST_REFRESH";
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
    try {
      handleAuthenticationRefresh(request);
    } catch (final Exception e) {
      logger.debug("The session can't be refreshed.");
    }
    filterChain.doFilter(request, response);
  }

  private void handleAuthenticationRefresh(final HttpServletRequest request) {

    final var session = request.getSession(false);
    if (session != null && authenticationProvider.getCamundaAuthentication() != null) {
      final Instant now = Instant.now();
      final Instant lastRefresh = (Instant) session.getAttribute(LAST_REFRESH_ATTR);
      if (lastRefresh == null) {
        initializeRefreshAttributes(session, now);
        return;
      }
      if (isRefreshRequired(lastRefresh, now)) {
        lockAndRefresh(session, now);
      }
    }
  }

  private void lockAndRefresh(final HttpSession session, final Instant now) {
    final Instant lastRefresh;
    synchronized (session.getAttribute(LAST_REFRESH_ATTR + "_LOCK")) {
      lastRefresh = (Instant) session.getAttribute(LAST_REFRESH_ATTR);
      if (isRefreshRequired(lastRefresh, now)) {
        authenticationProvider.refresh();
        session.setAttribute(LAST_REFRESH_ATTR, now);
      }
    }
  }

  private static void initializeRefreshAttributes(final HttpSession session, final Instant now) {
    session.setAttribute(LAST_REFRESH_ATTR, now);
    session.setAttribute(LAST_REFRESH_ATTR + "_LOCK", new Object());
  }

  private boolean isRefreshRequired(final Instant lastRefresh, final Instant now) {
    return MILLIS.between(lastRefresh, now) >= authenticationRefreshInterval.toMillis();
  }
}
