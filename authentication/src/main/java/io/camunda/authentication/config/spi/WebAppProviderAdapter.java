/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.spi;

import io.camunda.security.spring.spi.WebAppProviderPort;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.Set;

/**
 * Host-supplied {@link WebAppProviderPort} that derives the web app id from the request URL prefix
 * — {@code /admin/...} → {@code admin}, {@code /operate/...} → {@code operate}, {@code
 * /tasklist/...} → {@code tasklist}. Other paths return empty (no web app context).
 */
public class WebAppProviderAdapter implements WebAppProviderPort {

  public static final Set<String> WEB_APPS = Set.of("admin", "operate", "tasklist");

  @Override
  public Optional<String> webAppFor(final HttpServletRequest request) {
    final String pathWithinApp = stripContextPath(request);
    if (pathWithinApp.isEmpty() || "/".equals(pathWithinApp)) {
      // Path is empty or just the root "/" — no web app to resolve.
      return Optional.empty();
    }
    final String firstSegment = extractFirstPathSegment(pathWithinApp);
    return WEB_APPS.contains(firstSegment) ? Optional.of(firstSegment) : Optional.empty();
  }

  private static String stripContextPath(final HttpServletRequest request) {
    final String uri = request.getRequestURI() == null ? "" : request.getRequestURI();
    final String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
    return uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;
  }

  /**
   * Returns the path segment after the leading slash, e.g. {@code /operate/foo} → {@code operate}.
   */
  private static String extractFirstPathSegment(final String path) {
    final int secondSlash = path.indexOf('/', 1);
    return secondSlash > 0 ? path.substring(1, secondSlash) : path.substring(1);
  }
}
