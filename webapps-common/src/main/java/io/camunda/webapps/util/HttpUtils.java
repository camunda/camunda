/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.util;

import jakarta.servlet.http.HttpServletRequest;

public final class HttpUtils {
  // session attribute key used to store the requested URL for redirects after login
  public static final String REQUESTED_URL = "requestedUrl";

  private HttpUtils() {}

  /**
   * Builds the full requested URL from an {@link HttpServletRequest}.
   *
   * <p>Combines the request URI (excluding the context path) and the query string to form the
   * complete URL requested by the client.
   *
   * @param request the {@link HttpServletRequest} to extract the URL from
   */
  public static String getRequestedUrl(final HttpServletRequest request) {
    final String requestedPath =
        request.getRequestURI().substring(request.getContextPath().length());
    final String queryString = request.getQueryString();
    return queryString == null || queryString.isEmpty()
        ? requestedPath
        : requestedPath + "?" + queryString;
  }
}
