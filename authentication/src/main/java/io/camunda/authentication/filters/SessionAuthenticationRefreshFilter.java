/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.filters;

import io.camunda.security.auth.CamundaAuthenticationProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class SessionAuthenticationRefreshFilter extends OncePerRequestFilter {
  // TODO move to a configuration class
  private static final Duration SESSION_REFRESH_INTERVAL = Duration.ofSeconds(120L);
  private static final String LAST_REFRESH_ATTR = "AUTH_LAST_REFRESH";
  private final CamundaAuthenticationProvider authenticationProvider;
  private final Supplier<SecurityContext> securityContextSupplier;

  public SessionAuthenticationRefreshFilter(
      final CamundaAuthenticationProvider authenticationProvider) {
    this(authenticationProvider, SecurityContextHolder::getContext);
  }

  public SessionAuthenticationRefreshFilter(
      final CamundaAuthenticationProvider authenticationProvider,
      final Supplier<SecurityContext> securityContextSupplier) {
    this.authenticationProvider = authenticationProvider;
    this.securityContextSupplier = securityContextSupplier;
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {
    final var session = request.getSession(false);
    if (session != null && securityContextSupplier.get().getAuthentication() != null) {
      handleSessionRefresh(session);
    }
    filterChain.doFilter(request, response);
  }

  private void handleSessionRefresh(final HttpSession session) {
    final Instant now = Instant.now();
    final Instant lastRefresh = (Instant) session.getAttribute(LAST_REFRESH_ATTR);

    // Initialize if first access
    if (lastRefresh == null) {
      session.setAttribute(LAST_REFRESH_ATTR, now);
      return;
    }

    // Check if refresh needed
    // TODO Lock or find another way to skip concurrent update
    if (ChronoUnit.MILLIS.between(lastRefresh, now) >= SESSION_REFRESH_INTERVAL.toMillis()) {
      authenticationProvider.refresh();
      session.setAttribute(LAST_REFRESH_ATTR, now);
    }
  }
}
