/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

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
 * PhysicalTenantContext}.
 *
 * <p>{@code ApiFiltersConfiguration} registers it to run before Spring Security's {@code
 * FilterChainProxy}, so the id is on the request when components <em>inside</em> the security chain
 * consume it — e.g. per-tenant basic-auth user resolution, and per-tenant OIDC/session handling. An
 * MVC {@code HandlerInterceptor} would fire later (during dispatch, after the chain) and so could
 * not serve those in-chain consumers.
 *
 * <p>Only paths matching {@code /physical-tenants/{id}/...} are processed; other requests pass
 * through unchanged. The {@code id} is intentionally not validated here — this is the single
 * extraction point, and rejection of unknown tenants is left to CSL's security chains (an
 * unconfigured tenant matches no per-scope chain and is rejected by the catch-all with 404). See
 * ADR-0003.
 */
public final class PhysicalTenantFilter implements Filter {

  private static final Logger LOG = LoggerFactory.getLogger(PhysicalTenantFilter.class);

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
    LOG.trace("Resolved physical tenant '{}' from path '{}'", tenantId, path);
  }
}
