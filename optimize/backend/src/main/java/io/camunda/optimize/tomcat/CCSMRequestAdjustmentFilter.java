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
 * Servlet filter for CCSM (Camunda Cloud Self-Managed) environments that rewrites external API
 * paths and serves static resources.
 *
 * <p>This filter uses {@link PathRewritingRequestWrapper} to modify the request path instead of
 * {@link jakarta.servlet.RequestDispatcher#forward}. This is required because Spring Security 7's
 * {@code PathPatternRequestMatcher} evaluates paths from the original request URI. Using a wrapper
 * ensures that Spring Security (and all other downstream components) see the rewritten path.
 */
public class CCSMRequestAdjustmentFilter implements Filter {

  private ServletContext servletContext;

  @Override
  public void init(final FilterConfig filterConfig) throws ServletException {
    this.servletContext = filterConfig.getServletContext();
  }

  @Override
  public void doFilter(
      final ServletRequest request, final ServletResponse response, final FilterChain chain)
      throws IOException, ServletException {
    final HttpServletRequest httpRequest = (HttpServletRequest) request;
    final HttpServletResponse httpResponse = (HttpServletResponse) response;

    String requestURI = httpRequest.getRequestURI();
    requestURI = ExternalResourcesUtil.stripContextPath(requestURI, httpRequest);

    /* transform /external/api -> /api/external */
    if (requestURI.startsWith("/external/api/")) {
      final String rewrittenURI =
          httpRequest.getContextPath()
              + requestURI.replaceFirst("/external/api/", "/api/external/");
      chain.doFilter(new PathRewritingRequestWrapper(httpRequest, rewrittenURI), response);
      return;
    }

    /* serve static external resource */
    if (ExternalResourcesUtil.shouldServeStaticResource(httpRequest)) {
      ExternalResourcesUtil.serveStaticResource(httpRequest, httpResponse, servletContext);
      return;
    }

    chain.doFilter(request, response);
  }
}
