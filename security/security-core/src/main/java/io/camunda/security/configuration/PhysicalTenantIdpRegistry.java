/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable startup snapshot of physical-tenant ↔ OIDC-provider assignments. Built once from {@code
 * camunda.identity.engine-idp-assignments} and held in memory for the lifetime of the process.
 * Lookups are O(1) in both directions.
 */
public final class PhysicalTenantIdpRegistry {

  private final Map<String, List<String>> tenantToIdps;
  private final Map<String, List<String>> idpToTenants;

  public PhysicalTenantIdpRegistry(final Map<String, List<String>> assignments) {
    final Map<String, List<String>> forward = new HashMap<>();
    final Map<String, List<String>> reverse = new HashMap<>();

    if (assignments != null) {
      assignments.forEach(
          (tenantId, idps) -> {
            forward.put(tenantId, List.copyOf(idps));
            for (final var idp : idps) {
              reverse.computeIfAbsent(idp, k -> new ArrayList<>()).add(tenantId);
            }
          });
    }

    reverse.replaceAll((k, v) -> List.copyOf(v));

    tenantToIdps = Map.copyOf(forward);
    idpToTenants = Map.copyOf(reverse);
  }

  public List<String> getIdpsForTenant(final String tenantId) {
    return tenantToIdps.getOrDefault(tenantId, List.of());
  }

  public List<String> getTenantsForIdp(final String idpId) {
    return idpToTenants.getOrDefault(idpId, List.of());
  }

  public Set<String> tenantIds() {
    return tenantToIdps.keySet();
  }
}
