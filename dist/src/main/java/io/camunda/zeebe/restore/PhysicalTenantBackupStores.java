/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg.BackupStoreFactory;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link BackupStore} of each physical tenant, created on first use.
 *
 * <p>Lazy, not eagerly built for every configured tenant. A tenant may legitimately have no backup
 * store at all — the configuration isolation rules permit it — and a restore that does not name
 * that tenant has no business failing because of it. Which tenants a run restores is only knownr
 * once its arguments are resolved, well after this is constructed, so the decision is deferred to
 * the point where a store is actually asked for.
 *
 * <p>A store per tenant is required, not tidiness: {@link
 * io.camunda.zeebe.backup.api.BackupIdentifier} names no physical tenant — it addresses a backup by
 * partition, checkpoint and node alone — and partition numbers restart at 1 in every tenant's
 * partition group. Two tenants sharing one key space would write their partition 1 backups to the
 * same keys, and each tenant's retention would delete the other's. {@code BackupStoreLocation}
 * rejects that at startup; reading each tenant back through its own store is the other half of it.
 */
@NullMarked
final class PhysicalTenantBackupStores implements AutoCloseable {

  private final Map<String, BrokerCfg> configurations;
  private final Map<String, BackupStore> created = new LinkedHashMap<>();

  PhysicalTenantBackupStores(final Map<String, BrokerCfg> configurations) {
    this.configurations = Map.copyOf(configurations);
  }

  /**
   * That tenant's backup store, creating it if this is the first ask.
   *
   * @throws IllegalArgumentException if the tenant has no backup store configured, which only
   *     matters for a tenant this run is actually restoring
   */
  BackupStore forPhysicalTenant(final String physicalTenantId) {
    final var configuration = configurations.get(physicalTenantId);
    if (configuration == null) {
      throw new IllegalArgumentException(
          "No configuration for physical tenant '%s'".formatted(physicalTenantId));
    }
    return created.computeIfAbsent(
        physicalTenantId,
        tenantId -> {
          final var store = BackupStoreFactory.createStore(configuration.getData().getBackup());
          if (store == null) {
            throw new IllegalArgumentException(
                "No backup store configured for physical tenant '%s', cannot restore it from backup."
                    .formatted(tenantId));
          }
          return store;
        });
  }

  /** Only the stores that were actually created, so a tenant never restored is never opened. */
  Collection<BackupStore> openStores() {
    return created.values();
  }

  @Override
  public void close() {
    created.values().forEach(store -> store.closeAsync().join());
    created.clear();
  }
}
