/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.config.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

public class LoggingFilter extends OncePerRequestFilter {

  private static final Logger LOGGER = LoggerFactory.getLogger(LoggingFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws IOException, ServletException {

    final long startTime = System.currentTimeMillis();
    filterChain.doFilter(request, response);
    final long timeTaken = System.currentTimeMillis() - startTime;

    LOGGER.info(
        "Request Processed: "
            + "Method={}, "
            + "URI={}, "
            + "Response Code={}, "
            + "Time Taken={} ms",
        request.getMethod(),
        request.getRequestURI(),
        response.getStatus(),
        timeTaken);
  }
}
