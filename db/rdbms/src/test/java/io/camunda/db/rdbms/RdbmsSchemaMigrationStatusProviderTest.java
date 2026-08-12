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

import io.camunda.cluster.MigrationConditionStatus;
import io.camunda.cluster.MigrationState;
import java.util.LinkedHashMap;
import java.util.Map;
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
    final var tenantA = mock(RdbmsSchemaVersionStore.class);
    when(tenantA.getMigrationStatus())
        .thenReturn(new MigrationConditionStatus(MigrationState.MIGRATED, "a done"));
    final var tenantB = mock(RdbmsSchemaVersionStore.class);
    when(tenantB.getMigrationStatus())
        .thenReturn(new MigrationConditionStatus(MigrationState.MIGRATION_IN_PROGRESS, "b behind"));

    // when
    final var statuses =
        provider(orderedMap("tenantA", tenantA, "tenantB", tenantB)).getMigrationStatus();

    // then - no cross-tenant aggregation, each tenant reports independently
    assertThat(statuses).hasSize(2);
    assertThat(statuses.get("tenantA").state()).isEqualTo(MigrationState.MIGRATED);
    assertThat(statuses.get("tenantB").state()).isEqualTo(MigrationState.MIGRATION_IN_PROGRESS);
  }

  private static RdbmsSchemaMigrationStatusProvider provider(
      final Map<String, RdbmsSchemaVersionStore> versionStoresByPhysicalTenant) {
    return new RdbmsSchemaMigrationStatusProvider(versionStoresByPhysicalTenant);
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
