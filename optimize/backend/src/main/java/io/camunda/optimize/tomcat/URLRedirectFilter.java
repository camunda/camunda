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
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;

public class URLRedirectFilter implements Filter {

  private static final String WEBAPP_PATH = "/webapp";

  private final Pattern redirectPattern;
  private final String redirectPath;

  public URLRedirectFilter(final String regex, final String redirectPath) {
    redirectPattern = Pattern.compile(regex);
    this.redirectPath = redirectPath;
  }

  @Override
  public void doFilter(
      final ServletRequest request, final ServletResponse response, final FilterChain chain)
      throws IOException, ServletException {
    final HttpServletRequest httpRequest = (HttpServletRequest) request;
    final HttpServletResponse httpResponse = (HttpServletResponse) response;

    String requestURI = httpRequest.getRequestURI();
    final String originalRequestURI = requestURI;

    /* Exclude contextPath from the business logic */
    final String contextPath = httpRequest.getContextPath();
    if (!contextPath.isEmpty() && requestURI.startsWith(contextPath)) {
      requestURI = requestURI.substring(contextPath.length());
    }

    /* Handle missing trailing slash to home */
    if (requestURI.isEmpty()) {
      httpResponse.sendRedirect(redirectPath);
      return;
    }

    /* Validate requests to the home page */
    if ("/".equals(requestURI)) {
      chain.doFilter(request, response);
      return;
    }

    /* Validate requests to the static resources */
    final String staticRequestPath = requestURI;
    final String staticFilePath = WEBAPP_PATH + staticRequestPath;
    final InputStream fileStream = getClass().getResourceAsStream(staticFilePath);
    if (fileStream != null) {
      chain.doFilter(request, response);
      return;
    }

    /* Redirect URLs that do not pass our validity rule */
    if (redirectPattern.matcher(requestURI).matches()) {
      httpResponse.sendRedirect(redirectPath);
      return;
    }

    chain.doFilter(request, response);
  }
}
