/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.cluster.migration.MigrationConditionStatus;
import io.camunda.cluster.migration.MigrationState;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/** Unit tests for the RDBMS upgrade-readiness condition, one entry per physical tenant. */
class RdbmsSchemaMigrationStatusProviderTest {

  @Test
  void shouldReportConditionName() {
    assertThat(provider(Map.of()).conditionName())
        .isEqualTo(RdbmsSchemaMigrationStatusProvider.CONDITION_NAME);
  }

  @Test
  void shouldReportNoTenantsWhenNoneAreConfigured() {
    // when
    final var statuses = provider(Map.of()).getMigrationStatus();

    // then
    assertThat(statuses).isEmpty();
  }

  @Test
  void shouldReportEachTenantUnderItsOwnPhysicalTenantId() {
    // given
    final var tenantA = versionStore("8.10.0", "8.10.0");
    final var tenantB = versionStore("8.9.0", "8.10.0");

    // when
    final var statuses =
        provider(orderedMap("tenantA", tenantA, "tenantB", tenantB)).getMigrationStatus();

    // then - no cross-tenant aggregation, each tenant reports independently
    assertThat(statuses).hasSize(2);
    assertThat(statuses.get("tenantA").state()).isEqualTo(MigrationState.MIGRATED);
    assertThat(statuses.get("tenantB").state()).isEqualTo(MigrationState.MIGRATION_IN_PROGRESS);
  }

  @Test
  void shouldReportMigratedForSameVersion() {
    // given - schema=8.10.0, app=8.10.0
    final var status = singleStatus(versionStore("8.10.0", "8.10.0"));

    // then
    assertThat(status.state()).isEqualTo(MigrationState.MIGRATED);
    assertThat(status.detail()).contains("8.10.0");
  }

  @Test
  void shouldReportMigrationInProgressForPatchUpgrade() {
    // given - schema=8.9.0, app=8.9.5 (not yet migrated to the running app's exact version)
    final var status = singleStatus(versionStore("8.9.0", "8.9.5"));

    // then
    assertThat(status.state()).isEqualTo(MigrationState.MIGRATION_IN_PROGRESS);
  }

  @Test
  void shouldReportMigrationInProgressForMinorUpgrade() {
    // given - schema=8.9.1, app=8.10.0
    final var status = singleStatus(versionStore("8.9.1", "8.10.0"));

    // then
    assertThat(status.state()).isEqualTo(MigrationState.MIGRATION_IN_PROGRESS);
  }

  @Test
  void shouldReportMigrationInProgressForFreshDatabase() {
    // given - resolveCurrentSchemaVersion returns null (fresh DB, not yet initialized)
    final var status = singleStatus(versionStore(null, "8.11.0"));

    // then
    assertThat(status.state()).isEqualTo(MigrationState.MIGRATION_IN_PROGRESS);
    assertThat(status.detail()).contains("fresh database");
  }

  @Test
  void shouldReportUnknownForIncompatibleUpgradePath() {
    // given - schema=8.9.0, app=8.11.0 (skipped 8.10) - a real problem, not "in progress"
    final var status = singleStatus(versionStore("8.9.0", "8.11.0"));

    // then
    assertThat(status.state()).isEqualTo(MigrationState.UNKNOWN);
  }

  @Test
  void shouldReportUnknownForIndeterminateSchemaVersion() {
    // given - stored schema version is not a valid semantic version
    final var status = singleStatus(versionStore("not-a-semver", "8.10.0"));

    // then
    assertThat(status.state()).isEqualTo(MigrationState.UNKNOWN);
  }

  @Test
  void shouldReportUnknownForUnparseableApplicationVersion() {
    // given - app=development (not a semantic version)
    final var status = singleStatus(versionStore("8.9.0", "development"));

    // then
    assertThat(status.state()).isEqualTo(MigrationState.UNKNOWN);
    assertThat(status.detail()).contains("development");
  }

  @Test
  void shouldReportUnknownWhenReadingCurrentVersionFails() throws Exception {
    // given - getConnection() throws
    final var dataSource = mock(DataSource.class);
    when(dataSource.getConnection()).thenThrow(new RuntimeException("DB connection refused"));
    final var store = new RdbmsSchemaVersionStore(dataSource, "", "8.10.0");

    // when
    final var status = singleStatus(store);

    // then - a read failure must never throw; it must be reported as UNKNOWN
    assertThat(status.state()).isEqualTo(MigrationState.UNKNOWN);
    assertThat(status.detail()).contains("DB connection refused");
  }

  private static RdbmsSchemaMigrationStatusProvider provider(
      final Map<String, RdbmsSchemaVersionStore> versionStoresByPhysicalTenant) {
    return new RdbmsSchemaMigrationStatusProvider(versionStoresByPhysicalTenant);
  }

  private static MigrationConditionStatus singleStatus(final RdbmsSchemaVersionStore versionStore) {
    return provider(Map.of("tenant", versionStore)).getMigrationStatus().get("tenant");
  }

  /**
   * Builds a {@link RdbmsSchemaVersionStore} whose {@link
   * RdbmsSchemaVersionStore#resolveCurrentSchemaVersion} returns {@code schemaVersion}, backed by a
   * mock data source that yields a mock connection.
   */
  private static RdbmsSchemaVersionStore versionStore(
      final String schemaVersion, final String appVersion) {
    final var dataSource = mock(DataSource.class);
    try {
      when(dataSource.getConnection()).thenReturn(mock(Connection.class));
    } catch (final SQLException e) {
      throw new RuntimeException(e);
    }
    return new RdbmsSchemaVersionStore(dataSource, "", appVersion) {
      @Override
      protected String resolveCurrentSchemaVersion(
          final Connection connection, final String prefix) {
        return schemaVersion;
      }
    };
  }

  private static Map<String, RdbmsSchemaVersionStore> orderedMap(
      final String keyA,
      final RdbmsSchemaVersionStore valueA,
      final String keyB,
      final RdbmsSchemaVersionStore valueB) {
    final var map = new LinkedHashMap<String, RdbmsSchemaVersionStore>();
    map.put(keyA, valueA);
    map.put(keyB, valueB);
    return map;
  }
}
