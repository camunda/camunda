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
 * Cross-tenant rule: no two physical tenants may resolve to overlapping primary-storage backup key
 * spaces (see {@link BackupStoreLocation}).
 *
 * <p>A backup is addressed by {@code partitionId/checkpointId/nodeId}, which names no tenant, and
 * every tenant's partition group numbers its partitions from 1. Two tenants sharing a key space
 * therefore write their partition 1 backups to the same keys, and because retention lists and
 * deletes by partition wildcard, each tenant's retention job also deletes the other's backups — a
 * backup that silently restores another tenant's data, or is gone when it is needed.
 *
 * <p>Comparing <em>fully resolved</em> locations means tenants that both inherit the root backup
 * configuration (without overriding it) collide too, which is the case this rule mainly exists for:
 * inheriting is the default, so a multi-tenant cluster that configures backups once at the root
 * would otherwise point every tenant at one bucket.
 *
 * <p>Tenants without a backup store write no backups and are skipped. The synthesized {@code
 * default} tenant participates like any other. Single-tenant deployments resolve to a one-entry map
 * and are a no-op.
 */
@NullMarked
class PrimaryStorageBackupIsolationValidation implements CrossTenantValidation {

  @Override
  public void validate(final Map<String, Camunda> resolvedByTenant) {
    if (resolvedByTenant.size() <= 1) {
      // a single tenant (the common single-tenant deployment) cannot collide with anything
      return;
    }

    final Map<String, BackupStoreLocation> locationsByTenant = new LinkedHashMap<>();
    resolvedByTenant.forEach(
        (tenantId, camunda) -> {
          final var location =
              BackupStoreLocation.of(camunda.getData().getPrimaryStorage().getBackup());
          if (location != null) {
            locationsByTenant.put(tenantId, location);
          }
        });

    final List<String> collisions = new ArrayList<>();
    final List<String> reported = new ArrayList<>();
    locationsByTenant.forEach(
        (tenantId, location) -> {
          if (reported.contains(tenantId)) {
            return;
          }
          final var sharing = new ArrayList<>(List.of(tenantId));
          locationsByTenant.forEach(
              (otherId, otherLocation) -> {
                if (!otherId.equals(tenantId) && location.sharesKeySpaceWith(otherLocation)) {
                  sharing.add(otherId);
                }
              });
          if (sharing.size() > 1) {
            reported.addAll(sharing);
            collisions.add(
                String.format(
                    "tenants %s share the same backup location [%s]",
                    sharing, location.describe()));
          }
        });

    if (!collisions.isEmpty()) {
      throw new UnifiedConfigurationException(
          "Physical tenants must not share a primary-storage backup location, or their backups "
              + "would overwrite and delete each other: backups are addressed by partition id, and "
              + "every tenant has a partition 1. Give each tenant its own bucket, container or base "
              + "path under data.primary-storage.backup. Conflicts: "
              + String.join("; ", collisions));
    }
  }
}
