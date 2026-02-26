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
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Servlet filter for CCSaaS (Camunda Cloud SaaS) environments that adjusts request paths by
 * stripping the clusterId prefix and rewriting external API paths.
 *
 * <p>This filter uses {@link HttpServletRequestWrapper} to modify the request path instead of
 * {@link jakarta.servlet.RequestDispatcher#forward}. This is required because Spring Security 7's
 * {@code PathPatternRequestMatcher} evaluates paths from the original request URI. Using a wrapper
 * ensures that Spring Security (and all other downstream components) see the rewritten path.
 */
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
    boolean pathModified = false;
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
      if (requestURI.isEmpty()) {
        requestURI = "/";
      }
      pathModified = true;
    }

    /* transform /external/api -> /api/external (Optimize only) */
    if (requestURI.startsWith("/external/api/")) {
      requestURI = requestURI.replaceFirst("/external/api/", "/api/external/");
      pathModified = true;
    }

    if (pathModified) {
      chain.doFilter(new PathRewritingRequestWrapper(httpRequest, requestURI), response);
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
