/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static io.camunda.spring.utils.PhysicalTenantContext.PATH_VARIABLE_PHYSICAL_TENANT_ID;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.gateway.mapping.http.GatewayErrorMapper;
import io.camunda.spring.utils.PhysicalTenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;

public class EndpointAccessErrorFilter extends HttpFilter {
  private final ObjectMapper objectMapper;
  private final String errorMessage;
  private final List<PathPattern> pathPatterns;
  private final Predicate<String> blockedForPhysicalTenant;

  public EndpointAccessErrorFilter(
      @Autowired final ObjectMapper objectMapper,
      final String errorMessage,
      final List<PathPattern> pathPatterns) {
    this(objectMapper, errorMessage, pathPatterns, physicalTenantId -> true);
  }

  /**
   * Blocks a matching request only for the physical tenants {@code blockedForPhysicalTenant}
   * accepts. The tenant is the {@link PhysicalTenantContext#PATH_VARIABLE_PHYSICAL_TENANT_ID} path
   * variable of the matching pattern, or the {@code default} tenant for a pattern without it. It is
   * read from the path because this filter can run before the tenant is resolved on the request.
   */
  public EndpointAccessErrorFilter(
      final ObjectMapper objectMapper,
      final String errorMessage,
      final List<PathPattern> pathPatterns,
      final Predicate<String> blockedForPhysicalTenant) {
    this.objectMapper = objectMapper;
    this.errorMessage = errorMessage;
    this.pathPatterns = pathPatterns;
    this.blockedForPhysicalTenant = blockedForPhysicalTenant;
  }

  @Override
  protected void doFilter(
      final HttpServletRequest req, final HttpServletResponse res, final FilterChain chain)
      throws IOException, ServletException {
    final var requestPath =
        PathContainer.parsePath(req.getRequestURI().substring(req.getContextPath().length()));
    final var match =
        pathPatterns.stream()
            .map(pattern -> pattern.matchAndExtract(requestPath))
            .filter(Objects::nonNull)
            .findFirst();
    if (match.isEmpty()
        || !blockedForPhysicalTenant.test(
            match
                .get()
                .getUriVariables()
                .getOrDefault(PATH_VARIABLE_PHYSICAL_TENANT_ID, DEFAULT_PHYSICAL_TENANT_ID))) {
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
