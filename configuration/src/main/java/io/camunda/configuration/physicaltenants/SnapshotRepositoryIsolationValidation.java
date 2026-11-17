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
 * Cross-tenant rule: no two physical tenants may resolve to the same {@link
 * SnapshotRepositoryIdentity} — the same Elasticsearch/OpenSearch snapshot repository on the same
 * cluster.
 *
 * <p>History snapshots carry no tenant id in their names, so the repository <em>is</em> the
 * boundary between two tenants' backups. Sharing one puts every tenant's snapshots within reach of
 * an {@code _all} listing, a {@code DELETE _snapshot/<repo>/*}, a repository delete, or an operator
 * restoring the wrong repository. This mirrors {@link PrimaryStorageBackupIsolationValidation},
 * which hard-fails on a shared primary-storage backup location for the same reason.
 *
 * <p>Comparing <em>fully resolved</em> configuration means tenants that merely inherit a root
 * {@code repository-name} collide too — the case this rule mainly exists for, since inheriting is
 * the default.
 *
 * <p>A tenant with no {@code repository-name} takes no snapshots and is skipped, not rejected: that
 * deployment is already surfaced by a startup warning and its backup endpoints reject every request
 * at runtime. RDBMS and {@code none} tenants have no snapshot repository and are skipped as well.
 *
 * <p>The synthesized {@code default} tenant participates like any other. Single-tenant deployments
 * resolve to a one-entry map and are a no-op. Colliding tenants are reported as a single grouped
 * error, not O(n²) pairwise messages.
 */
@NullMarked
class SnapshotRepositoryIsolationValidation implements CrossTenantValidation {

  @Override
  public void validate(final Map<String, Camunda> resolvedByTenant) {
    if (resolvedByTenant.size() <= 1) {
      // a single tenant cannot collide with anything
      return;
    }

    final Map<SnapshotRepositoryIdentity, List<String>> tenantsByRepository = new LinkedHashMap<>();
    resolvedByTenant.forEach(
        (tenantId, camunda) -> {
          final var secondaryStorage = camunda.getData().getSecondaryStorage();
          // rdbms/none take no ES/OS snapshots; elasticsearchOrOpensearch() is empty for them
          secondaryStorage
              .elasticsearchOrOpensearch()
              .map(database -> SnapshotRepositoryIdentity.of(secondaryStorage.getType(), database))
              .ifPresent(
                  identity ->
                      tenantsByRepository
                          .computeIfAbsent(identity, k -> new ArrayList<>())
                          .add(tenantId));
        });

    final List<String> collisions = new ArrayList<>();
    tenantsByRepository.forEach(
        (identity, tenantIds) -> {
          if (tenantIds.size() > 1) {
            collisions.add(
                String.format(
                    "tenants %s share the same snapshot repository [%s]",
                    tenantIds, identity.describe()));
          }
        });

    if (!collisions.isEmpty()) {
      throw new UnifiedConfigurationException(
          "Physical tenants must not share a secondary-storage snapshot repository."
              + "Register one repository per tenant and set a "
              + "distinct data.secondary-storage.<database>.backup.repository-name for each. "
              + "Conflicts: "
              + String.join("; ", collisions));
    }
  }
}
