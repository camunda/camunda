/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.config.ElasticsearchProperties;
import io.camunda.exporter.schema.descriptors.IndexDescriptor;
import io.camunda.exporter.utils.TestSupport;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.http.HttpHost;
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
    final var properties = new ElasticsearchProperties();
    properties.getDefaultSettings().setNumberOfReplicas(10);
    properties.getDefaultSettings().setNumberOfShards(10);

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
            searchEngineClient, List.of(index), List.of(indexTemplate), properties);

    schemaManager.initialiseResources();

    final var retrievedIndex =
        elsClient.indices().get(req -> req.index("full_name")).get("full_name");

    assertThat(retrievedIndex.settings().index().numberOfReplicas()).isEqualTo("10");
    assertThat(retrievedIndex.settings().index().numberOfShards()).isEqualTo("10");
  }

  @Test
  void shouldUseIndexSpecificSettingsIfSpecified() throws IOException {
    final var properties = new ElasticsearchProperties();
    properties.getDefaultSettings().setNumberOfReplicas(10);
    properties.getDefaultSettings().setNumberOfShards(10);
    properties.setReplicasByIndexName(Map.of("index_name", 5));
    properties.setShardsByIndexName(Map.of("index_name", 5));

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
            searchEngineClient, List.of(index), List.of(indexTemplate), properties);

    schemaManager.initialiseResources();

    final var retrievedIndex =
        elsClient.indices().get(req -> req.index("full_name")).get("full_name");

    assertThat(retrievedIndex.settings().index().numberOfReplicas()).isEqualTo("5");
    assertThat(retrievedIndex.settings().index().numberOfShards()).isEqualTo("5");
  }

  @Test
  void shouldOverwriteIndexTemplateIfMappingsFileChanged() throws IOException {
    // given
    final var indexTemplate =
        TestUtil.mockIndexTemplate(
            "index_name",
            "full_name*",
            "alias",
            Collections.emptyList(),
            "template_name",
            "mappings.json");
    final var schemaManager =
        new ElasticsearchSchemaManager(
            searchEngineClient, List.of(), List.of(indexTemplate), new ElasticsearchProperties());

    schemaManager.initialiseResources();

    // when
    when(indexTemplate.getMappingsClasspathFilename()).thenReturn("mappings-added-property.json");

    final Map<IndexDescriptor, Set<IndexMappingProperty>> schemasToChange =
        Map.of(indexTemplate, Set.of());
    schemaManager.updateSchema(schemasToChange);

    // then
    final var template =
        elsClient
            .indices()
            .getIndexTemplate(req -> req.name("template_name"))
            .indexTemplates()
            .getFirst();

    assertThat(template.indexTemplate().template().mappings().properties().get("foo").isText())
        .isTrue();
  }

  @Test
  void shouldAppendToIndexMappingsWithNewProperties() throws IOException {
    // given
    final var index = TestUtil.mockIndex("index_name", "alias", "index_name", "mappings.json");

    final var schemaManager =
        new ElasticsearchSchemaManager(
            searchEngineClient, List.of(index), List.of(), new ElasticsearchProperties());

    schemaManager.initialiseResources();

    // when
    final var newProperties = new HashSet<IndexMappingProperty>();
    newProperties.add(new IndexMappingProperty("foo", Map.of("type", "text")));
    newProperties.add(new IndexMappingProperty("bar", Map.of("type", "keyword")));

    final Map<IndexDescriptor, Set<IndexMappingProperty>> schemasToChange =
        Map.of(index, newProperties);

    schemaManager.updateSchema(schemasToChange);

    // then
    final var updatedIndex =
        elsClient.indices().get(req -> req.index("index_name")).get("index_name");

    assertThat(updatedIndex.mappings().properties().get("foo").isText()).isTrue();
    assertThat(updatedIndex.mappings().properties().get("bar").isKeyword()).isTrue();
  }

  @Test
  void shouldReadIndexMappingsFileCorrectly() {
    // given
    final var index = TestUtil.mockIndex("index_name", "alias", "index_name", "mappings.json");

    final var schemaManager =
        new ElasticsearchSchemaManager(
            searchEngineClient, List.of(), List.of(), new ElasticsearchConfig());

    // when
    final var indexMapping = schemaManager.readIndex(index);

    // then
    assertThat(indexMapping.dynamic()).isEqualTo("strict");

    assertThat(indexMapping.properties())
        .containsExactlyInAnyOrder(
            new IndexMappingProperty.Builder()
                .name("hello")
                .typeDefinition(Map.of("type", "text"))
                .build(),
            new IndexMappingProperty.Builder()
                .name("world")
                .typeDefinition(Map.of("type", "keyword"))
                .build());
  }
}
