/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.jetty;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;
import java.io.IOException;

import static org.camunda.optimize.jetty.OptimizeResourceConstants.NO_CACHE_RESOURCES;
import static org.camunda.optimize.jetty.OptimizeResourceConstants.REST_API_PATH;

/**
 * After an upgrade of Optimize the browser might load old resources
 * from the browser cache. This filter adds the no store
 * cookie to the response to prevent those caching issues after the upgrade.
 */
public class NoCachingFilter implements Filter {

  public static final String NO_STORE = "no-store";

  public NoCachingFilter() {
  }

  @Override
  public void init(FilterConfig filterConfig) {
    // nothing to do here
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws
                                                                                            IOException,
                                                                                            ServletException {
    HttpServletRequest servletRequest = (HttpServletRequest) request;
    String requestPath = servletRequest.getServletPath();
    boolean isStaticResourceThatShouldNotBeCached =
      NO_CACHE_RESOURCES.stream().anyMatch(requestPath::endsWith);
    if (isStaticResourceThatShouldNotBeCached || isApiRestCall(requestPath)) {
      ((HttpServletResponse) response).setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE);
    }
    chain.doFilter(request, response);
  }

  private boolean isApiRestCall(String requestPath) {
    return requestPath.startsWith(REST_API_PATH);
  }

  @Override
  public void destroy() {
    // nothing to do here
  }
}
