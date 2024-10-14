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
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.utils.TestSupport;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import java.io.IOException;
import java.util.Collection;
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

  private static final ExporterConfiguration CONFIG = new ExporterConfiguration();
  private static ElasticsearchClient elsClient;
  private static SearchEngineClient searchEngineClient;
  private IndexDescriptor index;
  private IndexTemplateDescriptor indexTemplate;

  @BeforeAll
  public static void init() {
    // Create the low-level client
    CONFIG.getConnect().setUrl(CONTAINER.getHttpHostAddress());
    elsClient = new ElasticsearchConnector(CONFIG.getConnect()).createClient();

    searchEngineClient = new ElasticsearchEngineClient(elsClient);
  }

  @BeforeEach
  public void refresh() throws IOException {
    elsClient.indices().delete(req -> req.index("*"));
    elsClient.indices().deleteIndexTemplate(req -> req.name("*"));

    indexTemplate =
        SchemaTestUtil.mockIndexTemplate(
            "index_name",
            "test*",
            "template_alias",
            Collections.emptyList(),
            "template_name",
            "/mappings.json");

    index =
        SchemaTestUtil.mockIndex(
            CONFIG.getIndex().getPrefix() + "qualified_name",
            "alias",
            "index_name",
            "/mappings.json");

    when(indexTemplate.getFullQualifiedName())
        .thenReturn(CONFIG.getIndex().getPrefix() + "template_index_qualified_name");
  }

  @Test
  void shouldInheritDefaultSettingsIfNoIndexSpecificSettings() throws IOException {
    final var properties = new ExporterConfiguration();
    properties.getIndex().setNumberOfReplicas(10);
    properties.getIndex().setNumberOfShards(10);

    final var schemaManager =
        new ElasticsearchSchemaManager(
            searchEngineClient, Set.of(index), Set.of(indexTemplate), properties);

    schemaManager.initialiseResources();

    final var retrievedIndex =
        elsClient
            .indices()
            .get(req -> req.index(index.getFullQualifiedName()))
            .get(index.getFullQualifiedName());

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

    final var schemaManager =
        new ElasticsearchSchemaManager(
            searchEngineClient, Set.of(index), Set.of(indexTemplate), properties);

    schemaManager.initialiseResources();

    final var retrievedIndex =
        elsClient
            .indices()
            .get(req -> req.index(index.getFullQualifiedName()))
            .get(index.getFullQualifiedName());

    assertThat(retrievedIndex.settings().index().numberOfReplicas()).isEqualTo("5");
    assertThat(retrievedIndex.settings().index().numberOfShards()).isEqualTo("5");
  }

  @Test
  void shouldOverwriteIndexTemplateIfMappingsFileChanged() throws IOException {
    // given
    final var schemaManager =
        new ElasticsearchSchemaManager(
            searchEngineClient, Set.of(), Set.of(indexTemplate), new ExporterConfiguration());

    schemaManager.initialiseResources();

    // when
    when(indexTemplate.getMappingsClasspathFilename()).thenReturn("/mappings-added-property.json");

    final Map<IndexDescriptor, Collection<IndexMappingProperty>> schemasToChange =
        Map.of(indexTemplate, Set.of());
    schemaManager.updateSchema(schemasToChange);

    // then
    final var template =
        elsClient
            .indices()
            .getIndexTemplate(req -> req.name(indexTemplate.getTemplateName()))
            .indexTemplates()
            .getFirst();

    validateMappings(
        template.indexTemplate().template().mappings(), "/mappings-added-property.json");
  }

  @Test
  void shouldAppendToIndexMappingsWithNewProperties() throws IOException {
    // given
    final var schemaManager =
        new ElasticsearchSchemaManager(
            searchEngineClient, Set.of(index), Set.of(), new ExporterConfiguration());

    schemaManager.initialiseResources();

    // when
    final var newProperties = new HashSet<IndexMappingProperty>();
    newProperties.add(new IndexMappingProperty("foo", Map.of("type", "text")));
    newProperties.add(new IndexMappingProperty("bar", Map.of("type", "keyword")));

    final Map<IndexDescriptor, Collection<IndexMappingProperty>> schemasToChange =
        Map.of(index, newProperties);

    schemaManager.updateSchema(schemasToChange);

    // then
    final var updatedIndex =
        elsClient
            .indices()
            .get(req -> req.index(index.getFullQualifiedName()))
            .get(index.getFullQualifiedName());

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
    final var indexMapping = IndexMapping.from(index, new ObjectMapper());

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
