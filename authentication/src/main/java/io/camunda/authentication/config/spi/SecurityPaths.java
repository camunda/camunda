/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config.spi;

import java.util.Set;

/**
 * OC's path patterns that no configuration can change, readable without a {@link
 * SecurityPathAdapter} instance. Configuration-dependent sets stay private to the adapter, so the
 * {@code webapp-enabled} gate can only be read from the configured bean.
 */
public final class SecurityPaths {

  // Cluster paths only — never add a tenant prefix here. SecurityPathAdapter adds the
  // /physical-tenants/default versions instead, and only when no physical tenant is configured, so
  // the alias is served by one mechanism, never both.
  public static final Set<String> API_PATHS =
      Set.of("/api/**", "/v1/**", "/v2/**", "/mcp/**", "/.well-known/oauth-protected-resource/**");

  public static final Set<String> UNPROTECTED_API_PATHS =
      Set.of(
          "/v2/license",
          "/v2/setup/user",
          "/v2/status",
          "/v1/external/process/**",
          "/.well-known/oauth-protected-resource/**");

  // Served by CSL's unprotected-paths chain, which sits ahead of every authenticated chain and
  // installs no authentication filter at all. That is what /cluster/v2/status needs and what
  // listing it in UNPROTECTED_API_PATHS could not give it: a `permitAll` inside an authenticated
  // chain still lets the Basic or bearer filter reject a credential it does not recognise, and the
  // cluster-admin chain recognises only cluster-admin credentials — so a client migrating here from
  // /v2/status, which sends its ordinary API credentials on every request, would be answered 401 by
  // a health endpoint. Here the Authorization header is never inspected. The exact path is listed,
  // so the rest of /cluster/v2/** stays with the cluster-admin chains.
  public static final Set<String> UNPROTECTED_PATHS =
      Set.of(
          "/error",
          "/actuator/**",
          "/ready",
          "/health",
          "/startup",
          "/favicon.ico",
          "/cluster/v2/status");

  private SecurityPaths() {}
}
