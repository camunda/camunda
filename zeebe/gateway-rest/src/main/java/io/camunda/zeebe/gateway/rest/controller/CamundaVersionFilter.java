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
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import org.springframework.web.util.UrlPathHelper;

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
 * <p>CSL's {@code JsonProblemDetailAuthFailureHandler} calls {@link HttpServletResponse#reset()}
 * before writing a 401/403, which would otherwise drop the header. The response is wrapped so
 * {@code reset()} restores it.
 *
 * <p>Path matching uses Spring's decoded path ({@link UrlPathHelper#getPathWithinApplication}), the
 * same view Spring MVC and Spring Security route against, so a percent-encoded {@code /v2} segment
 * still advertises the version. Malformed percent-encoding falls back to the raw URI rather than
 * failing the request.
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
    if (!isOrchestrationClusterRestPath(request)) {
      chain.doFilter(request, response);
      return;
    }
    final String version = VersionUtil.getVersion();
    response.setHeader(CAMUNDA_VERSION_HEADER, version);
    chain.doFilter(request, new VersionPreservingResponse(response, version));
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

  /**
   * Context-relative path as Spring MVC / Security see it (decoded, context stripped). Falls back
   * to the raw request URI when decoding fails so a malformed percent-sequence cannot 500 the
   * request.
   */
  private static String contextRelativePath(final HttpServletRequest request) {
    try {
      return UrlPathHelper.defaultInstance.getPathWithinApplication(request);
    } catch (final Exception e) {
      return rawContextRelativePath(request);
    }
  }

  private static String rawContextRelativePath(final HttpServletRequest request) {
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

  /**
   * Restores {@code Camunda-Version} after {@link HttpServletResponse#reset()}, which the
   * authentication-failure handler uses to rebuild a 401/403.
   */
  private static final class VersionPreservingResponse extends HttpServletResponseWrapper {

    private final String version;

    private VersionPreservingResponse(final HttpServletResponse response, final String version) {
      super(response);
      this.version = version;
    }

    @Override
    public void reset() {
      super.reset();
      super.setHeader(CAMUNDA_VERSION_HEADER, version);
    }
  }
}
