/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.elasticsearch;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.schema.indices.MetadataIndex;
import io.camunda.operate.store.MetadataStore;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchMetadataStore implements MetadataStore {

  private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchMetadataStore.class);

  @Autowired private RetryElasticsearchClient retryElasticsearchClient;
  @Autowired private MetadataIndex metadataIndex;

  @Override
  public String getSchemaVersion() {
    // Check if metadata index exists
    if (!retryElasticsearchClient.indexExists(metadataIndex.getFullQualifiedName())) {
      LOG.debug("Metadata index does not exist");
      return null;
    }
    final var schemaVersionDoc =
        retryElasticsearchClient.getDocument(
            metadataIndex.getFullQualifiedName(), SCHEMA_VERSION_METADATA_ID);
    if (schemaVersionDoc == null) {
      LOG.debug("Schema version metadata document cannot be found");
      return null;
    }
    return schemaVersionDoc.get(MetadataIndex.VALUE).toString();
  }

  @Override
  public void storeSchemaVersion(final String version) {
    retryElasticsearchClient.createOrUpdateDocument(
        metadataIndex.getFullQualifiedName(),
        SCHEMA_VERSION_METADATA_ID,
        Map.of(
            MetadataIndex.ID, SCHEMA_VERSION_METADATA_ID,
            MetadataIndex.VALUE, version));
    LOG.debug("Stored schema version: {}", version);
  }
}
