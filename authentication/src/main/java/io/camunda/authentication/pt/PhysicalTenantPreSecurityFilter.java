/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Servlet {@link Filter} that extracts the physical tenant id from tenant-prefixed request paths
 * ({@code /physical-tenants/{id}/...}) and stamps it onto the request via {@link
 * PhysicalTenantContext} before Spring Security's filter chain runs.
 *
 * <p>Spring Security's filter chains (including the per-tenant API chains built by CSL from {@link
 * io.camunda.security.api.model.config.ScopedSecurityDescriptor}s) execute inside Spring Security's
 * {@code FilterChainProxy}, which runs at a fixed order. The existing MVC {@code
 * PhysicalTenantInterceptor} fires later — after the security filter chain — so basic-auth
 * processing cannot rely on it. This filter runs at a lower order (before the security filter
 * chain) to ensure the tenant id is available to:
 *
 * <ul>
 *   <li>{@link BasicAuthUserDetailsAdapter}, which calls {@link PhysicalTenantContext#current()} to
 *       look up users from the correct per-tenant {@code UserServices}.
 * </ul>
 *
 * <p>Only paths matching {@code /physical-tenants/{id}/...} are processed; all other requests pass
 * through unchanged with no attribute set. The {@code id} segment is not validated against the set
 * of configured tenants — CSL's security chain will reject the request with 401/403 if the tenant
 * is unknown or the credentials do not match.
 */
public final class PhysicalTenantPreSecurityFilter implements Filter {

  static final String PHYSICAL_TENANT_PATH_PREFIX = "/physical-tenants/";
  private static final Logger LOG = LoggerFactory.getLogger(PhysicalTenantPreSecurityFilter.class);

  private final Set<String> configuredTenantIds;

  /**
   * @param configuredTenantIds the set of tenant ids for which PT-prefixed paths are active;
   *     requests for unknown ids pass through unmodified (CSL's chain will reject them)
   */
  public PhysicalTenantPreSecurityFilter(final Set<String> configuredTenantIds) {
    this.configuredTenantIds = Set.copyOf(configuredTenantIds);
  }

  @Override
  public void doFilter(
      final ServletRequest servletRequest,
      final ServletResponse servletResponse,
      final FilterChain chain)
      throws IOException, ServletException {
    if (servletRequest instanceof final HttpServletRequest request) {
      extractTenantId(request);
    }
    chain.doFilter(servletRequest, servletResponse);
  }

  private void extractTenantId(final HttpServletRequest request) {
    final String path = request.getRequestURI();
    if (path == null || !path.startsWith(PHYSICAL_TENANT_PATH_PREFIX)) {
      return;
    }
    // Extract the segment after /physical-tenants/
    final int start = PHYSICAL_TENANT_PATH_PREFIX.length();
    final int slash = path.indexOf('/', start);
    final String tenantId = slash > start ? path.substring(start, slash) : null;
    if (tenantId == null || tenantId.isEmpty()) {
      return;
    }
    PhysicalTenantContext.setPhysicalTenantId(request, tenantId);
    LOG.debug("Pre-security: resolved physical tenant '{}' from path '{}'", tenantId, path);
  }
}
