/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.document;

import io.camunda.analytics.meter.MeterIdStore;
import io.camunda.analytics.serving.spi.DatasetSpecStore;
import io.camunda.analytics.serving.spi.MetadataStore;
import io.camunda.search.clients.DocumentBasedSchemaClient;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.DocumentBasedWriteClient;

/**
 * The document-backed {@link MetadataStore}: one implementation composed from the three neutral
 * document seams — OC's {@link DocumentBasedSearchClient} (read) + {@link DocumentBasedWriteClient}
 * (write) and {@link DocumentBasedSchemaClient} (index creation) — so it serves both Elasticsearch
 * and OpenSearch with no per-backend logic here. {@link #migrate()} creates the two fixed control-
 * plane indices from their hand-rolled JSON mappings; the spec and meter-id stores read/write them.
 */
public final class DocumentMetadataStore implements MetadataStore {

  static final String SPEC_INDEX = "analytics-dataset-spec";
  static final String METER_ID_INDEX = "analytics-meter-id";

  private final DocumentBasedSearchClient searchClient;
  private final DocumentBasedSchemaClient schemaClient;
  private final DatasetSpecStore datasetSpecStore;
  private final MeterIdStore meterIdStore;

  public DocumentMetadataStore(
      final DocumentBasedSearchClient searchClient,
      final DocumentBasedWriteClient writeClient,
      final DocumentBasedSchemaClient schemaClient) {
    this.searchClient = searchClient;
    this.schemaClient = schemaClient;
    this.datasetSpecStore = new DocumentDatasetSpecStore(searchClient, writeClient, SPEC_INDEX);
    this.meterIdStore = new DocumentMeterIdStore(searchClient, writeClient, METER_ID_INDEX);
  }

  @Override
  public void migrate() {
    schemaClient.createIndex(SPEC_INDEX, DocumentMappings.load(DocumentMappings.DATASET_SPEC));
    schemaClient.createIndex(METER_ID_INDEX, DocumentMappings.load(DocumentMappings.METER_ID));
  }

  @Override
  public MeterIdStore meterIdStore() {
    return meterIdStore;
  }

  @Override
  public DatasetSpecStore datasetSpecStore() {
    return datasetSpecStore;
  }

  @Override
  public void close() {
    searchClient.close();
  }
}
