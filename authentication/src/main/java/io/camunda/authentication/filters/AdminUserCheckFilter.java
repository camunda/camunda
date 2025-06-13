/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.filters;

import io.camunda.search.query.RoleQuery;
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
      final var adminRoleHasMembers =
          roleServices
                  .searchMembers(
                      RoleQuery.of(
                          builder ->
                              builder.filter(
                                  filter ->
                                      filter
                                          .joinParentId(ADMIN_ROLE_ID)
                                          .memberType(EntityType.USER))))
                  .total()
              > 0;

      if (!adminRoleHasMembers) {
        LOG.debug("No user with admin role exists. Redirecting to identity setup page.");
        response.sendRedirect(String.format("%s/identity/setup", request.getContextPath()));
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
