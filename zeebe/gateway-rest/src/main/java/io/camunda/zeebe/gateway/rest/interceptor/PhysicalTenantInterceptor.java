/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.interceptor;

import io.camunda.zeebe.gateway.rest.annotation.ClusterScoped;
import io.camunda.zeebe.gateway.rest.context.PhysicalTenantContext;
import io.camunda.zeebe.gateway.rest.filter.PhysicalTenantRoutingFilter;
import io.camunda.zeebe.gateway.rest.util.PhysicalTenantRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Validates the physical tenant id extracted by {@link PhysicalTenantRoutingFilter} and exposes the
 * resolved id as a request attribute via {@link PhysicalTenantContext}.
 *
 * <ul>
 *   <li>If the request arrived via the {@code /v2/physical-tenants/{physicalTenantId}/...} prefix
 *       but targets a {@link ClusterScoped} controller, a {@link ResponseStatusException} with HTTP
 *       404 is thrown so that {@code GlobalControllerExceptionHandler} can wrap it in a {@code
 *       CamundaProblemDetail}, matching the error format produced by the rest of the API.
 *   <li>If the id extracted by the filter is unknown to the configured {@link
 *       PhysicalTenantRegistry}, the same exception mechanism is used.
 *   <li>If no prefix was present, the id defaults to {@link
 *       PhysicalTenantContext#DEFAULT_PHYSICAL_TENANT_ID}.
 * </ul>
 */
public class PhysicalTenantInterceptor implements HandlerInterceptor {

  private final PhysicalTenantRegistry registry;

  public PhysicalTenantInterceptor(final PhysicalTenantRegistry registry) {
    this.registry = registry;
  }

  @Override
  public boolean preHandle(
      final HttpServletRequest request, final HttpServletResponse response, final Object handler) {

    final boolean isPrefixed =
        Boolean.TRUE.equals(
            request.getAttribute(
                PhysicalTenantContext.REQUEST_ATTRIBUTE_IS_PHYSICAL_TENANT_PREFIXED));

    if (!isPrefixed) {
      PhysicalTenantContext.setPhysicalTenantId(
          request, PhysicalTenantContext.DEFAULT_PHYSICAL_TENANT_ID);
      return true;
    }

    if (handler instanceof final HandlerMethod hm
        && AnnotatedElementUtils.hasAnnotation(hm.getBeanType(), ClusterScoped.class)) {
      throw notFound(request, null);
    }

    final String tenantId = PhysicalTenantContext.getPhysicalTenantId(request);
    if (!registry.exists(tenantId)) {
      throw notFound(request, "Unknown physical tenant: " + tenantId);
    }

    return true;
  }

  private static ResponseStatusException notFound(
      final HttpServletRequest request, final String detail) {
    final var ex =
        detail != null
            ? new ResponseStatusException(HttpStatus.NOT_FOUND, detail)
            : new ResponseStatusException(HttpStatus.NOT_FOUND);
    final var originalUri =
        (String) request.getAttribute(PhysicalTenantContext.REQUEST_ATTRIBUTE_ORIGINAL_REQUEST_URI);
    if (originalUri != null) {
      ex.getBody().setInstance(URI.create(originalUri));
    }
    return ex;
  }
}
