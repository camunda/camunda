/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema;

import static io.camunda.exporter.schema.SchemaTestUtil.validateMappings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.utils.TestSupport;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
    final var config = new ExporterConfiguration();
    config.getConnect().setUrl(CONTAINER.getHttpHostAddress());
    elsClient = new ElasticsearchConnector(config.getConnect()).createClient();

    searchEngineClient = new ElasticsearchEngineClient(elsClient);
  }

  @BeforeEach
  public void refresh() throws IOException {
    elsClient.indices().delete(req -> req.index("*"));
    elsClient.indices().deleteIndexTemplate(req -> req.name("*"));
  }

  @Test
  void shouldInheritDefaultSettingsIfNoIndexSpecificSettings() throws IOException {
    final var properties = new ExporterConfiguration();
    properties.getIndex().setNumberOfReplicas(10);
    properties.getIndex().setNumberOfShards(10);

    final var indexTemplate =
        SchemaTestUtil.mockIndexTemplate(
            "indexName",
            "full_name*",
            "alias",
            Collections.emptyList(),
            "template_name",
            "/mappings.json");

    final var index =
        SchemaTestUtil.mockIndex("full_name", "alias", "index_name", "/mappings.json");

    final var schemaManager =
        new ElasticsearchSchemaManager(
            searchEngineClient, Set.of(index), Set.of(indexTemplate), properties);

    schemaManager.initialiseResources();

    final var retrievedIndex =
        elsClient.indices().get(req -> req.index("full_name")).get("full_name");

    assertThat(retrievedIndex.settings().index().numberOfReplicas()).isEqualTo("10");
    assertThat(retrievedIndex.settings().index().numberOfShards()).isEqualTo("10");
  }

  @Test
  void shouldUseIndexSpecificSettingsIfSpecified() throws IOException {
    final var properties = new ExporterConfiguration();
    properties.getIndex().setNumberOfReplicas(10);
    properties.getIndex().setNumberOfShards(10);
    properties.setReplicasByIndexName(Map.of("index_name", 5));
    properties.setShardsByIndexName(Map.of("index_name", 5));

    final var indexTemplate =
        SchemaTestUtil.mockIndexTemplate(
            "index_name",
            "full_name*",
            "alias",
            Collections.emptyList(),
            "template_name",
            "/mappings.json");

    final var index =
        SchemaTestUtil.mockIndex("full_name", "alias", "index_name", "/mappings.json");

    final var schemaManager =
        new ElasticsearchSchemaManager(
            searchEngineClient, Set.of(index), Set.of(indexTemplate), properties);

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
        SchemaTestUtil.mockIndexTemplate(
            "index_name",
            "full_name*",
            "alias",
            Collections.emptyList(),
            "template_name",
            "/mappings.json");
    final var schemaManager =
        new ElasticsearchSchemaManager(
            searchEngineClient, Set.of(), Set.of(indexTemplate), new ExporterConfiguration());

    schemaManager.initialiseResources();

    // when
    when(indexTemplate.getMappingsClasspathFilename()).thenReturn("/mappings-added-property.json");

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

    validateMappings(
        template.indexTemplate().template().mappings(), "/mappings-added-property.json");
  }

  @Test
  void shouldAppendToIndexMappingsWithNewProperties() throws IOException {
    // given
    final var index =
        SchemaTestUtil.mockIndex("index_name", "alias", "index_name", "/mappings.json");

    final var schemaManager =
        new ElasticsearchSchemaManager(
            searchEngineClient, Set.of(index), Set.of(), new ExporterConfiguration());

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
    final var index =
        SchemaTestUtil.mockIndex("index_name", "alias", "index_name", "/mappings.json");

    final var schemaManager =
        new ElasticsearchSchemaManager(
            searchEngineClient, Set.of(), Set.of(), new ExporterConfiguration());

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
