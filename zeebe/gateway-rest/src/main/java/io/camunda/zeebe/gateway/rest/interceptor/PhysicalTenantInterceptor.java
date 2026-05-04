/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.interceptor;

import io.camunda.zeebe.gateway.rest.context.PhysicalTenantContext;
import io.camunda.zeebe.gateway.rest.util.PhysicalTenantResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Validates the {@code physicalTenantId} captured from the request URI and exposes the resolved id
 * as a request attribute via {@link PhysicalTenantContext}.
 *
 * <ul>
 *   <li>If the request matches a tenant-prefixed route ({@code
 *       /v2/physical-tenants/{physicalTenantId}/...}) and the id is unknown to the configured
 *       {@link PhysicalTenantResolver}, the request is rejected with HTTP 404 before reaching the
 *       controller.
 *   <li>If no prefix is present, the resolved id defaults to {@link
 *       PhysicalTenantContext#DEFAULT_PHYSICAL_TENANT_ID}.
 * </ul>
 */
public class PhysicalTenantInterceptor implements HandlerInterceptor {

  private final PhysicalTenantResolver resolver;

  public PhysicalTenantInterceptor(final PhysicalTenantResolver resolver) {
    this.resolver = resolver;
  }

  @Override
  @SuppressWarnings("unchecked")
  public boolean preHandle(
      final HttpServletRequest request, final HttpServletResponse response, final Object handler)
      throws Exception {
    final Map<String, String> uriVars =
        (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    final String tenantId =
        uriVars != null
            ? uriVars.get(PhysicalTenantContext.PATH_VARIABLE_PHYSICAL_TENANT_ID)
            : null;

    if (tenantId == null) {
      PhysicalTenantContext.setPhysicalTenantId(
          request, PhysicalTenantContext.DEFAULT_PHYSICAL_TENANT_ID);
      return true;
    }

    if (!resolver.exists(tenantId)) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND, "Unknown physical tenant: " + tenantId);
      return false;
    }

    PhysicalTenantContext.setPhysicalTenantId(request, tenantId);
    return true;
  }
}
