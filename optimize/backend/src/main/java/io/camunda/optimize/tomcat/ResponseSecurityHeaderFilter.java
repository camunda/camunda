/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.tomcat;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

public class ResponseSecurityHeaderFilter implements Filter {

  private final ConfigurationService configurationService;

  public ResponseSecurityHeaderFilter(final ConfigurationService configurationService) {
    this.configurationService = configurationService;
  }

  @Override
  public void doFilter(
      final ServletRequest request, final ServletResponse response, final FilterChain chain)
      throws IOException, ServletException {
    final HttpServletResponse httpResponse = (HttpServletResponse) response;

    final Map<String, String> headers =
        configurationService.getSecurityConfiguration().getResponseHeaders().getHeadersWithValues();

    for (final String key : headers.keySet()) {
      httpResponse.addHeader(key, headers.get(key));
    }

    chain.doFilter(request, response);
  }
}
