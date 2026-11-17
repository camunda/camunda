/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.spring.utils.PhysicalTenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Servlet {@link jakarta.servlet.Filter} that makes Swagger UI reachable under a tenant-prefixed
 * path ({@code /physical-tenants/{id}/swagger...}).
 *
 * <p>{@code /swagger} and {@code /swagger-ui/**} are not {@code @RequestMapping} controller
 * methods: {@code /swagger} is a {@code ViewControllerRegistry} redirect (see {@code
 * OpenApiResourceConfig}) and {@code /swagger-ui/**} is a static resource handler auto-registered
 * by springdoc, so {@code PhysicalTenantRequestMappingHandlerMapping} can never mint a
 * tenant-prefixed sibling for them. This filter fills that gap by redirecting the bare paths to
 * their tenant-prefixed {@code swagger-ui/index.html} and forwarding tenant-prefixed asset requests
 * to the real, unprefixed handler springdoc registers.
 *
 * <p>{@code ApiFiltersConfiguration} registers this to run <em>after</em> Spring Security's {@code
 * FilterChainProxy}, unlike {@link PhysicalTenantFilter}: this filter does not validate the tenant
 * id, so it must only run once security has already confirmed the tenant+path is permitted (an
 * unconfigured tenant is rejected by the catch-all chain with 404 before reaching this filter). See
 * ADR-0003.
 */
public final class PhysicalTenantSwaggerFilter extends HttpFilter {

  private static final String SWAGGER_PATH = "/swagger";
  private static final String SWAGGER_UI_PATH = "/swagger-ui";
  private static final String SWAGGER_UI_INDEX = SWAGGER_UI_PATH + "/index.html";

  @Override
  protected void doFilter(
      final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain)
      throws IOException, ServletException {
    final String tenantId = PhysicalTenantContext.getPhysicalTenantId(request);
    if (tenantId == null) {
      chain.doFilter(request, response);
      return;
    }

    final String remainder = swaggerRemainder(request, tenantId);
    if (remainder == null) {
      chain.doFilter(request, response);
      return;
    }

    if (isBareSwaggerPath(remainder)) {
      response.sendRedirect(
          request.getContextPath()
              + PhysicalTenantContext.PHYSICAL_TENANTS_PATH_SEGMENT
              + tenantId
              + SWAGGER_UI_INDEX);
    } else {
      request.getRequestDispatcher(remainder).forward(request, response);
    }
  }

  private static boolean isBareSwaggerPath(final String remainder) {
    return SWAGGER_PATH.equals(remainder)
        || (SWAGGER_PATH + "/").equals(remainder)
        || SWAGGER_UI_PATH.equals(remainder)
        || (SWAGGER_UI_PATH + "/").equals(remainder);
  }

  /**
   * Returns the tenant-relative path (e.g. {@code /swagger-ui/index.html}) if the request targets a
   * swagger path under this tenant's prefix, otherwise {@code null}.
   */
  private static String swaggerRemainder(final HttpServletRequest request, final String tenantId) {
    final String path = contextRelativePath(request);
    final String prefix = PhysicalTenantContext.PHYSICAL_TENANTS_PATH_SEGMENT + tenantId;
    if (path == null || !path.startsWith(prefix)) {
      return null;
    }
    final String remainder = path.substring(prefix.length());
    if (isBareSwaggerPath(remainder) || remainder.startsWith(SWAGGER_UI_PATH + "/")) {
      return remainder;
    }
    return null;
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
