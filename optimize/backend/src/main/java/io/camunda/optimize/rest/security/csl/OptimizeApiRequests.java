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
 * Tells the API surface apart from the webapp surface, so the CSL adapters can keep Optimize's
 * contract that API calls are answered with a status code and navigations with a page.
 */
public final class OptimizeApiRequests {

  private static final String API_PATH = "/api";

  private OptimizeApiRequests() {}

  /** Matches the same requests as the {@code /api/**} pattern, that is {@code /api} and below. */
  public static boolean isApiRequest(final HttpServletRequest request) {
    final String path = request.getRequestURI().substring(request.getContextPath().length());
    return path.equals(API_PATH) || path.startsWith(API_PATH + "/");
  }
}
