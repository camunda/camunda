/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.filters;

import io.camunda.security.configuration.PhysicalTenantIdpRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gates {@code /{tenantId}/...} requests where {@code tenantId} matches a known physical tenant.
 *
 * <p>For OAuth2 (browser session) authentication, applies three checks in order:
 *
 * <ol>
 *   <li>The user is OAuth2-authenticated (else 401).
 *   <li>The user's authenticated IdP is assigned to the URL tenant per {@link
 *       PhysicalTenantIdpRegistry} (else 403).
 *   <li>The HTTP session's {@code boundTenantId} (set at login start by {@code
 *       TenantLoginController.startLogin}) equals the URL tenant (else 403).
 * </ol>
 *
 * <p>Bearer-token authenticated requests pass through unchanged — they don't carry a session and
 * are out of scope for the POC.
 *
 * <p>The first path segment {@code "login"} is never a tenant id (reserved by the picker URL
 * scheme), so {@code /login/{tenantId}} and {@code /login/{tenantId}/{idpId}} pass through this
 * filter without enforcement.
 *
 * <p>Wired into the OIDC chain by {@link io.camunda.authentication.config.WebSecurityConfig} via
 * {@code addFilterAfter(..., WebComponentAuthorizationCheckFilter.class)}; not picked up by
 * component scan.
 */
public class PhysicalTenantAuthorizationFilter extends OncePerRequestFilter {

  /** HTTP session attribute key carrying the tenant chosen at login start. */
  public static final String BOUND_TENANT_ATTRIBUTE = "boundPhysicalTenantId";

  private final PhysicalTenantIdpRegistry registry;

  public PhysicalTenantAuthorizationFilter(final PhysicalTenantIdpRegistry registry) {
    this.registry = registry;
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest req, final HttpServletResponse res, final FilterChain chain)
      throws IOException, ServletException {

    final var urlTenantId = firstPathSegment(req.getRequestURI());
    if (urlTenantId == null || !registry.tenantIds().contains(urlTenantId)) {
      chain.doFilter(req, res);
      return;
    }

    final var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "authentication required");
      return;
    }

    if (!(auth instanceof final OAuth2AuthenticationToken oauth)) {
      // bearer-token (non-OAuth2) auth — out of scope for the POC, pass through
      chain.doFilter(req, res);
      return;
    }

    final var registrationId = oauth.getAuthorizedClientRegistrationId();
    if (!registry.getIdpsForTenant(urlTenantId).contains(registrationId)) {
      res.sendError(
          HttpServletResponse.SC_FORBIDDEN,
          "IdP '" + registrationId + "' is not assigned to tenant '" + urlTenantId + "'");
      return;
    }

    final var session = req.getSession(false);
    final var boundTenant = session == null ? null : session.getAttribute(BOUND_TENANT_ATTRIBUTE);
    if (!urlTenantId.equals(boundTenant)) {
      res.sendError(
          HttpServletResponse.SC_FORBIDDEN,
          "Session is bound to tenant '" + boundTenant + "', not '" + urlTenantId + "'");
      return;
    }

    chain.doFilter(req, res);
  }

  private static String firstPathSegment(final String uri) {
    if (uri == null || uri.length() < 2 || uri.charAt(0) != '/') {
      return null;
    }
    final var slash = uri.indexOf('/', 1);
    return slash < 0 ? uri.substring(1) : uri.substring(1, slash);
  }
}
