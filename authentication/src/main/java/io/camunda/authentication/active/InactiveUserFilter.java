/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.active;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.spring.utils.PhysicalTenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects requests of authenticated users that are deactivated ({@code active=false}) with 403.
 *
 * <p>Must be registered with an order after Spring Security's filter chain proxy: filters ordered
 * after it run inside the proxy's call, where the {@code SecurityContext} of both bearer-token API
 * requests and session-based webapp requests is populated. Requests of clients (no username) and
 * unauthenticated requests pass untouched.
 */
public final class InactiveUserFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(InactiveUserFilter.class);

  private final CamundaAuthenticationProvider authenticationProvider;
  private final ActiveUserChecker activeUserChecker;

  public InactiveUserFilter(
      final CamundaAuthenticationProvider authenticationProvider,
      final ActiveUserChecker activeUserChecker) {
    this.authenticationProvider = authenticationProvider;
    this.activeUserChecker = activeUserChecker;
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {
    final var username = authenticatedUsername();
    if (username != null && !activeUserChecker.isActive(physicalTenantId(request), username)) {
      LOG.debug("Rejecting request of deactivated user '{}'", username);
      reject(request, response);
      return;
    }
    filterChain.doFilter(request, response);
  }

  private String authenticatedUsername() {
    final var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken) {
      LOG.debug("Skipping active-user check, the request is not authenticated: {}", authentication);
      return null;
    }
    try {
      final var camundaAuthentication = authenticationProvider.getCamundaAuthentication();
      if (camundaAuthentication == null || camundaAuthentication.isAnonymous()) {
        return null;
      }
      final var username = camundaAuthentication.authenticatedUsername();
      return username == null || username.isBlank() ? null : username;
    } catch (final RuntimeException e) {
      // Whoever needs the authentication downstream hits the same failure and reports it properly.
      LOG.debug("Skipping active-user check, the authentication could not be resolved", e);
      return null;
    }
  }

  private static String physicalTenantId(final HttpServletRequest request) {
    final var id = PhysicalTenantContext.getPhysicalTenantId(request);
    return id != null ? id : PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
  }

  private static void reject(final HttpServletRequest request, final HttpServletResponse response)
      throws IOException {
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    final var instance = request.getRequestURI().replace("\\", "\\\\").replace("\"", "\\\"");
    response
        .getWriter()
        .write(
            "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,"
                + "\"detail\":\"The user is deactivated.\",\"instance\":\""
                + instance
                + "\"}");
  }
}
