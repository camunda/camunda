/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import io.camunda.spring.utils.PhysicalTenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * Prefixes the {@code contextPath} and {@code baseName} model attributes set by the webapp index
 * controllers so a physical-tenant-prefixed request renders an SPA whose {@code <base href>}
 * carries the PT prefix.
 *
 * <p>The legacy index controllers (operate, tasklist, admin) set {@code contextPath =
 * servletContextPath + "/<app>/"} unconditionally (e.g. {@code /operate/}). The unified webapp's
 * {@code WebappIndexController} additionally sets {@code baseName = servletContextPath +
 * "/webapp/"}, which drives the rendered shell's {@code <base href>} and the SPA boot config.
 * Without this interceptor those values would always be unprefixed, so even when entered through
 * {@code /physical-tenants/<id>/<app>} the browser would resolve assets and API calls back to the
 * unprefixed URL space, where the PT session cookie does not apply and the SPA breaks.
 *
 * <p>The physical tenant id is resolved from the request via {@link
 * PhysicalTenantContext#getPhysicalTenantId(HttpServletRequest)}, which {@code
 * PhysicalTenantFilter} stamps for {@code /physical-tenants/<id>/...} requests and leaves {@code
 * null} for cluster (unprefixed) requests. The attribute survives the internal forward the index
 * controllers perform for SPA sub-paths, so reading it is more robust than parsing the
 * (post-forward) request URI.
 *
 * <p>No-op when the request is not PT-prefixed or carries no model.
 */
public class PhysicalTenantWebappContextPathInterceptor implements HandlerInterceptor {

  private static final String CONTEXT_PATH_ATTRIBUTE = "contextPath";
  private static final String BASE_NAME_ATTRIBUTE = "baseName";

  @Override
  public void postHandle(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final Object handler,
      final ModelAndView modelAndView) {
    if (modelAndView == null) {
      return;
    }
    final String physicalTenantId = PhysicalTenantContext.getPhysicalTenantId(request);
    if (physicalTenantId == null) {
      return;
    }
    prefixAttribute(request, modelAndView, CONTEXT_PATH_ATTRIBUTE, physicalTenantId);
    prefixAttribute(request, modelAndView, BASE_NAME_ATTRIBUTE, physicalTenantId);
  }

  private void prefixAttribute(
      final HttpServletRequest request,
      final ModelAndView modelAndView,
      final String attribute,
      final String physicalTenantId) {
    final Object existing = modelAndView.getModel().get(attribute);
    if (existing instanceof final String existingPath) {
      // PT routes are servlet-context-relative, so the external path is
      // <contextPath>/physical-tenants/<id>/<webapp>/. The index controllers set the model's
      // path attributes to <contextPath>/<webapp>/, so the PT segment must be inserted after the
      // servlet context path (which is empty in the common case) — not before it.
      final String contextPath = request.getContextPath();
      final String afterContextPath =
          existingPath.startsWith(contextPath)
              ? existingPath.substring(contextPath.length())
              : existingPath;
      modelAndView.addObject(
          attribute,
          contextPath
              + PhysicalTenantContext.PHYSICAL_TENANTS_PATH_SEGMENT
              + physicalTenantId
              + afterContextPath);
    }
  }
}
