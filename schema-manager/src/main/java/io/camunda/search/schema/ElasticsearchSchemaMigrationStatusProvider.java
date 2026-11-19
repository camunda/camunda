/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import static java.util.Objects.requireNonNull;

import io.camunda.cluster.migration.MigrationConditionStatus;
import io.camunda.cluster.migration.MigrationStatusProvider;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reports whether the Elasticsearch/OpenSearch schema of every configured physical tenant has
 * migrated to the running application version, for the upgrade-readiness endpoint. Covers both
 * search engines: the schema (webapp-style indices, tracked via {@link SchemaMetadataStore}'s
 * {@code schema-version} document) is identical between them, only the wire client differs.
 *
 * <p>Reports one entry per physical tenant.
 */
public final class ElasticsearchSchemaMigrationStatusProvider implements MigrationStatusProvider {

  public static final String CONDITION_NAME = "elasticsearchSchemaMigrated";

  private final Map<String, ElasticsearchSchemaVersionStore> versionStoresByPhysicalTenant;

  @VisibleForTesting
  ElasticsearchSchemaMigrationStatusProvider(
      final Map<String, ElasticsearchSchemaVersionStore> versionStoresByPhysicalTenant) {
    this.versionStoresByPhysicalTenant = versionStoresByPhysicalTenant;
  }

  /**
   * Builds a provider from the per-physical-tenant {@link SearchEngineConfiguration} map — the same
   * input the search-engine schema initializer consumes — and the clients to read with. The clients
   * are shared with the rest of the application and are owned elsewhere: the provider never closes
   * them. The upgrade-readiness endpoint calls {@link #getMigrationStatus()} on every poll, which
   * is why the clients are long-lived instead of opened per call.
   */
  public static ElasticsearchSchemaMigrationStatusProvider fromClients(
      final Map<String, SearchEngineConfiguration> configsByPhysicalTenant,
      final Map<String, SearchEngineClient> searchEngineClientsByPhysicalTenant,
      final String applicationVersion) {
    final Map<String, ElasticsearchSchemaVersionStore> versionStores = new LinkedHashMap<>();
    configsByPhysicalTenant.forEach(
        (physicalTenantId, config) ->
            versionStores.put(
                physicalTenantId,
                new ElasticsearchSchemaVersionStore(
                    requireNonNull(
                        searchEngineClientsByPhysicalTenant.get(physicalTenantId),
                        "No search engine client for physical tenant '" + physicalTenantId + "'"),
                    config.connect().getIndexPrefix(),
                    config.connect().getTypeEnum().isElasticSearch(),
                    applicationVersion)));
    return new ElasticsearchSchemaMigrationStatusProvider(versionStores);
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
