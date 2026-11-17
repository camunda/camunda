/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.adapters;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.cache.ExporterEntityCacheProvider;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.search.schema.SearchEngineClient;
import java.io.IOException;
import org.opensearch.client.opensearch.OpenSearchClient;

public interface ClientAdapter extends AutoCloseable {

  static ClientAdapter of(final ConnectConfiguration configuration) {
    final var databaseType = configuration.getTypeEnum();
    return switch (databaseType) {
      case DatabaseType.ELASTICSEARCH -> new ElasticsearchAdapter(configuration);
      case DatabaseType.OPENSEARCH -> new OpensearchAdapter(configuration);
      default -> throw new IllegalArgumentException("Unsupported databaseType: " + databaseType);
    };
  }

  /**
   * Wraps an already-created client, e.g. a shared Spring bean. The adapter does not own the client
   * and does not close it.
   */
  static ClientAdapter of(final ElasticsearchClient client) {
    return new ElasticsearchAdapter(client);
  }

  /**
   * Wraps an already-created client, e.g. a shared Spring bean. The adapter does not own the client
   * and does not close it.
   */
  static ClientAdapter of(final OpenSearchClient client) {
    return new OpensearchAdapter(client);
  }

  ObjectMapper objectMapper();

  SearchEngineClient getSearchEngineClient();

  BatchRequest createBatchRequest();

  ExporterEntityCacheProvider getExporterEntityCacheProvider();

  @Override
  void close() throws IOException;
}
