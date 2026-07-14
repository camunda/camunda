/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.document;

import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.DatabaseConfig;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.os.clients.OpensearchSearchClient;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.testcontainers.OpenSearchContainer;

/** {@link AbstractDocumentDatasetStoreIT} against a containerized OpenSearch. */
final class OpensearchDatasetStoreIT extends AbstractDocumentDatasetStoreIT {

  private static OpenSearchContainer<?> container;
  private static OpenSearchClient rawClient;
  private static DocumentDatasetStore store;

  @BeforeAll
  static void startOpensearch() {
    container = TestSearchContainers.createDefaultOpensearchContainer();
    container.start();
    final ConnectConfiguration config = new ConnectConfiguration();
    config.setType(DatabaseConfig.OPENSEARCH);
    config.setUrl(container.getHttpHostAddress());
    rawClient = new OpensearchConnector(config).createClient();
    final OpensearchSearchClient client = new OpensearchSearchClient(rawClient);
    store = new DocumentDatasetStore(client, client, client);
  }

  @AfterAll
  static void stopOpensearch() {
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
