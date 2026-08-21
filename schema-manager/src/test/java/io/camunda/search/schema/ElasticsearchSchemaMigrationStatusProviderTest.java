/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.cluster.migration.MigrationConditionStatus;
import io.camunda.cluster.migration.MigrationState;
import io.camunda.webapps.schema.descriptors.index.MetadataIndex;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for the Elasticsearch/OpenSearch upgrade-readiness condition. */
class ElasticsearchSchemaMigrationStatusProviderTest {

  @Test
  void shouldReportConditionName() {
    assertThat(provider(Map.of()).conditionName())
        .isEqualTo(ElasticsearchSchemaMigrationStatusProvider.CONDITION_NAME);
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
    // given - metadata index does not exist yet (fresh installation)
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
  void shouldReportUnknownWhenReadingCurrentVersionFails() {
    // given - the search engine call itself fails (e.g. connection refused)
    final var searchEngineClient = mock(SearchEngineClient.class);
    when(searchEngineClient.indexExists(anyString()))
        .thenThrow(new RuntimeException("connection refused"));
    final var store = new ElasticsearchSchemaVersionStore(searchEngineClient, "", true, "8.10.0");

    // when
    final var status = singleStatus(store);

    // then - a read failure must never throw; it must be reported as UNKNOWN
    assertThat(status.state()).isEqualTo(MigrationState.UNKNOWN);
    assertThat(status.detail()).contains("connection refused");
  }

  private static ElasticsearchSchemaMigrationStatusProvider provider(
      final Map<String, ElasticsearchSchemaVersionStore> versionStoresByPhysicalTenant) {
    return new ElasticsearchSchemaMigrationStatusProvider(versionStoresByPhysicalTenant);
  }

  private static MigrationConditionStatus singleStatus(
      final ElasticsearchSchemaVersionStore versionStore) {
    return provider(Map.of("tenant", versionStore)).getMigrationStatus().get("tenant");
  }

  /**
   * Builds an {@link ElasticsearchSchemaVersionStore} whose metadata index reports {@code
   * schemaVersion} as already stored (or a fresh installation if {@code null}), backed by a mocked
   * {@link SearchEngineClient}.
   */
  private static ElasticsearchSchemaVersionStore versionStore(
      final String schemaVersion, final String appVersion) {
    final var searchEngineClient = mock(SearchEngineClient.class);
    if (schemaVersion == null) {
      when(searchEngineClient.indexExists(anyString())).thenReturn(false);
    } else {
      when(searchEngineClient.indexExists(anyString())).thenReturn(true);
      when(searchEngineClient.getDocument(anyString(), anyString()))
          .thenReturn(Map.of(MetadataIndex.VALUE, schemaVersion));
    }
    return new ElasticsearchSchemaVersionStore(searchEngineClient, "", true, appVersion);
  }

  private static Map<String, ElasticsearchSchemaVersionStore> orderedMap(
      final String keyA,
      final ElasticsearchSchemaVersionStore valueA,
      final String keyB,
      final ElasticsearchSchemaVersionStore valueB) {
    final var map = new LinkedHashMap<String, ElasticsearchSchemaVersionStore>();
    map.put(keyA, valueA);
    map.put(keyB, valueB);
    return map;
  }
}
