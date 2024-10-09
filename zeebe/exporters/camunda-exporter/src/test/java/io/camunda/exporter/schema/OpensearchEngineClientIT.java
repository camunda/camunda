/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.config.ExporterConfiguration.IndexSettings;
import io.camunda.exporter.utils.TestSupport;
import io.camunda.search.connect.os.OpensearchConnector;
import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class OpensearchEngineClientIT {
  @Container
  private static final OpensearchContainer<?> CONTAINER =
      TestSupport.createDefaultOpensearchContainer();

  private static OpenSearchClient openSearchClient;
  private static OpensearchEngineClient opensearchEngineClient;

  @BeforeAll
  public static void init() {
    final var config = new ExporterConfiguration();
    config.getConnect().setUrl(CONTAINER.getHttpHostAddress());
    openSearchClient = new OpensearchConnector(config.getConnect()).createClient();

    opensearchEngineClient = new OpensearchEngineClient(openSearchClient);
  }

  @BeforeEach
  public void refresh() throws IOException {
    openSearchClient.indices().delete(req -> req.index("*"));
    openSearchClient.indices().deleteIndexTemplate(req -> req.name("*"));
  }

  @Test
  void shouldCreateIndexNormally() throws IOException {
    final var descriptor =
        SchemaTestUtil.mockIndex("qualified_name", "alias", "index_name", "/mappings.json");

    final var indexSettings = new IndexSettings();
    opensearchEngineClient.createIndex(descriptor, indexSettings);

    final var index =
        openSearchClient.indices().get(req -> req.index("qualified_name")).get("qualified_name");

    SchemaTestUtil.validateMappings(index.mappings(), "/mappings.json");

    assertThat(index.aliases().keySet()).isEqualTo(Set.of("alias"));
    assertThat(index.settings().index().numberOfReplicas())
        .isEqualTo(indexSettings.getNumberOfReplicas().toString());
    assertThat(index.settings().index().numberOfShards())
        .isEqualTo(indexSettings.getNumberOfShards().toString());
  }
}
