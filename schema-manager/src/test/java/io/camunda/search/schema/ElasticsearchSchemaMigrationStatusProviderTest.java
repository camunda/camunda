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

/**
 * Unit tests for the Elasticsearch/OpenSearch upgrade-readiness condition. The
 * schema-version-to-{@link MigrationState} mapping itself (patch/minor upgrade, fresh database,
 * incompatible/indeterminate versions, read failures) is shared across every schema-version-backed
 * provider and exhaustively covered once elsewhere -- these tests only cover what's genuinely
 * specific to this provider: multi-tenant map-shaping and one smoke test confirming it's wired to
 * that shared mapping correctly.
 */
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
