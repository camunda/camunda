/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.document;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.es.clients.ElasticsearchSearchClient;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

/** {@link AbstractDocumentDatasetStoreIT} against a containerized Elasticsearch. */
final class ElasticsearchDatasetStoreIT extends AbstractDocumentDatasetStoreIT {

  private static ElasticsearchContainer container;
  private static ElasticsearchClient rawClient;
  private static DocumentDatasetStore store;

  @BeforeAll
  static void startElasticsearch() {
    container = TestSearchContainers.createDefaultElasticsearchContainer();
    container.start();
    final ConnectConfiguration config = new ConnectConfiguration();
    config.setUrl(container.getHttpHostAddress());
    rawClient = new ElasticsearchConnector(config).createClient();
    final ElasticsearchSearchClient client = new ElasticsearchSearchClient(rawClient);
    store = new DocumentDatasetStore(client, client, client);
  }

  @AfterAll
  static void stopElasticsearch() {
    if (store != null) {
      store.close();
    }
    if (container != null) {
      container.stop();
    }
  }

  @Override
  DocumentDatasetStore store() {
    return store;
  }

  @Override
  void refresh() {
    try {
      rawClient.indices().refresh(r -> r);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
