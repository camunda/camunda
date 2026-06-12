/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import io.camunda.spring.utils.PhysicalTenantContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Servlet {@link Filter} that extracts the physical tenant id from tenant-prefixed request paths
 * ({@code /physical-tenants/{id}/...}) and stamps it onto the request via {@link
 * PhysicalTenantContext} before Spring Security's filter chain runs.
 *
 * <p>Spring Security's filter chains (including the per-tenant API chains built by CSL from {@link
 * io.camunda.security.api.model.config.ScopedSecurityDescriptor}s) execute inside Spring Security's
 * {@code FilterChainProxy}, which runs at a fixed order. An MVC {@code HandlerInterceptor} would
 * fire later — during dispatch, after the security filter chain — so components that consume the
 * tenant id <em>inside</em> the chain could not rely on it. This filter runs at a lower order
 * (before the security filter chain) so the id is on the request when those in-chain components
 * run, for example:
 *
 * <ul>
 *   <li>{@link BasicAuthUserDetailsAdapter}, which calls {@link PhysicalTenantContext#current()} to
 *       look up users from the correct per-tenant {@code UserServices};
 *   <li>per-tenant OIDC and webapp concerns such as tenant-specific session storage.
 * </ul>
 *
 * <p>Only paths matching {@code /physical-tenants/{id}/...} are processed; all other requests pass
 * through unchanged with no attribute set. The {@code id} segment is intentionally not validated
 * here: this filter is the single extraction point, and validation is left to CSL's security
 * chains. A request for an unconfigured tenant matches no per-scope chain and is rejected by CSL's
 * catch-all chain with 404; bad credentials for a configured tenant are rejected with 401/403. See
 * ADR-0003.
 */
public final class PhysicalTenantPreSecurityFilter implements Filter {

  private static final Logger LOG = LoggerFactory.getLogger(PhysicalTenantPreSecurityFilter.class);

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
    if (path == null || !path.startsWith(PhysicalTenantContext.PHYSICAL_TENANTS_PATH_SEGMENT)) {
      return;
    }
    // Extract the segment after /physical-tenants/
    final int start = PhysicalTenantContext.PHYSICAL_TENANTS_PATH_SEGMENT.length();
    final int slash = path.indexOf('/', start);
    final String tenantId = slash > start ? path.substring(start, slash) : null;
    if (tenantId == null || tenantId.isEmpty()) {
      return;
    }
    PhysicalTenantContext.setPhysicalTenantId(request, tenantId);
    LOG.trace("Pre-security: resolved physical tenant '{}' from path '{}'", tenantId, path);
  }
}
