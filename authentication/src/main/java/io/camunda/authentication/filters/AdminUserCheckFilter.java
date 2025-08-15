/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.filters;

import io.camunda.authentication.config.MutualTlsProperties;
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
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

public class AdminUserCheckFilter extends OncePerRequestFilter {

  public static final String ADMIN_ROLE_ID = DefaultRole.ADMIN.getId();
  public static final String USER_MEMBERS = "users";
  public static final String REDIRECT_PATH = "/identity/setup";
  public static final String ASSETS_PATH = "/identity/assets";
  private static final Logger LOG = LoggerFactory.getLogger(AdminUserCheckFilter.class);
  private final SecurityConfiguration securityConfig;
  private final RoleServices roleServices;
  private final Optional<MutualTlsProperties> mtlsProperties;

  public AdminUserCheckFilter(
      final SecurityConfiguration securityConfig,
      final RoleServices roleServices,
      final Optional<MutualTlsProperties> mtlsProperties) {
    this.securityConfig = securityConfig;
    this.roleServices = roleServices;
    this.mtlsProperties = mtlsProperties;
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

    // Check if mTLS user has configured admin roles
    final boolean mtlsAuth = isMtlsAuthenticated(request);
    LOG.info("AdminUserCheckFilter - URI: {}, mTLS authenticated: {}", requestURI, mtlsAuth);

    if (mtlsAuth && mtlsHasAdminRole()) {
      LOG.info(
          "mTLS authenticated user with admin role detected, skipping admin user check for: {}",
          requestURI);
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

  private boolean isMtlsAuthenticated(final HttpServletRequest request) {
    // Check for client certificates in the request
    final Object certificates = request.getAttribute("jakarta.servlet.request.X509Certificate");
    if (certificates == null) {
      return false;
    }

    // Check if there's an mTLS authentication in the security context
    final var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof PreAuthenticatedAuthenticationToken) {
      final Object credentials = authentication.getCredentials();
      // MutualTlsAuthenticationProvider sets the certificate as credentials
      return credentials instanceof X509Certificate;
    }

    return false;
  }

  private boolean mtlsHasAdminRole() {
    if (mtlsProperties.isEmpty()) {
      return false;
    }

    final MutualTlsProperties props = mtlsProperties.get();
    if (props.getDefaultRoles() == null || props.getDefaultRoles().isEmpty()) {
      return false;
    }

    // Check if any of the configured default roles is an admin role
    for (final String role : props.getDefaultRoles()) {
      final String normalizedRole = role.startsWith("ROLE_") ? role : "ROLE_" + role;
      if (ADMIN_ROLE_ID.equals(normalizedRole)
          || "ROLE_ADMIN".equalsIgnoreCase(normalizedRole)
          || normalizedRole.toUpperCase().contains("ADMIN")) {
        LOG.info("mTLS configuration includes admin role: {}", normalizedRole);
        return true;
      }
    }

    LOG.debug(
        "mTLS configuration does not include admin roles. Configured roles: {}",
        props.getDefaultRoles());
    return false;
  }
}
