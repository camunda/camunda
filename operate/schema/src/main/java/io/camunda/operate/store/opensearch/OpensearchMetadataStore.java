/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.opensearch;

import static io.camunda.operate.store.opensearch.dsl.RequestDSL.indexRequestBuilder;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.schema.indices.MetadataIndex;
import io.camunda.operate.store.MetadataStore;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchMetadataStore implements MetadataStore {

  private static final Logger LOG = LoggerFactory.getLogger(OpensearchMetadataStore.class);

  @Autowired private RichOpenSearchClient richOpenSearchClient;
  @Autowired private MetadataIndex metadataIndex;

  @Override
  public String getSchemaVersion() {
    // Check if metadata index exists
    final String metaDataIndexName = metadataIndex.getFullQualifiedName();
    if (!richOpenSearchClient.index().indexExists(metaDataIndexName)) {
      LOG.debug("Metadata index does not exist");
      return null;
    }
    if (!richOpenSearchClient
        .doc()
        .documentExistsWithRetries(metaDataIndexName, SCHEMA_VERSION_METADATA_ID)) {
      LOG.debug("Schema version metadata document cannot be found");
      return null;
    }
    final var schemaVersionDoc =
        richOpenSearchClient
            .doc()
            .getWithRetries(metaDataIndexName, SCHEMA_VERSION_METADATA_ID, Map.class);
    if (schemaVersionDoc.isEmpty()) {
      LOG.debug("Schema version metadata document cannot be found");
      return null;
    }
    return schemaVersionDoc.get().get(MetadataIndex.VALUE).toString();
  }

  @Override
  public void storeSchemaVersion(final String version) {
    richOpenSearchClient
        .doc()
        .indexWithRetries(
            indexRequestBuilder(metadataIndex.getFullQualifiedName())
                .id(SCHEMA_VERSION_METADATA_ID)
                .document(
                    Map.of(
                        MetadataIndex.ID, SCHEMA_VERSION_METADATA_ID,
                        MetadataIndex.VALUE, version)));
    LOG.debug("Stored schema version: {}", version);
  }
}
