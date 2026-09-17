/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Tells Optimize's API surface apart from its webapp surface, for the CSL components that have to
 * answer an unauthenticated or denied request differently on each: a machine-readable status on the
 * API, a browser-facing response on the webapp.
 */
final class OptimizeApiRequests {

  private static final String API_PATH = "/api";

  private OptimizeApiRequests() {}

  /** Matches the bearer/API surface exactly like Spring's {@code "/api/**"} does. */
  static boolean isApiRequest(final HttpServletRequest request) {
    final String path = request.getRequestURI().substring(request.getContextPath().length());
    return path.equals(API_PATH) || path.startsWith(API_PATH + "/");
  }
}
