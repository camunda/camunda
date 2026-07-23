/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import io.camunda.webapps.schema.descriptors.index.MetadataIndex;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.Map;
import org.slf4j.Logger;

class SchemaMetadataStore {
  // Schema version metadata constants
  @VisibleForTesting static final String SCHEMA_VERSION_METADATA_ID = "schema-version";
  // Cluster ID metadata constants
  @VisibleForTesting static final String CLUSTER_ID_METADATA_ID = "cluster-id";

  private final SearchEngineClient searchEngineClient;
  private final MetadataIndex metadataIndex;
  private final Logger logger;

  SchemaMetadataStore(
      final SearchEngineClient searchEngineClient,
      final MetadataIndex metadataIndex,
      final Logger logger) {
    this.searchEngineClient = searchEngineClient;
    this.metadataIndex = metadataIndex;
    this.logger = logger;
  }

  /**
   * Retrieves the schema version from metadata storage. Returns null if no version is stored
   * (indicating a fresh installation).
   */
  String getSchemaVersion() {
    // Check if metadata index exists
    if (!searchEngineClient.indexExists(metadataIndex.getFullQualifiedName())) {
      logger.info("Metadata index does not exist, assuming a fresh installation");
      return null;
    }
    final var schemaVersionDoc =
        searchEngineClient.getDocument(
            metadataIndex.getFullQualifiedName(), SCHEMA_VERSION_METADATA_ID);

    if (schemaVersionDoc != null) {
      return (String) schemaVersionDoc.get(MetadataIndex.VALUE);
    }

    logger.info("No schema version found in metadata index, assuming a fresh installation");
    return null;
  }

  /**
   * Stores the current application version as the schema version in metadata storage. This should
   * be called after a successful schema upgrade.
   */
  void storeSchemaVersion(final String version) {
    try {
      final var versionDoc =
          Map.<String, Object>of(
              MetadataIndex.ID, SCHEMA_VERSION_METADATA_ID,
              MetadataIndex.VALUE, version);

      searchEngineClient.upsertDocument(
          metadataIndex.getFullQualifiedName(), SCHEMA_VERSION_METADATA_ID, versionDoc);

      logger.info("Stored schema version metadata: {}", version);
    } catch (final Exception e) {
      logger.error("Failed to store schema version in metadata", e);
      throw new IllegalStateException("Could not store schema version metadata", e);
    }
  }

  /**
   * Retrieves the cluster ID from metadata storage. Returns null if no cluster ID is stored
   * (indicating a fresh installation, or an installation predating this check).
   */
  String getClusterId() {
    if (!searchEngineClient.indexExists(metadataIndex.getFullQualifiedName())) {
      logger.info("Metadata index does not exist, assuming a fresh installation");
      return null;
    }
    final var clusterIdDoc =
        searchEngineClient.getDocument(
            metadataIndex.getFullQualifiedName(), CLUSTER_ID_METADATA_ID);

    if (clusterIdDoc != null) {
      return (String) clusterIdDoc.get(MetadataIndex.VALUE);
    }

    logger.info("No cluster ID found in metadata index, assuming a fresh installation");
    return null;
  }

  void storeClusterId(final String clusterId) {
    try {
      final var clusterIdDoc =
          Map.<String, Object>of(
              MetadataIndex.ID, CLUSTER_ID_METADATA_ID,
              MetadataIndex.VALUE, clusterId);

      searchEngineClient.upsertDocument(
          metadataIndex.getFullQualifiedName(), CLUSTER_ID_METADATA_ID, clusterIdDoc);

      logger.info("Stored cluster ID metadata: {}", clusterId);
    } catch (final Exception e) {
      logger.error("Failed to store cluster ID in metadata", e);
      throw new IllegalStateException("Could not store cluster ID metadata", e);
    }
  }
}
