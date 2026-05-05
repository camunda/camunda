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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * Gates {@code /{tenantId}/...} requests where the first path segment matches a known physical
 * tenant. Reads the bound tenant from the {@code BOUND_TENANT_ATTRIBUTE} HTTP session attribute
 * (written by {@link io.camunda.authentication.config.TenantAwareOAuth2AuthorizationRequestResolver
 * TenantAwareOAuth2AuthorizationRequestResolver} during OIDC entry).
 *
 * <p>Path extraction uses {@link UrlPathHelper#getPathWithinApplication(HttpServletRequest)} so the
 * filter is correct under non-default servlet context paths.
 *
 * <p>Backward compatibility: when the registry is empty the filter is a no-op. Bearer-token traffic
 * ({@link JwtAuthenticationToken}) is out of scope for this POC and passes through.
 */
public final class TenantBindingEnforcementFilter extends OncePerRequestFilter {

  /** HTTP session attribute key carrying the tenant the user is bound to for this session. */
  public static final String BOUND_TENANT_ATTRIBUTE = "boundPhysicalTenantId";

  private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();

  private final PhysicalTenantIdpRegistry registry;

  public TenantBindingEnforcementFilter(final PhysicalTenantIdpRegistry registry) {
    this.registry = registry;
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest req, final HttpServletResponse res, final FilterChain chain)
      throws IOException, ServletException {

    if (registry.tenantIds().isEmpty()) {
      // BC: no tenants configured — the feature is dormant.
      chain.doFilter(req, res);
      return;
    }

    final var urlTenantId = firstPathSegment(req);
    if (urlTenantId == null || !registry.tenantIds().contains(urlTenantId)) {
      // Not a tenant-scoped request.
      chain.doFilter(req, res);
      return;
    }

    final var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "authentication required");
      return;
    }

    if (auth instanceof JwtAuthenticationToken) {
      // Bearer-token API auth — out of scope for tenant binding in the POC.
      chain.doFilter(req, res);
      return;
    }

    if (!(auth instanceof final OAuth2AuthenticationToken oauth)) {
      // Unknown authentication type (basic auth, anonymous, custom). Defensive pass-through:
      // the OIDC chain only carries OAuth2 / JWT auth in practice; if something else lands
      // here, it isn't ours to gate.
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
          boundTenant == null
              ? "Session is not bound to any tenant; re-login through the picker"
              : "Session is bound to a different tenant than the URL");
      return;
    }

    chain.doFilter(req, res);
  }

  private static String firstPathSegment(final HttpServletRequest req) {
    final var path = PATH_HELPER.getPathWithinApplication(req);
    if (path == null || path.length() < 2 || path.charAt(0) != '/') {
      return null;
    }
    final var slash = path.indexOf('/', 1);
    return slash < 0 ? path.substring(1) : path.substring(1, slash);
  }
}
