/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.document;

import io.camunda.analytics.serving.spi.DatasetQueryClient;
import io.camunda.analytics.serving.spi.DatasetSchemaManager;
import io.camunda.analytics.serving.spi.DatasetStore;
import io.camunda.analytics.serving.spi.ServingWriteMetrics;
import io.camunda.analytics.serving.spi.VersionedDatasetWriter;
import io.camunda.search.clients.DocumentBasedSchemaClient;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.DocumentBasedWriteClient;

/**
 * The document-backed serving {@link DatasetStore}: one implementation composed from the three
 * neutral seams (OC's search + write clients and the {@link DocumentBasedSchemaClient}), so it
 * serves both Elasticsearch and OpenSearch. The schema manager provisions the cube/table indices,
 * the writer indexes one fenced document per cell (or snapshot row, or projected row — ADR 0009),
 * and the query client maps documents back to cells for the neutral executor.
 */
public final class DocumentDatasetStore implements DatasetStore {

  private final DocumentBasedSearchClient searchClient;
  private final DocumentBasedWriteClient writeClient;
  private final DocumentBasedSchemaClient schemaClient;
  private final ServingWriteMetrics metrics;

  public DocumentDatasetStore(
      final DocumentBasedSearchClient searchClient,
      final DocumentBasedWriteClient writeClient,
      final DocumentBasedSchemaClient schemaClient) {
    this(searchClient, writeClient, schemaClient, ServingWriteMetrics.NOOP);
  }

  public DocumentDatasetStore(
      final DocumentBasedSearchClient searchClient,
      final DocumentBasedWriteClient writeClient,
      final DocumentBasedSchemaClient schemaClient,
      final ServingWriteMetrics metrics) {
    this.searchClient = searchClient;
    this.writeClient = writeClient;
    this.schemaClient = schemaClient;
    this.metrics = metrics;
  }

  @Override
  public DatasetSchemaManager schemaManager() {
    return new DocumentDatasetSchemaManager(schemaClient);
  }

  @Override
  public VersionedDatasetWriter writer() {
    return new DocumentDatasetWriter(writeClient, metrics);
  }

  @Override
  public DatasetQueryClient queryClient() {
    return new DocumentDatasetQueryClient(searchClient);
  }

  @Override
  public void close() {
    searchClient.close();
  }
}
