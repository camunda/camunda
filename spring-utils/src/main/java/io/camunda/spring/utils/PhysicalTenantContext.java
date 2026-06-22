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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private static final Logger LOG = LoggerFactory.getLogger(PhysicalTenantContext.class);

  /**
   * Physical tenant propagated onto a non-request (e.g. async executor) thread. Used to carry the
   * request's tenant across the request→async boundary by value, decoupled from the servlet request
   * lifecycle (the request may already be inactive when the async task runs).
   */
  private static final ThreadLocal<String> PROPAGATED_PHYSICAL_TENANT_ID = new ThreadLocal<>();

  private PhysicalTenantContext() {}

  public static void setPhysicalTenantId(
      final HttpServletRequest request, final String physicalTenantId) {
    request.setAttribute(REQUEST_ATTRIBUTE_PHYSICAL_TENANT_ID, physicalTenantId);
  }

  public static String getPhysicalTenantId(final HttpServletRequest request) {
    return asString(request.getAttribute(REQUEST_ATTRIBUTE_PHYSICAL_TENANT_ID));
  }

  /**
   * Returns the physical tenant id in effect on the current thread.
   *
   * <p>On a request thread this is the tenant resolved for the request. For prefixed {@code
   * /physical-tenants/{physicalTenantId}/...} requests the id is stamped on the request by {@code
   * PhysicalTenantFilter} (gateway-rest) or by the MCP gateway filter, and that value is returned.
   * Non-prefixed requests resolve to {@link PhysicalTenantIds#DEFAULT_PHYSICAL_TENANT_ID} via
   * different mechanisms: for REST {@code /v2/...} {@code PhysicalTenantFilter} stamps nothing and
   * this method falls back to the default; for MCP {@code /mcp/...} the MCP autoconfiguration
   * stamps the default explicitly.
   *
   * <p>On a non-request thread (e.g. an async executor worker) it returns the tenant propagated
   * onto the thread by {@code PhysicalTenantPropagatingExecutorService}; with neither a request
   * scope nor a propagated tenant there is nothing to resolve and the call throws.
   *
   * @return the physical tenant id in effect on the current thread; never {@code null}
   * @throws IllegalStateException if neither a request scope nor a propagated tenant is bound on
   *     the current thread
   */
  public static String current() {
    final String physicalTenantId = currentOrNull();
    if (physicalTenantId == null) {
      throw new IllegalStateException(
          "PhysicalTenantContext.current() called outside a request scope; "
              + "the physical tenant must be resolved on the request thread (or propagated onto this thread).");
    }
    return physicalTenantId;
  }

  /**
   * Like {@link #current()} but returns {@code null} instead of throwing when no tenant is bound.
   * Resolves from the request scope when present (falling back to {@link
   * PhysicalTenantIds#DEFAULT_PHYSICAL_TENANT_ID} for an unstamped request), otherwise from a value
   * propagated onto this thread (e.g. by {@code PhysicalTenantPropagatingExecutorService}).
   */
  static String currentOrNull() {
    final RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes != null) {
      final String value =
          asString(
              attributes.getAttribute(
                  REQUEST_ATTRIBUTE_PHYSICAL_TENANT_ID, RequestAttributes.SCOPE_REQUEST));
      return value != null ? value : PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
    }
    return PROPAGATED_PHYSICAL_TENANT_ID.get();
  }

  /** Removes any tenant propagated onto the current thread. */
  static void clearPropagatedPhysicalTenant() {
    LOG.trace("Clearing propagated physical tenant on thread {}", Thread.currentThread().getName());
    PROPAGATED_PHYSICAL_TENANT_ID.remove();
  }

  /**
   * Returns the raw propagated tenant value for the current thread, or {@code null} if none is set.
   *
   * <p>Unlike {@link #currentOrNull()}, this reads the propagation {@link ThreadLocal} directly and
   * never consults the request scope — so it is safe to call on a request thread when saving state
   * before a nested propagation, without capturing the request-derived tenant.
   */
  static String getPropagatedPhysicalTenant() {
    return PROPAGATED_PHYSICAL_TENANT_ID.get();
  }

  /**
   * Binds {@code physicalTenantId} as the propagated tenant for the current thread, so {@link
   * #current()} resolves it where no request scope exists. Intended for carrying a request's tenant
   * onto async worker threads; callers must {@link #clearPropagatedPhysicalTenant() clear} it in a
   * {@code finally} block.
   */
  static void setPropagatedPhysicalTenant(final String physicalTenantId) {
    LOG.trace(
        "Setting propagated physical tenant on thread {}: {}",
        Thread.currentThread().getName(),
        physicalTenantId);
    PROPAGATED_PHYSICAL_TENANT_ID.set(physicalTenantId);
  }

  private static String asString(final Object value) {
    return value != null ? value.toString() : null;
  }
}
