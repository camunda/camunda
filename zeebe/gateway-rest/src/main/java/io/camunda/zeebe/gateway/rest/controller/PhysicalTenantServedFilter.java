/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.spring.utils.PhysicalTenantContext.PHYSICAL_TENANTS_PATH_SEGMENT;

import io.camunda.cluster.PhysicalTenant;
import io.camunda.cluster.PhysicalTenantRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Rejects every {@code /physical-tenants/{id}/...} request for a physical tenant whose HTTP
 * security chain this node does not have, with the same fixed 404 as an unknown tenant.
 *
 * <p>The Camunda Security Library builds one security chain per physical tenant once, at startup. A
 * tenant added at runtime has no chain, and a removed tenant still has one. Both must fail closed
 * rather than reach a controller, so this filter only lets through the incarnation of a tenant that
 * existed when the chains were built and that is still part of the node. It runs before Spring
 * Security, next to {@link PhysicalTenantFilter}; see ADR-0003.
 */
@NullMarked
public final class PhysicalTenantServedFilter extends HttpFilter {

  private static final byte[] NOT_FOUND_BODY =
      """
      {"type":"about:blank","status":404,"title":"Not Found"}"""
          .getBytes(StandardCharsets.UTF_8);

  private final PhysicalTenantRegistry registry;
  private final Map<String, UUID> incarnationsWithSecurityChain;

  /**
   * @param registry the live tenant set; must still hold the boot tenants, since their incarnations
   *     are the ones with a security chain
   */
  public PhysicalTenantServedFilter(final PhysicalTenantRegistry registry) {
    this.registry = registry;
    incarnationsWithSecurityChain =
        registry.tenants().stream()
            .collect(
                Collectors.toUnmodifiableMap(PhysicalTenant::id, PhysicalTenant::incarnationId));
  }

  @Override
  protected void doFilter(
      final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain)
      throws IOException, ServletException {
    final var physicalTenantId = tenantIdOf(request);
    if (physicalTenantId != null && !isServed(physicalTenantId)) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      response.setContentType("application/problem+json");
      response.setContentLength(NOT_FOUND_BODY.length);
      response.getOutputStream().write(NOT_FOUND_BODY);
      return;
    }
    chain.doFilter(request, response);
  }

  private boolean isServed(final String physicalTenantId) {
    final var incarnation = incarnationsWithSecurityChain.get(physicalTenantId);
    return incarnation != null
        && registry
            .find(physicalTenantId)
            .filter(current -> current.incarnationId().equals(incarnation))
            .isPresent();
  }

  private static @Nullable String tenantIdOf(final HttpServletRequest request) {
    final String uri = request.getRequestURI();
    if (uri == null) {
      return null;
    }
    final String contextPath = request.getContextPath();
    final String path =
        contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
            ? uri.substring(contextPath.length())
            : uri;
    if (!path.startsWith(PHYSICAL_TENANTS_PATH_SEGMENT)) {
      return null;
    }
    final int start = PHYSICAL_TENANTS_PATH_SEGMENT.length();
    final int slash = path.indexOf('/', start);
    final String id = slash > start ? path.substring(start, slash) : path.substring(start);
    return id.isEmpty() ? null : id;
  }
}
