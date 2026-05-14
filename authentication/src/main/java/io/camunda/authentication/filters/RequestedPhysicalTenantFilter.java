/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.filters;

import io.camunda.security.configuration.PhysicalTenantConfiguration;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Resolves the physical tenant id from the request URL on {@code /v2/physical-tenants/{ptId}/**}
 * and attaches it as a request attribute under {@link #ATTRIBUTE}. Unknown PTs are rejected with
 * 404 before reaching any controller; requests outside the PT URL space pass through untouched.
 *
 * <p>This filter is a request-context plumbing concern, not an authorization decision. PT access is
 * enforced by the controllers consuming {@link
 * io.camunda.authentication.context.RequestedPhysicalTenant#id()}.
 */
public class RequestedPhysicalTenantFilter extends OncePerRequestFilter {

  public static final String ATTRIBUTE = "io.camunda.authentication.requestedPhysicalTenantId";

  private final PathPattern pattern;
  private final List<PhysicalTenantConfiguration> tenants;

  public RequestedPhysicalTenantFilter(
      final String pathPattern, final List<PhysicalTenantConfiguration> tenants) {
    this.pattern = PathPatternParser.defaultInstance.parse(pathPattern);
    this.tenants = tenants;
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {
    final var match = pattern.matchAndExtract(PathContainer.parsePath(request.getRequestURI()));
    if (match == null) {
      filterChain.doFilter(request, response);
      return;
    }
    final String ptId = match.getUriVariables().get("ptId");
    if (!isKnown(ptId)) {
      response.sendError(HttpStatus.NOT_FOUND.value(), "Unknown physical tenant: " + ptId);
      return;
    }
    request.setAttribute(ATTRIBUTE, ptId);
    filterChain.doFilter(request, response);
  }

  private boolean isKnown(final String ptId) {
    for (final PhysicalTenantConfiguration tenant : tenants) {
      if (ptId.equals(tenant.getId())) {
        return true;
      }
    }
    return false;
  }
}
