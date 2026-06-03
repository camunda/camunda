/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.GetIndexRequest;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class ElasticsearchSchemaCleanupIT {

  private static final Set<String> LEGACY_INDEXES =
      Set.of("tasklist-process-8.4.0_", "operate-user-1.2.0_");
  private static final Set<String> UNAFFECTED_INDEXES =
      Set.of("operate-list-view-8.3.0_", "operate-variable-8.3.0_");

  @Container
  private static final ElasticsearchContainer ES_CONTAINER =
      TestSearchContainers.createDefaultElasticsearchContainer();

  private ConnectConfiguration connectConfig;
  private ElasticsearchClient esClient;
  private SearchEngineClient esEngineClient;

  @BeforeEach
  void setup() throws IOException {
    connectConfig = new ConnectConfiguration();
    connectConfig.setUrl(ES_CONTAINER.getHttpHostAddress());
    final var esConnector = new ElasticsearchConnector(connectConfig);
    esClient = esConnector.createClient();
    esEngineClient = new ElasticsearchEngineClient(esClient, esConnector.objectMapper());

    clearDatabase();
  }

  @AfterEach
  public void teardown() {
    CloseHelper.quietCloseAll(esClient, esEngineClient);
  }

  @Test
  void shouldCleanupLegacyIndexes() throws IOException {
    /* Creation of some legacy indexes and some unaffected indexes */

    for (final String legacyIndex : LEGACY_INDEXES) {
      createIndex(legacyIndex);
    }
    for (final String unaffectedIndex : UNAFFECTED_INDEXES) {
      createIndex(unaffectedIndex);
    }

    /* Trigger the cleanup */

    final SchemaCleanup schemaCleanup = new SchemaCleanup(true, esEngineClient);
    schemaCleanup.performCleanup();

    /* Wait and verify that the legacy indexes are gone */

    await()
        .atMost(Duration.ofMinutes(1))
        .pollInterval(Duration.ofSeconds(1))
        .untilAsserted(
            () -> {
              final Set<String> indexes = fetchAllIndexes();
              assertThat(indexes).containsAll(UNAFFECTED_INDEXES);
              assertThat(indexes).doesNotContainAnyElementsOf(LEGACY_INDEXES);
            });
  }

  private void createIndex(final String indexName) throws IOException {
    final CreateIndexRequest request = new CreateIndexRequest.Builder().index(indexName).build();
    esClient.indices().create(request);
  }

  private Set<String> fetchAllIndexes() throws IOException {
    final GetIndexRequest request = new GetIndexRequest.Builder().index("*").build();
    final GetIndexResponse response = esClient.indices().get(request);
    return response.result().keySet();
  }

  private void clearDatabase() throws IOException {
    esClient.indices().delete(req -> req.index("*"));
    esClient.indices().deleteIndexTemplate(req -> req.name("*"));
  }
}
