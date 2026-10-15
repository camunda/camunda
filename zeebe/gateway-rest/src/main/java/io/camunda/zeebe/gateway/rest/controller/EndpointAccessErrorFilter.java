/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.gateway.mapping.http.GatewayErrorMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.function.BiPredicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;

public class EndpointAccessErrorFilter extends HttpFilter {
  private final ObjectMapper objectMapper;
  private final String errorMessage;
  private final List<PathPattern> pathPatterns;
  private final BiPredicate<String, PathContainer> exemption;

  public EndpointAccessErrorFilter(
      @Autowired final ObjectMapper objectMapper,
      final String errorMessage,
      final List<PathPattern> pathPatterns) {
    this(objectMapper, errorMessage, pathPatterns, (method, path) -> false);
  }

  /**
   * @param exemption tests the HTTP method and request path; requests for which it returns {@code
   *     true} are let through even if they match one of the {@code pathPatterns}
   */
  public EndpointAccessErrorFilter(
      final ObjectMapper objectMapper,
      final String errorMessage,
      final List<PathPattern> pathPatterns,
      final BiPredicate<String, PathContainer> exemption) {
    this.objectMapper = objectMapper;
    this.errorMessage = errorMessage;
    this.pathPatterns = pathPatterns;
    this.exemption = exemption;
  }

  @Override
  protected void doFilter(
      final HttpServletRequest req, final HttpServletResponse res, final FilterChain chain)
      throws IOException, ServletException {
    final var requestPath =
        PathContainer.parsePath(req.getRequestURI().substring(req.getContextPath().length()));
    if (pathPatterns.stream().noneMatch(p -> p.matches(requestPath))
        || exemption.test(req.getMethod(), requestPath)) {
      chain.doFilter(req, res);
      return;
    }
    res.setStatus(HttpServletResponse.SC_FORBIDDEN);
    res.setContentType(MediaType.APPLICATION_PROBLEM_JSON.toString());
    final var detail =
        GatewayErrorMapper.createProblemDetail(
            HttpStatusCode.valueOf(HttpServletResponse.SC_FORBIDDEN),
            String.format("%s endpoint is not accessible: %s", req.getRequestURI(), errorMessage),
            "Access issue");
    detail.setInstance(URI.create(req.getRequestURI()));
    res.getWriter().write(objectMapper.writeValueAsString(detail));
  }
}
