/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import io.camunda.webapps.schema.descriptors.index.SchemaMetadataIndex;
import java.util.Map;
import org.slf4j.Logger;

class SchemaMetadataStore {
  // Schema version metadata constants
  private static final String SCHEMA_VERSION_METADATA_ID = "schema-version";

  private final SearchEngineClient searchEngineClient;
  private final SchemaMetadataIndex schemaMetadataIndex;
  private final Logger logger;

  SchemaMetadataStore(
      final SearchEngineClient searchEngineClient,
      final SchemaMetadataIndex schemaMetadataIndex,
      final Logger logger) {
    this.searchEngineClient = searchEngineClient;
    this.schemaMetadataIndex = schemaMetadataIndex;
    this.logger = logger;
  }

  /**
   * Retrieves the schema version from metadata storage. Returns null if no version is stored
   * (indicating a fresh installation).
   */
  String getSchemaVersion() {
    // Check if metadata index exists
    if (!searchEngineClient.indexExists(schemaMetadataIndex.getFullQualifiedName())) {
      logger.debug("Schema metadata index does not exist, assuming fresh installation");
      return null;
    }
    final var schemaVersionDoc =
        searchEngineClient.getDocument(
            schemaMetadataIndex.getFullQualifiedName(), SCHEMA_VERSION_METADATA_ID);

    if (schemaVersionDoc != null) {
      return (String) schemaVersionDoc.get(SchemaMetadataIndex.VALUE);
    }

    logger.debug("No schema version found in metadata, assuming fresh installation");
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
              SchemaMetadataIndex.ID, SCHEMA_VERSION_METADATA_ID,
              SchemaMetadataIndex.VALUE, version);

      searchEngineClient.upsertDocument(
          schemaMetadataIndex.getFullQualifiedName(), SCHEMA_VERSION_METADATA_ID, versionDoc);

      logger.debug("Stored schema version: {}", version);
    } catch (final Exception e) {
      logger.error("Failed to store schema version in metadata", e);
      throw new IllegalStateException("Could not store schema version metadata", e);
    }
  }
}
