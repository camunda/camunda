/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import io.camunda.cluster.MigrationConditionStatus;
import io.camunda.cluster.MigrationStatusProvider;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * Reports whether the RDBMS schema of every configured physical tenant has migrated to the running
 * application version, for the upgrade-readiness endpoint.
 *
 * <p>Builds its own {@link RdbmsSchemaVersionStore} per tenant directly from {@link
 * PerTenantSchemaConfig}, independent of {@link RdbmsSchemaManagerRegistry}/{@link
 * LiquibaseSchemaManager}: reading the {@code RDBMS_SCHEMA_VERSION} table works the same whether
 * this application's own Liquibase run wrote it or an operator's external tooling did ({@code
 * auto-ddl=false}), which is exactly the case {@link RdbmsSchemaManagerRegistry#isInitialized}
 * cannot answer on its own.
 *
 * <p>Reports one entry per physical tenant — no cross-tenant aggregation happens here. Each tenant
 * is an independent RDBMS schema; combining them into a single answer is the upgrade-readiness
 * aggregator's job, not this provider's.
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
   * input {@link DefaultRdbmsSchemaManagerRegistry#fromConfigs} consumes.
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
            statusesByPhysicalTenant.put(physicalTenantId, versionStore.getMigrationStatus()));
    return statusesByPhysicalTenant;
  }
}
