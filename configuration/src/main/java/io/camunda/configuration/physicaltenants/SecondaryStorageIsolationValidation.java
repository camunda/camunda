/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.UnifiedConfigurationException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

/**
 * Cross-tenant rule: no two physical tenants may resolve to the same {@link StorageIdentity}.
 *
 * <p>Two tenants sharing a storage identity would silently double-write into the same
 * indices/tables — the headline footgun of the physical-tenant model. Comparing <em>fully
 * resolved</em> identities means tenants that both inherit the root storage (without overriding it)
 * collide too, which implicitly enforces "each tenant must set its own storage". The synthesized
 * {@code default} tenant participates like any other. Single-tenant deployments resolve to a
 * one-entry map and are a no-op.
 *
 * <p>Tenants are grouped by identity and any group larger than one is reported as a single error
 * naming all the colliding tenants — not as O(n²) pairwise messages.
 */
@NullMarked
public class SecondaryStorageIsolationValidation implements CrossTenantValidation {

  @Override
  public void validate(final Map<String, Camunda> resolvedByTenant) {
    if (resolvedByTenant.size() <= 1) {
      // a single tenant (the common single-tenant deployment) cannot collide with anything
      return;
    }

    final Map<StorageIdentity, List<String>> tenantsByIdentity = new LinkedHashMap<>();
    resolvedByTenant.forEach(
        (tenantId, camunda) -> {
          final StorageIdentity identity = StorageIdentity.of(camunda);
          if (identity != null) {
            tenantsByIdentity.computeIfAbsent(identity, k -> new ArrayList<>()).add(tenantId);
          }
        });

    final List<String> collisions = new ArrayList<>();
    tenantsByIdentity.forEach(
        (identity, tenantIds) -> {
          if (tenantIds.size() > 1) {
            collisions.add(
                String.format(
                    "tenants %s share the same secondary-storage location [%s]",
                    tenantIds, identity.describe()));
          }
        });

    if (!collisions.isEmpty()) {
      throw new UnifiedConfigurationException(
          "Physical tenants must not share a secondary-storage location, or they would write into "
              + "the same database. Use a distinct connection, or a distinct index/table prefix per "
              + "tenant. Conflicts: "
              + String.join("; ", collisions));
    }
  }
}
