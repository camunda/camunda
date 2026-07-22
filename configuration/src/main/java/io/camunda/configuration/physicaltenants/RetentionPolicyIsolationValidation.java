/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.SecondaryStorage;
import io.camunda.configuration.UnifiedConfigurationException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

/**
 * Cross-tenant rule: no two physical tenants with retention enabled may resolve to the same {@link
 * RetentionPolicyIdentity} — the same ILM/ISM lifecycle policy on the same cluster.
 *
 * <p>When retention is enabled, each document-based (Elasticsearch/OpenSearch) tenant's schema
 * manager creates a lifecycle policy named {@code
 * data.secondary-storage.<database>.history.policy-name} on its target cluster. Because that policy
 * is a cluster-global named object — <em>not</em> scoped to the tenant's index prefix — two tenants
 * that share a cluster (an allowed setup when their index prefixes differ) would silently overwrite
 * each other's policy unless they use distinct policy names. This rule complements {@link
 * SecondaryStorageIsolationValidation}, which guards the indices themselves.
 *
 * <p>Only tenants with retention enabled participate: a tenant with retention disabled never
 * creates a lifecycle policy and therefore cannot collide. RDBMS and {@code none} tenants have no
 * ILM/ISM policy and are skipped.
 *
 * <p>The synthesized {@code default} tenant participates like any other. Single-tenant deployments
 * resolve to a one-entry map and are a no-op. Colliding tenants are reported as a single grouped
 * error, not O(n²) pairwise messages.
 *
 * <p><b>Out of scope:</b> the separate <em>usage-metrics</em> lifecycle policy. It is created with
 * a fixed default name today and is not yet configurable through unified configuration; extending
 * this rule to cover it is tracked by <a
 * href="https://github.com/camunda/camunda/issues/58237">#58237</a>.
 */
@NullMarked
class RetentionPolicyIsolationValidation implements CrossTenantValidation {

  @Override
  public void validate(final Map<String, Camunda> resolvedByTenant) {
    if (resolvedByTenant.size() <= 1) {
      // a single tenant cannot collide with anything
      return;
    }

    final Map<RetentionPolicyIdentity, List<String>> tenantsByPolicy = new LinkedHashMap<>();
    resolvedByTenant.forEach(
        (tenantId, camunda) -> {
          final SecondaryStorage secondaryStorage = camunda.getData().getSecondaryStorage();
          if (!secondaryStorage.getRetention().isEnabled()) {
            // retention disabled → no lifecycle policy is created → cannot collide
            return;
          }
          // rdbms/none have no ILM/ISM policy; elasticsearchOrOpensearch() is empty for them
          secondaryStorage
              .elasticsearchOrOpensearch()
              .ifPresent(
                  database ->
                      tenantsByPolicy
                          .computeIfAbsent(
                              RetentionPolicyIdentity.of(secondaryStorage.getType(), database),
                              k -> new ArrayList<>())
                          .add(tenantId));
        });

    final List<String> collisions = new ArrayList<>();
    tenantsByPolicy.forEach(
        (identity, tenantIds) -> {
          if (tenantIds.size() > 1) {
            collisions.add(
                String.format(
                    "tenants %s share the same lifecycle policy [%s]",
                    tenantIds, identity.describe()));
          }
        });

    if (!collisions.isEmpty()) {
      throw new UnifiedConfigurationException(
          "Physical tenants with retention enabled must not share a secondary-storage lifecycle "
              + "(ILM/ISM) policy, or they would overwrite each other's retention policy on the same "
              + "cluster. Use a distinct data.secondary-storage.<database>.history.policy-name per "
              + "tenant that shares a cluster. Conflicts: "
              + String.join("; ", collisions));
    }
  }
}
