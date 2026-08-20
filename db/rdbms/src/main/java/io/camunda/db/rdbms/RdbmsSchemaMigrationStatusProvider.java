/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import io.camunda.cluster.migration.MigrationConditionStatus;
import io.camunda.cluster.migration.MigrationState;
import io.camunda.cluster.migration.MigrationStatusProvider;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.migration.VersionCompatibilityCheck;
import io.camunda.zeebe.util.migration.VersionCompatibilityCheck.CheckResult.Compatible;
import io.camunda.zeebe.util.migration.VersionCompatibilityCheck.CheckResult.Incompatible;
import io.camunda.zeebe.util.migration.VersionCompatibilityCheck.CheckResult.Indeterminate;
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
            statusesByPhysicalTenant.put(
                physicalTenantId, toMigrationStatus(versionStore.getCurrentSchemaVersion())));
    return statusesByPhysicalTenant;
  }

  /**
   * Maps raw schema-version facts to the upgrade-readiness condition reported for one physical
   * tenant.
   *
   * <ul>
   *   <li>Schema version equals the application version ({@link Compatible.SameVersion}) → {@link
   *       MigrationState#MIGRATED}.
   *   <li>Schema version is one or more minors behind, on a supported upgrade path ({@link
   *       Compatible.PatchUpgrade}/{@link Compatible.MinorUpgrade}), or no version has been
   *       recorded yet (fresh database) → {@link MigrationState#MIGRATION_IN_PROGRESS}. This
   *       includes externally-managed schemas ({@code auto-ddl=false}) that have not yet been
   *       migrated by the operator's own tooling.
   *   <li>An illegal upgrade path ({@link Incompatible}), an unparseable version ({@link
   *       Indeterminate}, or an unparseable application version, or any read failure (e.g. a
   *       connection error) → {@link MigrationState#UNKNOWN} — this is a "we don't know," not a "we
   *       know it's not done."
   * </ul>
   */
  @VisibleForTesting
  MigrationConditionStatus toMigrationStatus(
      final RdbmsSchemaVersionStore.CurrentSchemaVersion currentSchemaVersion) {
    return switch (currentSchemaVersion.kind()) {
      case AVAILABLE ->
          toMigrationStatus(
              VersionCompatibilityCheck.check(
                  currentSchemaVersion.schemaVersion().orElseThrow(),
                  currentSchemaVersion.stableApplicationVersion().orElseThrow()));
      case FRESH_DATABASE ->
          new MigrationConditionStatus(
              MigrationState.MIGRATION_IN_PROGRESS,
              "no schema version recorded yet for prefix '"
                  + currentSchemaVersion.prefix()
                  + "' (fresh database)");
      case READ_FAILURE ->
          new MigrationConditionStatus(
              MigrationState.UNKNOWN, currentSchemaVersion.detail().orElseThrow());
    };
  }

  private MigrationConditionStatus toMigrationStatus(
      final VersionCompatibilityCheck.CheckResult result) {
    return switch (result) {
      case final Compatible.SameVersion same ->
          new MigrationConditionStatus(
              MigrationState.MIGRATED,
              "schema version " + same.version() + " matches the application version");
      case final Compatible.PatchUpgrade patch ->
          new MigrationConditionStatus(
              MigrationState.MIGRATION_IN_PROGRESS,
              "schema version " + patch.from() + " has not yet migrated to " + patch.to());
      case final Compatible.MinorUpgrade minor ->
          new MigrationConditionStatus(
              MigrationState.MIGRATION_IN_PROGRESS,
              "schema version " + minor.from() + " has not yet migrated to " + minor.to());
      case final Incompatible incompatible ->
          new MigrationConditionStatus(
              MigrationState.UNKNOWN, "incompatible schema upgrade path: " + incompatible);
      case final Indeterminate indeterminate ->
          new MigrationConditionStatus(
              MigrationState.UNKNOWN,
              "cannot determine schema version compatibility: " + indeterminate);
    };
  }
}
