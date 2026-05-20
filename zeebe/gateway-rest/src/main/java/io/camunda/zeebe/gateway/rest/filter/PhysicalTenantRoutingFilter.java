/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.filter;

import io.camunda.zeebe.gateway.rest.context.PhysicalTenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.UrlPathHelper;

/**
 * Rewrites {@code /v2/physical-tenants/{tenantId}/...} requests to {@code /v2/...} before Spring
 * MVC handler resolution, and stores the extracted tenant id in the request context via {@link
 * PhysicalTenantContext}.
 *
 * <p>Validation of the extracted tenant id and rejection of {@link
 * io.camunda.zeebe.gateway.rest.annotation.ClusterScoped} endpoints accessed via the tenant prefix
 * is handled downstream by {@link
 * io.camunda.zeebe.gateway.rest.interceptor.PhysicalTenantInterceptor}, which runs after handler
 * resolution and therefore has access to the resolved controller class.
 */
public class PhysicalTenantRoutingFilter extends OncePerRequestFilter {

  private static final String API_PREFIX = "/v2";
  private static final String TENANT_PATH_PREFIX = API_PREFIX + "/physical-tenants/";

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {

    final String path = pathWithinApplication(request);
    if (!path.startsWith(TENANT_PATH_PREFIX)) {
      filterChain.doFilter(request, response);
      return;
    }

    final String remainder = path.substring(TENANT_PATH_PREFIX.length());
    final int slashPos = remainder.indexOf('/');
    final String rawTenantId = slashPos < 0 ? remainder : remainder.substring(0, slashPos);

    if (rawTenantId.isEmpty()) {
      filterChain.doFilter(request, response);
      return;
    }

    final String rewrittenPath =
        slashPos < 0 ? API_PREFIX : API_PREFIX + remainder.substring(slashPos);
    final String tenantId = URLDecoder.decode(rawTenantId, StandardCharsets.UTF_8);

    PhysicalTenantContext.setPhysicalTenantId(request, tenantId);
    request.setAttribute(PhysicalTenantContext.REQUEST_ATTRIBUTE_IS_PHYSICAL_TENANT_PREFIXED, true);
    request.setAttribute(
        PhysicalTenantContext.REQUEST_ATTRIBUTE_ORIGINAL_REQUEST_URI, request.getRequestURI());

    filterChain.doFilter(new RewrittenPathRequest(request, rewrittenPath), response);
  }

  /**
   * Returns the request path stripped of the servlet context path. Prefer this over {@link
   * HttpServletRequest#getServletPath()} since upstream request wrappers may rewrite the URI
   * without updating the servlet path.
   */
  private static String pathWithinApplication(final HttpServletRequest request) {
    final String contextPath = request.getContextPath();
    final String uri = request.getRequestURI();
    return contextPath.isEmpty() ? uri : uri.substring(contextPath.length());
  }

  private static final class RewrittenPathRequest extends HttpServletRequestWrapper {

    private final String servletPath;
    private final Map<String, Object> localAttributes = new HashMap<>();

    RewrittenPathRequest(final HttpServletRequest wrapped, final String servletPath) {
      super(wrapped);
      this.servletPath = servletPath;
      localAttributes.put(ServletRequestPathUtils.PATH_ATTRIBUTE, null);
      localAttributes.put(UrlPathHelper.PATH_ATTRIBUTE, null);
    }

    @Override
    public String getRequestURI() {
      return getContextPath() + servletPath;
    }

    @Override
    public String getServletPath() {
      return servletPath;
    }

    @Override
    public String getPathInfo() {
      return null;
    }

    @Override
    public Object getAttribute(final String name) {
      if (localAttributes.containsKey(name)) {
        return localAttributes.get(name);
      }
      return super.getAttribute(name);
    }

    @Override
    public void setAttribute(final String name, final Object value) {
      if (localAttributes.containsKey(name)) {
        localAttributes.put(name, value);
      } else {
        super.setAttribute(name, value);
      }
    }

    @Override
    public void removeAttribute(final String name) {
      localAttributes.remove(name);
      super.removeAttribute(name);
    }
  }
}
