/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.filters;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.RoleServices;
import io.camunda.zeebe.protocol.record.value.DefaultRole;
import io.camunda.zeebe.protocol.record.value.EntityType;
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

  public static final String ADMIN_ROLE_ID = DefaultRole.ADMIN.getId();
  public static final String USER_MEMBERS = "users";
  public static final String REDIRECT_PATH = "/admin/setup";
  public static final String ASSETS_PATH = "/admin/assets";
  private static final Logger LOG = LoggerFactory.getLogger(AdminUserCheckFilter.class);
  private final SecurityConfiguration securityConfig;
  private final RoleServices roleServices;

  public AdminUserCheckFilter(
      final SecurityConfiguration securityConfig, final RoleServices roleServices) {
    this.securityConfig = securityConfig;
    this.roleServices = roleServices;
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {
    // Skip redirect logic for the setup page itself to prevent redirect loops
    // Skip redirect logic for loading assets, e.g., CSS and JS files
    final var requestURI = request.getRequestURI();
    final String setupPath = request.getContextPath() + REDIRECT_PATH;
    if (requestURI.equals(setupPath) || requestURI.contains(ASSETS_PATH)) {
      filterChain.doFilter(request, response);
      return;
    }

    final var hasConfiguredAdminUser =
        !securityConfig
            .getInitialization()
            .getDefaultRoles()
            .getOrDefault(ADMIN_ROLE_ID, Map.of())
            .getOrDefault(USER_MEMBERS, Set.of())
            .isEmpty();

    if (hasConfiguredAdminUser) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      if (!roleServices
          .withAuthentication(CamundaAuthentication.anonymous())
          .hasMembersOfType(ADMIN_ROLE_ID, EntityType.USER)) {
        LOG.debug("No user with admin role exists. Redirecting to identity setup page.");
        final var redirectUrl = String.format("%s%s", request.getContextPath(), REDIRECT_PATH);
        response.sendRedirect(redirectUrl);
        return;
      }
    } catch (final RuntimeException ex) {
      // Don't redirect the request if an error occurs while checking for admin role members.
      LOG.error(
          "Error while searching for admin role members. This might indicate that secondary storage is down.",
          ex);
    }

    filterChain.doFilter(request, response);
  }
}
