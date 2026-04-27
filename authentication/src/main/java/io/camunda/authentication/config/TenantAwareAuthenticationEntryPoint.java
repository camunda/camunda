/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.security.configuration.PhysicalTenantIdpRegistry;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.util.UrlPathHelper;

/**
 * Routes unauthenticated requests to the tenant-aware login picker when the request URL is
 * tenant-scoped (first path segment matches a known physical tenant id). For non-tenant URLs the
 * call is delegated to the wrapped {@link AuthenticationEntryPoint} (Spring's default OAuth2
 * behaviour).
 *
 * <p>Backward compatibility: when the registry is empty, every request is delegated unchanged.
 */
public final class TenantAwareAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();

  private final PhysicalTenantIdpRegistry registry;
  private final AuthenticationEntryPoint delegate;

  public TenantAwareAuthenticationEntryPoint(
      final PhysicalTenantIdpRegistry registry, final AuthenticationEntryPoint delegate) {
    this.registry = registry;
    this.delegate = delegate;
  }

  @Override
  public void commence(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final AuthenticationException authException)
      throws IOException, ServletException {

    if (registry.tenantIds().isEmpty()) {
      delegate.commence(request, response, authException);
      return;
    }

    final var firstSegment = firstPathSegment(request);
    if (firstSegment != null && registry.tenantIds().contains(firstSegment)) {
      response.sendRedirect(request.getContextPath() + "/admin/" + firstSegment + "/login");
      return;
    }

    delegate.commence(request, response, authException);
  }

  private static String firstPathSegment(final HttpServletRequest request) {
    final var path = PATH_HELPER.getPathWithinApplication(request);
    if (path == null || path.length() < 2 || path.charAt(0) != '/') {
      return null;
    }
    final var slash = path.indexOf('/', 1);
    return slash < 0 ? path.substring(1) : path.substring(1, slash);
  }
}
