/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.document;

import io.camunda.analytics.serving.spi.DatasetStore;
import io.camunda.analytics.serving.spi.MetadataStore;
import io.camunda.analytics.serving.spi.ServingWriteMetrics;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.DatabaseConfig;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.es.clients.ElasticsearchSearchClient;
import io.camunda.search.os.clients.OpensearchSearchClient;

/**
 * Builds the document-backed stores for Elasticsearch or OpenSearch from a {@link
 * ConnectConfiguration}, choosing the backend by {@link ConnectConfiguration#getType()}. This is
 * the only place a concrete backend is named: it constructs OC's search client, which implements
 * all three neutral seams (read + write + schema); everything above is backend-neutral.
 */
public final class DocumentStores {

  private DocumentStores() {}

  /** The control-plane metadata store for the configured document backend. */
  public static MetadataStore metadataStore(final ConnectConfiguration configuration) {
    if (isOpensearch(configuration)) {
      final OpensearchSearchClient client = opensearchClient(configuration);
      return new DocumentMetadataStore(client, client, client);
    }
    final ElasticsearchSearchClient client = elasticsearchClient(configuration);
    return new DocumentMetadataStore(client, client, client);
  }

  /** The serving store (schema/writer/query) for the configured document backend. */
  public static DatasetStore datasetStore(final ConnectConfiguration configuration) {
    return datasetStore(configuration, ServingWriteMetrics.NOOP);
  }

  /**
   * The serving store whose writer reports its write-path health through {@code metrics} (rows
   * written, fence rejections, flush duration, bulk batch size).
   */
  public static DatasetStore datasetStore(
      final ConnectConfiguration configuration, final ServingWriteMetrics metrics) {
    if (isOpensearch(configuration)) {
      final OpensearchSearchClient client = opensearchClient(configuration);
      return new DocumentDatasetStore(client, client, client, metrics);
    }
    final ElasticsearchSearchClient client = elasticsearchClient(configuration);
    return new DocumentDatasetStore(client, client, client, metrics);
  }

  private static boolean isOpensearch(final ConnectConfiguration configuration) {
    return DatabaseConfig.OPENSEARCH.equals(configuration.getType());
  }

  private static ElasticsearchSearchClient elasticsearchClient(
      final ConnectConfiguration configuration) {
    return new ElasticsearchSearchClient(new ElasticsearchConnector(configuration).createClient());
  }

  private static OpensearchSearchClient opensearchClient(final ConnectConfiguration configuration) {
    return new OpensearchSearchClient(new OpensearchConnector(configuration).createClient());
  }
}
