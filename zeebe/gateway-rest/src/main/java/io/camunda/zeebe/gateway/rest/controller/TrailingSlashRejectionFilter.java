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
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;

/**
 * Rejects {@code /v2/...} requests ending in {@code /} with a 400.
 *
 * <p>No v2 route ends in a slash, so such a request almost always means an empty trailing path
 * parameter (e.g. {@code DELETE /v2/groups/}). Spring never matches an empty segment against a
 * {@code {groupId}} variable, so without this filter the request falls through to the
 * static-resource handler and returns a misleading 404 before any identifier validation runs.
 */
public final class TrailingSlashRejectionFilter extends HttpFilter {

  private static final String API_PREFIX = "/v2/";

  private final ObjectMapper objectMapper;

  public TrailingSlashRejectionFilter(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilter(
      final HttpServletRequest req, final HttpServletResponse res, final FilterChain chain)
      throws IOException, ServletException {
    final String path = req.getRequestURI().substring(req.getContextPath().length());
    if (!path.startsWith(API_PREFIX) || !path.endsWith("/")) {
      chain.doFilter(req, res);
      return;
    }
    res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    res.setContentType(MediaType.APPLICATION_PROBLEM_JSON.toString());
    final var detail =
        GatewayErrorMapper.createProblemDetail(
            HttpStatusCode.valueOf(HttpServletResponse.SC_BAD_REQUEST),
            "Missing path parameter: request path '%s' must not end with '/'".formatted(path),
            "INVALID_ARGUMENT");
    detail.setInstance(URI.create(req.getRequestURI()));
    res.getWriter().write(objectMapper.writeValueAsString(detail));
  }
}
