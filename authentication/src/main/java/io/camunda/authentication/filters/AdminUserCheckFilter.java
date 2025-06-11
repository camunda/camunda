/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.filters;

import io.camunda.security.configuration.SecurityConfiguration;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

public class AdminUserCheckFilter extends OncePerRequestFilter {
  private static final Logger LOG = LoggerFactory.getLogger(AdminUserCheckFilter.class);
  public static final String USER_MEMBERS = "users";
  private final SecurityConfiguration securityConfig;

  public AdminUserCheckFilter(final SecurityConfiguration securityConfig) {
    this.securityConfig = securityConfig;
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {
    final var hasConfiguredAdminUser =
        !securityConfig
            .getInitialization()
            .getDefaultRoles()
            .getOrDefault("admin", Map.of())
            .getOrDefault(USER_MEMBERS, Set.of())
            .isEmpty();

    if (!hasConfiguredAdminUser) {
      LOG.info("No admin user configured. Redirecting to identity setup page.");
      response.sendRedirect(String.format("%s/identity/setup", request.getContextPath()));
    }

    filterChain.doFilter(request, response);
  }
}
