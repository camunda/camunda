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
      final String rewrittenURI = requestURI.replaceFirst("/external/api/", "/api/external/");
      final RequestDispatcher dispatcher = request.getRequestDispatcher(rewrittenURI);
      dispatcher.forward(request, response);
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
