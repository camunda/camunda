/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import io.camunda.security.configuration.PhysicalTenantIdpRegistry;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant-aware login picker.
 *
 * <p>{@code GET /login/{tenantId}} returns the JSON list of OIDC providers assigned to the tenant,
 * each paired with the standard OAuth2 entry URL annotated with a {@code ?tenant=...} query
 * parameter. The entry URL is consumed by Spring's OIDC chain (specifically by {@link
 * io.camunda.authentication.config.TenantAwareOAuth2AuthorizationRequestResolver
 * TenantAwareOAuth2AuthorizationRequestResolver}), which validates the IdP↔tenant assignment and
 * stamps the session before redirecting to the IdP.
 *
 * <p>Already-authenticated callers receive 409 — the user must logout before binding a new tenant.
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
    if (isAuthenticated()) {
      return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
    if (!registry.tenantIds().contains(tenantId)) {
      return ResponseEntity.notFound().build();
    }
    final var encodedTenant = URLEncoder.encode(tenantId, StandardCharsets.UTF_8);
    final var options =
        registry.getIdpsForTenant(tenantId).stream()
            .map(
                id ->
                    new IdpOption(
                        id,
                        OAUTH2_AUTH_PREFIX
                            + URLEncoder.encode(id, StandardCharsets.UTF_8)
                            + "?tenant="
                            + encodedTenant))
            .toList();
    return ResponseEntity.ok(new TenantLoginResponse(tenantId, options));
  }

  private static boolean isAuthenticated() {
    final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal());
  }

  public record TenantLoginResponse(String tenantId, List<IdpOption> idps) {}

  public record IdpOption(String id, String loginUrl) {}
}
