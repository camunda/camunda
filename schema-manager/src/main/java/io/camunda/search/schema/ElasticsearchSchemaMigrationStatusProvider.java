/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import io.camunda.cluster.migration.MigrationConditionStatus;
import io.camunda.cluster.migration.MigrationStatusProvider;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.search.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.search.schema.opensearch.OpensearchEngineClient;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reports whether the Elasticsearch/OpenSearch schema of every configured physical tenant has
 * migrated to the running application version, for the upgrade-readiness endpoint. Covers both
 * search engines: the schema (webapp-style indices, tracked via {@link SchemaMetadataStore}'s
 * {@code schema-version} document) is identical between them, only the wire client differs.
 *
 * <p>Reports one entry per physical tenant.
 */
public final class ElasticsearchSchemaMigrationStatusProvider
    implements MigrationStatusProvider, AutoCloseable {

  public static final String CONDITION_NAME = "elasticsearchSchemaMigrated";

  private static final Logger LOG =
      LoggerFactory.getLogger(ElasticsearchSchemaMigrationStatusProvider.class);

  private final Map<String, ElasticsearchSchemaVersionStore> versionStoresByPhysicalTenant;
  private final Map<String, SearchEngineClient> searchEngineClientsByPhysicalTenant;

  @VisibleForTesting
  ElasticsearchSchemaMigrationStatusProvider(
      final Map<String, ElasticsearchSchemaVersionStore> versionStoresByPhysicalTenant) {
    this(versionStoresByPhysicalTenant, Map.of());
  }

  private ElasticsearchSchemaMigrationStatusProvider(
      final Map<String, ElasticsearchSchemaVersionStore> versionStoresByPhysicalTenant,
      final Map<String, SearchEngineClient> searchEngineClientsByPhysicalTenant) {
    this.versionStoresByPhysicalTenant = versionStoresByPhysicalTenant;
    this.searchEngineClientsByPhysicalTenant = searchEngineClientsByPhysicalTenant;
  }

  /**
   * Builds a provider from the per-physical-tenant {@link SearchEngineConfiguration} map — the same
   * input the search-engine schema initializer consumes. Opens one long-lived {@link
   * SearchEngineClient} per physical tenant and holds it for the life of this provider, unlike a
   * one-shot startup check: the upgrade-readiness endpoint calls {@link #getMigrationStatus()} on
   * every poll, so a fresh client per call would pay a full connection-setup cost every time.
   * {@link #close()} must be called on application shutdown to release them.
   */
  public static ElasticsearchSchemaMigrationStatusProvider fromConfigs(
      final Map<String, SearchEngineConfiguration> configsByPhysicalTenant,
      final String applicationVersion) {
    final Map<String, ElasticsearchSchemaVersionStore> versionStores = new LinkedHashMap<>();
    final Map<String, SearchEngineClient> searchEngineClients = new LinkedHashMap<>();
    configsByPhysicalTenant.forEach(
        (physicalTenantId, config) -> {
          final var searchEngineClient = createSearchEngineClient(config);
          searchEngineClients.put(physicalTenantId, searchEngineClient);
          versionStores.put(
              physicalTenantId,
              new ElasticsearchSchemaVersionStore(
                  searchEngineClient,
                  config.connect().getIndexPrefix(),
                  config.connect().getTypeEnum().isElasticSearch(),
                  applicationVersion));
        });
    return new ElasticsearchSchemaMigrationStatusProvider(versionStores, searchEngineClients);
  }

  private static SearchEngineClient createSearchEngineClient(
      final SearchEngineConfiguration config) {
    final var connect = config.connect();
    if (connect.getTypeEnum().isElasticSearch()) {
      final var connector = new ElasticsearchConnector(connect);
      return new ElasticsearchEngineClient(connector.createClient(), connector.objectMapper());
    }
    final var connector = new OpensearchConnector(connect);
    return new OpensearchEngineClient(connector.createClient(), connector.objectMapper());
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

  @Override
  public void close() {
    searchEngineClientsByPhysicalTenant.forEach(
        (physicalTenantId, searchEngineClient) -> {
          try {
            searchEngineClient.close();
          } catch (final Exception e) {
            LOG.warn(
                "Failed to close the search engine client for physical tenant '{}'.",
                physicalTenantId,
                e);
          }
        });
  }
}
