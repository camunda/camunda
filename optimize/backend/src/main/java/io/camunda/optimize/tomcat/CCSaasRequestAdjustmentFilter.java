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

public class CCSaasRequestAdjustmentFilter implements Filter {

  private final String clusterId;
  private ServletContext servletContext;

  public CCSaasRequestAdjustmentFilter(final String clusterId) {
    this.clusterId = clusterId == null ? "" : clusterId;
  }

  @Override
  public void init(final FilterConfig filterConfig) throws ServletException {
    this.servletContext = filterConfig.getServletContext();
  }

  @Override
  public void doFilter(
      final ServletRequest request, final ServletResponse response, final FilterChain chain)
      throws IOException, ServletException {
    boolean shallForward = false;
    final HttpServletRequest httpRequest = (HttpServletRequest) request;
    final HttpServletResponse httpResponse = (HttpServletResponse) response;
    String requestURI = httpRequest.getRequestURI();

    /* Handle missing final slash from the home page request */
    if (requestURI.equals(clusterIdPath())) {
      httpResponse.sendRedirect(requestURI + "/");
      return;
    }

    /* serve static external resource (Optimize only) */
    if (ExternalResourcesUtil.shouldServeStaticResource(httpRequest, clusterId)) {
      ExternalResourcesUtil.serveStaticResource(
          httpRequest, httpResponse, servletContext, clusterId);
      return;
    }

    /* Strip cluster ID subpath */
    if (!clusterId.isEmpty() && requestURI.startsWith(clusterIdPath())) {
      requestURI = requestURI.substring(clusterIdPath().length());
      shallForward = true;
    }

    /* transform /external/api -> /api/external (Optimize only) */
    if (requestURI.startsWith("/external/api/")) {
      requestURI = requestURI.replaceFirst("/external/api/", "/api/external/");
      shallForward = true;
    }

    if (shallForward) {
      final RequestDispatcher dispatcher = request.getRequestDispatcher(requestURI);
      dispatcher.forward(request, response);
      return;
    }

    chain.doFilter(request, response);
  }

  private String clusterIdPath() {
    if (clusterId.isEmpty()) {
      return "";
    }

    return "/" + clusterId;
  }
}
