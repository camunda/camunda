/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.NoopExporterConfiguration.ElasticsearchConfig;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.apache.http.HttpHost;
import org.assertj.core.api.Assertions;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class ElasticsearchSchemaManagerIT {
  @Container
  private static final ElasticsearchContainer CONTAINER = TestSupport.createDefaultContainer();

  private static ElasticsearchClient elsClient;

  private static SearchEngineClient searchEngineClient;

  @BeforeAll
  public static void init() {
    // Create the low-level client
    final RestClient restClient =
        RestClient.builder(HttpHost.create(CONTAINER.getHttpHostAddress())).build();

    // Create the transport with a Jackson mapper
    final ElasticsearchTransport transport =
        new RestClientTransport(restClient, new JacksonJsonpMapper(new ObjectMapper()));

    // And create the API client
    elsClient = new ElasticsearchClient(transport);

    searchEngineClient = new ElasticsearchEngineClient(elsClient);
  }

  @BeforeEach
  public void refresh() throws IOException {
    elsClient.indices().delete(req -> req.index("*"));
    elsClient.indices().deleteIndexTemplate(req -> req.name("*"));
  }

  @Test
  void shouldInheritDefaultSettingsIfNoIndexSpecificSettings() throws IOException {
    final var settings = new ElasticsearchConfig();
    settings.defaultSettings.numberOfReplicas = "10";
    settings.defaultSettings.numberOfShards = "10";

    final var indexTemplate =
        TestUtil.mockIndexTemplate(
            "indexName",
            "full_name*",
            "alias",
            Collections.emptyList(),
            "template_name",
            "mappings.json");

    final var index = TestUtil.mockIndex("full_name", "alias", "index_name", "mappings.json");

    final var schemaManager =
        new ElasticsearchSchemaManager(
            searchEngineClient, List.of(index), List.of(indexTemplate), settings);

    schemaManager.initialiseResources();

    final var retrievedIndex =
        elsClient.indices().get(req -> req.index("full_name")).get("full_name");

    Assertions.assertThat(retrievedIndex.settings().index().numberOfReplicas()).isEqualTo("10");
    Assertions.assertThat(retrievedIndex.settings().index().numberOfShards()).isEqualTo("10");
  }

  @Test
  void shouldUseIndexSpecificSettingsIfSpecified() throws IOException {
    final var settings = new ElasticsearchConfig();
    settings.defaultSettings.numberOfReplicas = "10";
    settings.defaultSettings.numberOfShards = "10";
    settings.replicasByIndexName.put("index_name", "5");
    settings.shardsByIndexName.put("index_name", "5");

    final var indexTemplate =
        TestUtil.mockIndexTemplate(
            "index_name",
            "full_name*",
            "alias",
            Collections.emptyList(),
            "template_name",
            "mappings.json");

    final var index = TestUtil.mockIndex("full_name", "alias", "index_name", "mappings.json");

    final var schemaManager =
        new ElasticsearchSchemaManager(
            searchEngineClient, List.of(index), List.of(indexTemplate), settings);

    schemaManager.initialiseResources();

    final var retrievedIndex =
        elsClient.indices().get(req -> req.index("full_name")).get("full_name");

    Assertions.assertThat(retrievedIndex.settings().index().numberOfReplicas()).isEqualTo("5");
    Assertions.assertThat(retrievedIndex.settings().index().numberOfShards()).isEqualTo("5");
  }
}
