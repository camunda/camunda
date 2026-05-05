/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.context;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Carries the resolved physical tenant id for the current MCP request.
 *
 * <p>Mirrors the REST {@code io.camunda.zeebe.gateway.rest.context.PhysicalTenantContext} used for
 * {@code /v2/physical-tenants/{physicalTenantId}/...} routes. The MCP variant is intentionally
 * kept local to this module to avoid pulling in the {@code zeebe-gateway-rest} dependency.
 */
public final class PhysicalTenantContext {

  /** Default physical tenant id used when no prefix is present in the request path. */
  public static final String DEFAULT_PHYSICAL_TENANT_ID = "default";

  /** URI template variable carrying the physical tenant id in prefixed routes. */
  public static final String PATH_VARIABLE_PHYSICAL_TENANT_ID = "physicalTenantId";

  /** Request attribute key under which the resolved id is stored. */
  public static final String REQUEST_ATTRIBUTE_PHYSICAL_TENANT_ID =
      PhysicalTenantContext.class.getName() + ".PHYSICAL_TENANT_ID";

  private PhysicalTenantContext() {}

  public static void setPhysicalTenantId(
      final HttpServletRequest request, final String physicalTenantId) {
    request.setAttribute(REQUEST_ATTRIBUTE_PHYSICAL_TENANT_ID, physicalTenantId);
  }

  public static String getPhysicalTenantId(final HttpServletRequest request) {
    return asString(request.getAttribute(REQUEST_ATTRIBUTE_PHYSICAL_TENANT_ID));
  }

  /**
   * @return the resolved physical tenant id for the request being processed on this thread, or
   *     {@link #DEFAULT_PHYSICAL_TENANT_ID} when no request context is bound.
   */
  public static String current() {
    final RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes == null) {
      return DEFAULT_PHYSICAL_TENANT_ID;
    }
    final String value =
        asString(
            attributes.getAttribute(
                REQUEST_ATTRIBUTE_PHYSICAL_TENANT_ID, RequestAttributes.SCOPE_REQUEST));
    return value != null ? value : DEFAULT_PHYSICAL_TENANT_ID;
  }

  private static String asString(final Object value) {
    return value != null ? value.toString() : null;
  }
}

