/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.context;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ServletRequestPathUtils;

/**
 * Validates the {@code physicalTenantId} captured from the MCP request URI and exposes the resolved
 * id on the request via {@link PhysicalTenantContext}, mirroring the REST {@code
 * io.camunda.zeebe.gateway.rest.interceptor.PhysicalTenantInterceptor}.
 *
 * <p>MCP endpoints are served by Spring AI {@code RouterFunction}s registered on the unprefixed
 * paths (e.g. {@code /mcp/cluster}). To support the same physical-tenant addressing scheme as
 * {@code /v2/physical-tenants/{physicalTenantId}/...}, this filter:
 *
 * <ul>
 *   <li>matches incoming MCP requests against {@code
 *       /mcp/physical-tenants/{physicalTenantId}/<rest>};
 *   <li>rejects unknown ids with HTTP 404 before they reach any handler;
 *   <li>stores the resolved id on the request scope; and
 *   <li>rewrites the request URI to the unprefixed path ({@code /mcp/<rest>}) so the existing
 *       transport router function picks it up without any change.
 * </ul>
 *
 * <p>For unprefixed MCP requests the filter still records the {@link
 * PhysicalTenantContext#DEFAULT_PHYSICAL_TENANT_ID default} tenant id so tools can call {@link
 * PhysicalTenantContext#current()} consistently regardless of the URL used.
 */
public class PhysicalTenantMcpFilter extends OncePerRequestFilter {

  /** Path prefix recognised as MCP traffic. */
  static final String MCP_PREFIX = "/mcp/";

  /** Captures {@code /mcp/physical-tenants/{physicalTenantId}/<rest>}. */
  private static final Pattern TENANT_PATTERN =
      Pattern.compile("^/mcp/physical-tenants/([^/]+)(/.*)$");

  private final PhysicalTenantResolver resolver;

  public PhysicalTenantMcpFilter(final PhysicalTenantResolver resolver) {
    this.resolver = resolver;
  }

  @Override
  protected boolean shouldNotFilter(final HttpServletRequest request) {
    final String uri = request.getRequestURI();
    return uri == null || !uri.startsWith(MCP_PREFIX);
  }

  @Override
  protected void doFilterInternal(
      final @NonNull HttpServletRequest request,
      final @NonNull HttpServletResponse response,
      final @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    final String uri = request.getRequestURI();
    final Matcher matcher = TENANT_PATTERN.matcher(uri);
    if (!matcher.matches()) {
      // Unprefixed MCP request: default tenant.
      PhysicalTenantContext.setPhysicalTenantId(
          request, PhysicalTenantContext.DEFAULT_PHYSICAL_TENANT_ID);
      filterChain.doFilter(request, response);
      return;
    }

    final String tenantId = matcher.group(1);
    final String tail = matcher.group(2);

    if (!resolver.exists(tenantId)) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND, "Unknown physical tenant: " + tenantId);
      return;
    }

    PhysicalTenantContext.setPhysicalTenantId(request, tenantId);

    final String rewrittenUri = MCP_PREFIX.substring(0, MCP_PREFIX.length() - 1) + tail;
    final HttpServletRequest wrapped = new RewrittenUriRequest(request, rewrittenUri);
    // Refresh the cached parsed RequestPath so RouterFunctions match the rewritten URI.
    ServletRequestPathUtils.parseAndCache(wrapped);
    filterChain.doFilter(wrapped, response);
  }

  /**
   * {@link HttpServletRequestWrapper} that exposes a rewritten URI so downstream Spring MVC routing
   * (RouterFunction predicates, handler mapping path parsing) sees the unprefixed MCP path.
   */
  private static final class RewrittenUriRequest extends HttpServletRequestWrapper {

    private final String rewrittenUri;

    RewrittenUriRequest(final HttpServletRequest request, final String rewrittenUri) {
      super(request);
      this.rewrittenUri = rewrittenUri;
    }

    @Override
    public String getRequestURI() {
      return rewrittenUri;
    }

    @Override
    public StringBuffer getRequestURL() {
      final StringBuffer original = super.getRequestURL();
      final String originalUri = super.getRequestURI();
      final int idx = originalUri == null ? -1 : original.indexOf(originalUri);
      if (idx < 0) {
        return new StringBuffer(rewrittenUri);
      }
      return new StringBuffer(original.substring(0, idx)).append(rewrittenUri);
    }

    @Override
    public String getServletPath() {
      // The MCP RouterFunctions are mapped at the root dispatcher servlet, so the entire
      // rewritten URI (minus the context path) is the servlet path.
      final String contextPath = getContextPath();
      if (contextPath != null && !contextPath.isEmpty() && rewrittenUri.startsWith(contextPath)) {
        return rewrittenUri.substring(contextPath.length());
      }
      return rewrittenUri;
    }

    @Override
    public String getPathInfo() {
      return null;
    }
  }
}
