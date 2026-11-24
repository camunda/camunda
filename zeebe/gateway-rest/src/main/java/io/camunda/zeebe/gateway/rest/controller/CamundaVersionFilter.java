/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.spring.utils.PhysicalTenantContext.PHYSICAL_TENANTS_PATH_SEGMENT;

import io.camunda.zeebe.util.VersionUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Writes the {@code Camunda-Version} response header on every Orchestration Cluster REST ({@code
 * /v2/**}) response, including error and unauthenticated responses.
 *
 * <p>The value is {@link VersionUtil#getVersion()}, the same string already returned as {@code
 * gatewayVersion} on {@code GET /v2/topology}. Clients that ship the OpenAPI spec use it with
 * {@code x-added-in-version} to decide which operations this cluster supports, without a dedicated
 * topology call.
 *
 * <p>The header is set <em>before</em> the rest of the chain runs so a later filter that writes the
 * body and returns (for example {@link PhysicalTenantStatusScopeFilter}'s uniform 404) still
 * carries it. {@code ApiFiltersConfiguration} registers this before Spring Security so 401s from
 * the security chain include the header too.
 *
 * <p>Tenant-prefixed REST paths ({@code /physical-tenants/{id}/v2/...}) are treated as {@code /v2}
 * after the tenant prefix. Non-REST paths (actuator, swagger, management) are left unchanged.
 */
public final class CamundaVersionFilter extends HttpFilter {

  public static final String CAMUNDA_VERSION_HEADER = "Camunda-Version";

  private static final String V2_PREFIX = "/v2";
  private static final String V2_PREFIX_WITH_SLASH = "/v2/";

  @Override
  protected void doFilter(
      final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain)
      throws IOException, ServletException {
    if (isOrchestrationClusterRestPath(request)) {
      response.setHeader(CAMUNDA_VERSION_HEADER, VersionUtil.getVersion());
    }
    chain.doFilter(request, response);
  }

  private static boolean isOrchestrationClusterRestPath(final HttpServletRequest request) {
    final String path = contextRelativePath(request);
    if (path == null) {
      return false;
    }
    return isV2Path(path) || isTenantPrefixedV2Path(path);
  }

  private static boolean isV2Path(final String path) {
    return path.equals(V2_PREFIX) || path.startsWith(V2_PREFIX_WITH_SLASH);
  }

  private static boolean isTenantPrefixedV2Path(final String path) {
    if (!path.startsWith(PHYSICAL_TENANTS_PATH_SEGMENT)) {
      return false;
    }
    final int start = PHYSICAL_TENANTS_PATH_SEGMENT.length();
    final int slash = path.indexOf('/', start);
    if (slash <= start) {
      return false;
    }
    return isV2Path(path.substring(slash));
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
