/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import io.camunda.cluster.migration.MigrationConditionStatus;
import io.camunda.cluster.migration.MigrationStatusProvider;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * Reports whether the RDBMS schema of every configured physical tenant has migrated to the running
 * application version, for the upgrade-readiness endpoint.
 *
 * <p>Reports one entry per physical tenant.
 */
public final class RdbmsSchemaMigrationStatusProvider implements MigrationStatusProvider {

  public static final String CONDITION_NAME = "rdbmsSchemaMigrated";

  private final Map<String, RdbmsSchemaVersionStore> versionStoresByPhysicalTenant;

  @VisibleForTesting
  RdbmsSchemaMigrationStatusProvider(
      final Map<String, RdbmsSchemaVersionStore> versionStoresByPhysicalTenant) {
    this.versionStoresByPhysicalTenant = versionStoresByPhysicalTenant;
  }

  /**
   * Builds a provider from the per-physical-tenant {@link PerTenantSchemaConfig} map — the same
   * input {@link RdbmsSchemaManagers#fromConfigs} consumes.
   */
  public static RdbmsSchemaMigrationStatusProvider fromConfigs(
      final Map<String, PerTenantSchemaConfig> physicalTenantConfigs,
      final String applicationVersion) {
    final Map<String, RdbmsSchemaVersionStore> versionStores = new LinkedHashMap<>();
    physicalTenantConfigs.forEach(
        (physicalTenantId, config) ->
            versionStores.put(
                physicalTenantId,
                new RdbmsSchemaVersionStore(
                    config.dataSource(),
                    StringUtils.trimToEmpty(config.prefix()),
                    applicationVersion)));
    return new RdbmsSchemaMigrationStatusProvider(versionStores);
  }

  @Override
  public String conditionName() {
    return CONDITION_NAME;
  }

  @Override
  public Map<String, MigrationConditionStatus> getMigrationStatus() {
    final var statusesByPhysicalTenant = new LinkedHashMap<String, MigrationConditionStatus>();
    versionStoresByPhysicalTenant.forEach(
        (physicalTenantId, versionStore) ->
            statusesByPhysicalTenant.put(
                physicalTenantId, versionStore.getCurrentSchemaVersion().toMigrationStatus()));
    return statusesByPhysicalTenant;
  }
}
