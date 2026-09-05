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

/**
 * Unit tests for the RDBMS upgrade-readiness condition, one entry per physical tenant. The
 * schema-version-to-{@link MigrationState} mapping itself (patch/minor upgrade, fresh database,
 * incompatible/indeterminate versions, read failures) is shared across every schema-version-backed
 * provider and exhaustively covered once elsewhere -- these tests only cover what's genuinely
 * specific to this provider: multi-tenant map-shaping and one smoke test confirming it's wired to
 * that shared mapping correctly.
 */
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
