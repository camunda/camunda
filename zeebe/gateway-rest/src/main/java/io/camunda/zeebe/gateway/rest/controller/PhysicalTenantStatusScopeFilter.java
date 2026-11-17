/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static io.camunda.spring.utils.PhysicalTenantContext.PHYSICAL_TENANTS_PATH_SEGMENT;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Scopes {@code GET /v2/status} to the default physical tenant, per ADR 001 D3 (see {@code
 * docs/adr/management/001-physical-tenant-health-status-topology.md}).
 *
 * <p>{@code /v2/status} and {@code /physical-tenants/default/v2/status} are the only forms this
 * endpoint is exposed under; both are handled normally by {@code StatusController} and reach it
 * unaffected by this filter. Every {@code /physical-tenants/{id}/v2/status} request for any other
 * id — whether that id belongs to a configured physical tenant or not — gets rejected here with the
 * exact same 404, so unauthenticated callers cannot use response differences (e.g. 401 vs. 404) to
 * enumerate physical tenant ids.
 *
 * <p>The rejection writes a fixed, path-independent body directly rather than calling {@link
 * HttpServletResponse#sendError}: {@code sendError} triggers the servlet container's error-page
 * dispatch to {@code GlobalErrorController}, whose {@code ProblemDetail} embeds the request path in
 * its {@code instance} field — that would make the response for an existing non-default tenant
 * distinguishable from an unknown one, exactly what this filter must prevent.
 *
 * <p>{@code ApiFiltersConfiguration} registers this to run before Spring Security's {@code
 * FilterChainProxy}, alongside {@link PhysicalTenantFilter}: the rejection must happen before
 * per-physical-tenant security chain selection, so a configured non-default tenant and an unknown
 * one are indistinguishable to unauthenticated callers. See ADR-0003 for why pre-security filters
 * are the established mechanism for physical-tenant-aware routing decisions.
 */
public final class PhysicalTenantStatusScopeFilter extends HttpFilter {

  private static final String STATUS_SUFFIX = "/v2/status";

  // Fixed, path-independent body: identical for every rejected request, so an existing
  // non-default tenant and an unknown one are indistinguishable. Deliberately does not use
  // CamundaProblemDetail / GlobalErrorController (see class javadoc) — those embed the request
  // path.
  private static final String NOT_FOUND_BODY =
      """
      {"type":"about:blank","status":404,"title":"Not Found"}""";
  private static final byte[] NOT_FOUND_BODY_BYTES =
      NOT_FOUND_BODY.getBytes(StandardCharsets.UTF_8);

  @Override
  protected void doFilter(
      final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain)
      throws IOException, ServletException {
    if (isNonDefaultTenantStatusRequest(request)) {
      writeNotFound(response);
      return;
    }
    chain.doFilter(request, response);
  }

  private static void writeNotFound(final HttpServletResponse response) throws IOException {
    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    response.setContentType("application/problem+json");
    response.setContentLength(NOT_FOUND_BODY_BYTES.length);
    response.getOutputStream().write(NOT_FOUND_BODY_BYTES);
  }

  private boolean isNonDefaultTenantStatusRequest(final HttpServletRequest request) {
    final String path = contextRelativePath(request);
    if (path == null || !path.startsWith(PHYSICAL_TENANTS_PATH_SEGMENT)) {
      return false;
    }

    final int start = PHYSICAL_TENANTS_PATH_SEGMENT.length();
    final int slash = path.indexOf('/', start);
    if (slash <= start) {
      // no id segment, or an empty one, follows the prefix
      return false;
    }

    final String tenantId = path.substring(start, slash);
    if (DEFAULT_PHYSICAL_TENANT_ID.equals(tenantId)) {
      return false;
    }

    final String remainder = path.substring(slash);
    return STATUS_SUFFIX.equals(remainder) || (STATUS_SUFFIX + "/").equals(remainder);
  }

  private static String contextRelativePath(final HttpServletRequest request) {
    final String uri = request.getRequestURI();
    if (uri == null) {
      return null;
    }
    final String contextPath = request.getContextPath();
    if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
      return uri.substring(contextPath.length());
    }
    return uri;
  }
}
