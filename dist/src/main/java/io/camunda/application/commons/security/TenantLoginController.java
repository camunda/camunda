/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import io.camunda.authentication.filters.PhysicalTenantAuthorizationFilter;
import io.camunda.security.configuration.PhysicalTenantIdpRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant-aware login. Two endpoints under the existing {@code /login/**} namespace (already in
 * {@code WEBAPP_PATHS}); the exact path {@code /login} is left untouched for the basic-auth form
 * chain.
 *
 * <ul>
 *   <li>{@code GET /login/{tenantId}} — picker; returns the JSON list of IdPs assigned to the
 *       tenant, each paired with a tenant-bound login start URL.
 *   <li>{@code GET /login/{tenantId}/{idpId}} — login start; validates the IdP↔tenant assignment,
 *       refuses to rebind when the session is already pinned to a different tenant (409), stamps
 *       {@code boundTenantId} on the HTTP session, then 302-redirects to the existing {@code
 *       /oauth2/authorization/{idpId}} URL handled by {@code
 *       ClientAwareOAuth2AuthorizationRequestResolver}.
 * </ul>
 */
@RestController
public class TenantLoginController {

  private static final String OAUTH2_AUTH_PREFIX = "/oauth2/authorization/";

  private final PhysicalTenantIdpRegistry registry;

  public TenantLoginController(final PhysicalTenantIdpRegistry registry) {
    this.registry = registry;
  }

  @GetMapping("/login/{tenantId}")
  public ResponseEntity<TenantLoginResponse> picker(@PathVariable final String tenantId) {
    if (!registry.tenantIds().contains(tenantId)) {
      return ResponseEntity.notFound().build();
    }
    final var options =
        registry.getIdpsForTenant(tenantId).stream()
            .map(id -> new IdpOption(id, "/login/" + tenantId + "/" + id))
            .toList();
    return ResponseEntity.ok(new TenantLoginResponse(tenantId, options));
  }

  @GetMapping("/login/{tenantId}/{idpId}")
  public ResponseEntity<Void> startLogin(
      @PathVariable final String tenantId,
      @PathVariable final String idpId,
      final HttpServletRequest request) {
    if (!registry.tenantIds().contains(tenantId)) {
      return ResponseEntity.notFound().build();
    }
    if (!registry.getIdpsForTenant(tenantId).contains(idpId)) {
      return ResponseEntity.badRequest().build();
    }

    final var existing = request.getSession(false);
    if (existing != null) {
      final var bound =
          existing.getAttribute(PhysicalTenantAuthorizationFilter.BOUND_TENANT_ATTRIBUTE);
      if (bound != null && !bound.equals(tenantId)) {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
      }
    }

    request
        .getSession(true)
        .setAttribute(PhysicalTenantAuthorizationFilter.BOUND_TENANT_ATTRIBUTE, tenantId);

    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(OAUTH2_AUTH_PREFIX + idpId))
        .build();
  }

  public record TenantLoginResponse(String tenantId, List<IdpOption> idps) {}

  public record IdpOption(String id, String loginUrl) {}
}
