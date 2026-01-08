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
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;

public class EndpointAccessErrorFilter extends HttpFilter {
  private final ObjectMapper objectMapper;
  private final String errorMessage;

  public EndpointAccessErrorFilter(
      @Autowired final ObjectMapper objectMapper, final String errorMessage) {
    this.objectMapper = objectMapper;
    this.errorMessage = errorMessage;
  }

  @Override
  protected void doFilter(
      final HttpServletRequest req, final HttpServletResponse res, final FilterChain chain)
      throws IOException {
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
