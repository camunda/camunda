/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.tomcat;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CCSaasRequestAdjustmentFilter implements Filter {

  private static final Logger LOG = LoggerFactory.getLogger(CCSaasRequestAdjustmentFilter.class);
  private final String clusterId;
  private ServletContext servletContext;

  public CCSaasRequestAdjustmentFilter(final String clusterId) {
    this.clusterId = clusterId == null ? "" : clusterId;
  }

  @Override
  public void init(final FilterConfig filterConfig) throws ServletException {
    servletContext = filterConfig.getServletContext();
  }

  @Override
  public void doFilter(
      final ServletRequest request, final ServletResponse response, final FilterChain chain)
      throws IOException, ServletException {
    boolean shallForward = false;
    final HttpServletRequest httpRequest = (HttpServletRequest) request;
    final HttpServletResponse httpResponse = (HttpServletResponse) response;
    String requestURI = httpRequest.getRequestURI();

    // Enhanced logging for debugging
    final String originalURI = requestURI;
    final String method = httpRequest.getMethod();
    final String contextPath = httpRequest.getContextPath();
    LOG.info("=== CCSaasRequestAdjustmentFilter DEBUG ===");
    LOG.info("Original URI: {}", originalURI);
    LOG.info("Method: {}", method);
    LOG.info("Context Path: {}", contextPath);
    LOG.info("Cluster ID: {}", clusterId);
    LOG.info("Cluster ID Path: {}", clusterIdPath());

    /* Handle missing final slash from the home page request */
    if (requestURI.equals(clusterIdPath())) {
      final String redirectTarget = requestURI + "/";
      LOG.info("Redirecting missing slash: {} -> {}", requestURI, redirectTarget);
      httpResponse.sendRedirect(redirectTarget);
      return;
    }

    /* serve static external resource (Optimize only) */
    if (ExternalResourcesUtil.shouldServeStaticResource(httpRequest, clusterId)) {
      LOG.info("Serving static resource for URI: {}", requestURI);
      ExternalResourcesUtil.serveStaticResource(
          httpRequest, httpResponse, servletContext, clusterId);
      return;
    }

    /* Strip cluster ID subpath */
    if (!clusterId.isEmpty() && requestURI.startsWith(clusterIdPath())) {
      requestURI = requestURI.substring(clusterIdPath().length());
      shallForward = true;
      LOG.info("Stripped cluster ID path: {} -> {}", originalURI, requestURI);
    }

    /* transform /external/api -> /api/external (Optimize only) */
    if (requestURI.startsWith("/external/api/")) {
      final String oldURI = requestURI;
      requestURI = requestURI.replaceFirst("/external/api/", "/api/external/");
      shallForward = true;
      LOG.info("Transformed external API path: {} -> {}", oldURI, requestURI);
    }

    if (shallForward) {
      LOG.info("Forwarding request to: {}", requestURI);
      final RequestDispatcher dispatcher = request.getRequestDispatcher(requestURI);
      dispatcher.forward(request, response);
      return;
    }

    LOG.info("Continuing filter chain for: {}", requestURI);
    chain.doFilter(request, response);
  }

  private String clusterIdPath() {
    if (clusterId.isEmpty()) {
      return "";
    }

    return "/" + clusterId;
  }
}
