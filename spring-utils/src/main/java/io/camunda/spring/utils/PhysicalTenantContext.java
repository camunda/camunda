/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.utils;

import io.camunda.configuration.api.physicaltenants.PhysicalTenantIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Carries the resolved physical tenant id for the current request.
 *
 * <p>Used by both the REST gateway ({@code /physical-tenants/{physicalTenantId}/v2/...} routes) and
 * the MCP gateway ({@code /physical-tenants/{physicalTenantId}/mcp/...} routes). The id is
 * populated on the request by the respective interceptor and read downstream via {@link #current()}
 * (request-bound thread) or {@link #getPhysicalTenantId(HttpServletRequest)}.
 */
public final class PhysicalTenantContext {

  /** URL path prefix for physical-tenant-scoped routes (e.g. {@code /physical-tenants/foo/}). */
  public static final String PHYSICAL_TENANTS_PATH_SEGMENT = "/physical-tenants/";

  /** URI template variable carrying the physical tenant id in prefixed routes. */
  public static final String PATH_VARIABLE_PHYSICAL_TENANT_ID = "physicalTenantId";

  /**
   * URI template prefix for physical-tenant-scoped routes (e.g. {@code
   * /physical-tenants/{physicalTenantId}/...}).
   */
  public static final String PHYSICAL_TENANT_URI_PREFIX =
      PHYSICAL_TENANTS_PATH_SEGMENT + "{" + PATH_VARIABLE_PHYSICAL_TENANT_ID + "}";

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
   * Returns the resolved physical tenant id for the request being processed on the current thread.
   *
   * <p>When the request carries the {@code /physical-tenants/{physicalTenantId}/...} prefix, the id
   * stamped by {@code PhysicalTenantFilter} (gateway-rest) or the MCP {@code defaultTenantFilter}
   * is returned. For non-prefixed requests (plain {@code /v2/...} or {@code /mcp/...}) the filter
   * stamps {@link PhysicalTenantIds#DEFAULT_PHYSICAL_TENANT_ID} and that value is returned.
   *
   * @return the physical tenant id for the current request; never {@code null}
   * @throws IllegalStateException if called outside a servlet-request scope (i.e. {@link
   *     RequestContextHolder#getRequestAttributes()} returns {@code null})
   */
  public static String current() {
    final RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes == null) {
      throw new IllegalStateException(
          "PhysicalTenantContext.current() called outside a request scope; "
              + "the physical tenant must be resolved on the request thread (or supplied explicitly).");
    }
    final String value =
        asString(
            attributes.getAttribute(
                REQUEST_ATTRIBUTE_PHYSICAL_TENANT_ID, RequestAttributes.SCOPE_REQUEST));
    return value != null ? value : PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
  }

  private static String asString(final Object value) {
    return value != null ? value.toString() : null;
  }
}
