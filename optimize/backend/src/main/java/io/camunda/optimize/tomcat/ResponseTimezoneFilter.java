/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.tomcat;

import static io.camunda.optimize.rest.constants.RestConstants.X_OPTIMIZE_CLIENT_TIMEZONE;
import static io.camunda.optimize.rest.util.TimeZoneUtil.extractTimezone;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.ZoneId;
import java.util.Optional;
import org.slf4j.Logger;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

public class ResponseTimezoneFilter implements Filter {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(ResponseTimezoneFilter.class);

  public ResponseTimezoneFilter() {}

  @Override
  public void init(final FilterConfig filterConfig) {
    // nothing to do here
  }

  @Override
  public void doFilter(
      final ServletRequest request, final ServletResponse response, final FilterChain chain)
      throws IOException, ServletException {
    final HttpServletRequest servletRequest = (HttpServletRequest) request;
    final ZoneId timezone = extractTimezone(servletRequest);
    Optional.ofNullable(RequestContextHolder.getRequestAttributes())
        .ifPresent(
            attrs ->
                attrs.setAttribute(
                    X_OPTIMIZE_CLIENT_TIMEZONE, timezone.getId(), RequestAttributes.SCOPE_REQUEST));
    chain.doFilter(request, response);
  }

  @Override
  public void destroy() {
    // nothing to do here
  }
}
