/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter to restore authentication from request scope when SecurityContext is lost due to thread
 * switching during request processing.
 */
public class RequestScopedAuthenticationRestorationFilter extends OncePerRequestFilter {

  private static final Logger LOG =
      LoggerFactory.getLogger(RequestScopedAuthenticationRestorationFilter.class);
  private static final String REQUEST_AUTH_KEY = "camunda.certificate.authentication";
  private static final String REQUEST_ID_KEY = "camunda.request.id";

  // Thread-safe map to store authentication across threads for the same request
  private static final java.util.concurrent.ConcurrentHashMap<String, Authentication>
      REQUEST_AUTH_MAP = new java.util.concurrent.ConcurrentHashMap<>();

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {

    LOG.debug(
        "RequestScopedAuthenticationRestorationFilter processing: {} on thread: {}",
        request.getRequestURI(),
        Thread.currentThread().getName());

    // Check if we need to restore authentication from request scope
    final Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
    LOG.info(
        "Current auth state: Type: {}, Principal: {}, Authenticated: {}",
        currentAuth != null ? currentAuth.getClass().getSimpleName() : "null",
        currentAuth != null ? currentAuth.getName() : "null",
        currentAuth != null ? currentAuth.isAuthenticated() : false);

    if (currentAuth == null || !currentAuth.isAuthenticated()) {
      LOG.info(
          "Authentication is null or not authenticated, attempting to restore from request attributes");
      final Authentication storedAuth = getAuthenticationFromRequest(request);
      if (storedAuth != null && storedAuth.isAuthenticated()) {
        SecurityContextHolder.getContext().setAuthentication(storedAuth);
        LOG.info(
            "Successfully restored authentication from request attributes for: {} - Principal: {}, Thread: {}",
            request.getRequestURI(),
            storedAuth.getName(),
            Thread.currentThread().getName());
      } else {
        LOG.info(
            "No valid authentication found in request attributes for: {}", request.getRequestURI());
      }
    } else {
      LOG.debug(
          "Authentication already present, no restoration needed for: {}", request.getRequestURI());
    }

    filterChain.doFilter(request, response);
  }

  /** Retrieve authentication from request attributes if SecurityContext is empty */
  private Authentication getAuthenticationFromRequest(final HttpServletRequest request) {
    try {
      // First try request attributes
      Authentication auth = (Authentication) request.getAttribute(REQUEST_AUTH_KEY);
      if (auth != null) {
        LOG.debug("Retrieved authentication from request attributes: {}", auth.getName());
        return auth;
      }

      // Fall back to thread-safe map using request ID
      final String requestId = getOrCreateRequestId(request);
      auth = REQUEST_AUTH_MAP.get(requestId);
      if (auth != null) {
        LOG.debug(
            "Retrieved authentication from request map: {} for RequestId: {}, Thread: {}",
            auth.getName(),
            requestId,
            Thread.currentThread().getName());
        return auth;
      }
    } catch (final Exception e) {
      LOG.warn("Failed to retrieve authentication from request attributes", e);
    }
    return null;
  }

  /** Get or create a stable request ID for storing authentication across threads */
  private String getOrCreateRequestId(final HttpServletRequest request) {
    String requestId = (String) request.getAttribute(REQUEST_ID_KEY);
    if (requestId == null) {
      // Create a stable ID based on request attributes that survive thread switches
      final String sessionId = request.getSession(true).getId();
      final String requestUri = request.getRequestURI();
      final String method = request.getMethod();
      final String queryString = request.getQueryString() != null ? request.getQueryString() : "";

      // Create a hash of the request details for stability
      final String requestKey = sessionId + "_" + method + "_" + requestUri + "_" + queryString;
      requestId = "req_" + (requestKey.hashCode() & 0x7fffffff);
      request.setAttribute(REQUEST_ID_KEY, requestId);

      LOG.info(
          "RequestScopedAuthenticationRestorationFilter - Created stable request ID: {} for {} {} on thread: {}",
          requestId,
          method,
          requestUri,
          Thread.currentThread().getName());
    }
    return requestId;
  }
}
