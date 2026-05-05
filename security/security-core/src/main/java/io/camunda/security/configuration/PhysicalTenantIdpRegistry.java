/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Immutable startup snapshot of physical-tenant → OIDC-provider assignments. Built once from {@code
 * camunda.identity.engine-idp-assignments} and held in memory for the lifetime of the process.
 *
 * <p>Tenant ids matching reserved path segments (e.g. {@code login}, {@code oauth2}, {@code
 * actuator}) or all-numeric ids (collide with the user-task id matcher) are rejected at
 * construction; the bean factory fails and the application refuses to start.
 */
public final class PhysicalTenantIdpRegistry {

  /**
   * Reserved tenant ids — first-path-segment values used by the application or its security
   * configuration. A tenant matching one of these would shadow URL routing.
   */
  static final Set<String> RESERVED_TENANT_IDS =
      Set.of(
          // From WEBAPP_PATHS / form login / OIDC chain
          "login",
          "logout",
          "identity",
          "admin",
          "operate",
          "tasklist",
          "optimize",
          "console",
          "webmodeler",
          "sso-callback",
          "oauth2",
          "processes",
          "decisions",
          "instances",
          "new",
          // From UNPROTECTED_PATHS / actuator / docs
          "error",
          "actuator",
          "ready",
          "health",
          "startup",
          "post-logout",
          "swagger",
          "swagger-ui",
          "v3",
          "favicon.ico",
          // From API_PATHS / UNPROTECTED_API_PATHS
          "api",
          "v1",
          "v2",
          "mcp",
          ".well-known",
          // Static assets / Spring defaults
          "default-ui.css",
          "assets");

  private static final Pattern ALL_NUMERIC = Pattern.compile("^[0-9]+$");

  private final Map<String, List<String>> tenantToIdps;

  public PhysicalTenantIdpRegistry(final Map<String, List<String>> assignments) {
    final Map<String, List<String>> forward = new HashMap<>();

    if (assignments != null) {
      assignments.forEach(
          (tenantId, idps) -> {
            validate(tenantId);
            forward.put(tenantId, List.copyOf(idps));
          });
    }

    tenantToIdps = Map.copyOf(forward);
  }

  public List<String> getIdpsForTenant(final String tenantId) {
    return tenantToIdps.getOrDefault(tenantId, List.of());
  }

  public Set<String> tenantIds() {
    return tenantToIdps.keySet();
  }

  private static void validate(final String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException(
          "Physical tenant id must not be null or blank in camunda.identity.engine-idp-assignments");
    }
    if (RESERVED_TENANT_IDS.contains(tenantId)) {
      throw new IllegalArgumentException(
          "Physical tenant id '"
              + tenantId
              + "' is reserved (collides with a known URL path segment); choose a different id."
              + " Reserved ids: "
              + RESERVED_TENANT_IDS);
    }
    if (ALL_NUMERIC.matcher(tenantId).matches()) {
      throw new IllegalArgumentException(
          "Physical tenant id '"
              + tenantId
              + "' must not be all-numeric (collides with the user-task id route).");
    }
  }
}
