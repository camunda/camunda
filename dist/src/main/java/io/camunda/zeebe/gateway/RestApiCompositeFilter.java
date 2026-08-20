/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.web.filter.CompositeFilter;

public class RestApiCompositeFilter extends CompositeFilter {

  private static final Logger LOG = Loggers.GATEWAY_LOGGER;

  public RestApiCompositeFilter(final List<Filter> filters) {
    setFilters(filters);
  }

  @Override
  public void doFilter(
      final ServletRequest request, final ServletResponse response, final FilterChain chain)
      throws IOException, ServletException {
    try {
      super.doFilter(request, response, chain);
    } catch (final Exception e) {

      final var instance =
          request instanceof HttpServletRequest
              ? ((HttpServletRequest) request).getRequestURI()
              : "";
      final var msg =
          """
          { "type": "about:blank", "status": 500, "title": "Filter issue", "detail": "%s", "instance": "%s" }
          """
              .formatted(e.getMessage(), instance);

      if (response instanceof HttpServletResponse) {
        ((HttpServletResponse) response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      }

      response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
      response.getWriter().write(msg);
    }
  }
}
