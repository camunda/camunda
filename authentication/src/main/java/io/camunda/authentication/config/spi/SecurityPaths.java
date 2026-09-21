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

  // Tenant-prefixed paths (/physical-tenants/<id>/...) are deliberately NOT listed here. They are
  // owned exclusively by the per-tenant scoped security chains that PhysicalTenantScopeProvider
  // contributes (CSL derives each scope's matcher as basePath + these apiPaths, e.g.
  // /physical-tenants/<id>/v2/**). The cluster chain and a scoped chain share ORDER_API, so
  // listing the tenant prefix here would let the cluster chain also match a tenant request and, if
  // it wins the same-order tie-break, validate the token against the cluster's providers instead of
  // the tenant's — breaking per-tenant audience isolation. Keep this list cluster-only.
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
  // so the rest of /cluster/v2/** stays with the cluster-admin chains. /cluster/v2/status/upgrade
  // is listed for the identical reason (camunda/camunda#61619).
  public static final Set<String> UNPROTECTED_PATHS =
      Set.of(
          "/error",
          "/actuator/**",
          "/ready",
          "/health",
          "/startup",
          "/favicon.ico",
          "/cluster/v2/status",
          "/cluster/v2/status/upgrade");

  private SecurityPaths() {}
}
