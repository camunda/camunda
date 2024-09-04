/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.NoopExporterConfiguration.ElasticsearchConfig;
import io.camunda.exporter.NoopExporterConfiguration.IndexSettings;
import io.camunda.exporter.exceptions.IndexSchemaValidationException;
import io.camunda.exporter.schema.ElasticsearchEngineClient.MappingSource;
import java.io.IOException;
import java.util.Collections;
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
public class IndexSchemaValidatorIT {

  @Container
  private static final ElasticsearchContainer CONTAINER = TestSupport.createDefaultContainer();

  private static ElasticsearchClient elsClient;

  private static SearchEngineClient searchEngineClient;

  private static IndexSchemaValidator validator;

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

    final var schemaManager =
        new ElasticsearchSchemaManager(
            searchEngineClient, List.of(), List.of(), new ElasticsearchConfig());

    validator = new IndexSchemaValidator(schemaManager);
  }

  @BeforeEach
  public void refresh() throws IOException {
    elsClient.indices().delete(req -> req.index("*"));
    elsClient.indices().deleteIndexTemplate(req -> req.name("*"));
  }

  @Test
  void shouldDetectIndexWithAddedProperty() {
    // given
    final var index = TestUtil.mockIndex("qualified_name", "alias", "index_name", "mappings.json");

    searchEngineClient.createIndex(index);

    // when
    when(index.getMappingsClasspathFilename()).thenReturn("mappings-added-property.json");

    // then
    final var currentIndices = searchEngineClient.getMappings("*", MappingSource.INDEX);
    final var difference = validator.validateIndexMappings(currentIndices, Set.of(index));

    assertThat(difference)
        .containsExactly(
            entry(
                index,
                Set.of(
                    new IndexMappingProperty.Builder()
                        .name("foo")
                        .typeDefinition(Map.of("type", "text"))
                        .build())));
  }

  @Test
  void shouldValidateSameIndexWithNoDifferences() {
    // given
    final var index = TestUtil.mockIndex("qualified_name", "alias", "index_name", "mappings.json");

    searchEngineClient.createIndex(index);

    // when, then
    final var currentIndices =
        searchEngineClient.getMappings(index.getFullQualifiedName(), MappingSource.INDEX);
    final var difference = validator.validateIndexMappings(currentIndices, Set.of(index));

    assertThat(difference).isEmpty();
  }

  @Test
  void shouldIgnoreNotCreatedIndicesFromValidation() {

    // given, when, then
    final var currentIndices = searchEngineClient.getMappings("*", MappingSource.INDEX);

    final var index = TestUtil.mockIndex("qualified_name", "alias", "index_name", "mappings.json");
    final var difference = validator.validateIndexMappings(currentIndices, Set.of(index));

    assertThat(difference).isEmpty();
  }

  @Test
  public void shouldDetectAmbiguousIndexDifference() {
    // given
    final var oldIndex =
        TestUtil.mockIndex(
            "qualified_name", "alias", "index_name", "mappings-deleted-property.json");

    final var oldIndex2 =
        TestUtil.mockIndex(
            "qualified_name_2", "alias2", "index_name", "mappings-deleted-different-property.json");

    searchEngineClient.createIndex(oldIndex);
    searchEngineClient.createIndex(oldIndex2);

    // when
    final var currentIndex =
        TestUtil.mockIndex("qualified_name", "alias3", "index_name", "mappings.json");

    // then
    final var currentIndices = searchEngineClient.getMappings("*", MappingSource.INDEX);

    assertThatThrownBy(() -> validator.validateIndexMappings(currentIndices, Set.of(currentIndex)))
        .isInstanceOf(IndexSchemaValidationException.class)
        .hasMessageContaining(
            "Ambiguous schema update. First bring runtime and date indices to one schema.");
  }

  @Test
  void shouldIgnoreARemovedIndexProperty() {
    // given
    final var index =
        TestUtil.mockIndex("qualified_name", "alias", "index_name", "mappings-added-property.json");

    searchEngineClient.createIndex(index);

    // when
    when(index.getMappingsClasspathFilename()).thenReturn("mappings.json");

    // then
    final var currentIndices = searchEngineClient.getMappings("*", MappingSource.INDEX);
    final var difference = validator.validateIndexMappings(currentIndices, Set.of(index));

    assertThat(difference).isEmpty();
  }

  @Test
  void shouldDetectChangedIndexMappingParameters() {
    // given
    final var index = TestUtil.mockIndex("qualified_name", "alias", "index_name", "mappings.json");

    searchEngineClient.createIndex(index);

    // when
    when(index.getMappingsClasspathFilename()).thenReturn("mappings-changed-property-invalid.json");

    // then
    final var currentIndices = searchEngineClient.getMappings("*", MappingSource.INDEX);
    assertThatThrownBy(() -> validator.validateIndexMappings(currentIndices, Set.of(index)))
        .isInstanceOf(IndexSchemaValidationException.class)
        .hasMessageContaining(
            "Not supported index changes are introduced. Data migration is required.");
  }

  @Test
  void shouldDetectIndexTemplateWithAddedProperty() {
    // given
    final var indexTemplate =
        TestUtil.mockIndexTemplate(
            "index_name",
            "index_name.*",
            "alias",
            Collections.emptyList(),
            "template_name",
            "mappings.json");

    searchEngineClient.createIndexTemplate(indexTemplate, new IndexSettings(), true);

    // when
    when(indexTemplate.getMappingsClasspathFilename()).thenReturn("mappings-added-property.json");

    // then
    final var currentMappings =
        searchEngineClient.getMappings("template_name", MappingSource.INDEX_TEMPLATE);
    final var difference = validator.validateIndexMappings(currentMappings, Set.of(indexTemplate));

    assertThat(difference)
        .containsExactly(
            entry(
                indexTemplate,
                Set.of(
                    new IndexMappingProperty.Builder()
                        .name("foo")
                        .typeDefinition(Map.of("type", "text"))
                        .build())));
  }

  @Test
  void shouldIgnoreARemovedTemplateProperty() {
    // given
    final var indexTemplate =
        TestUtil.mockIndexTemplate(
            "index_name",
            "index_name.*",
            "alias",
            Collections.emptyList(),
            "template_name",
            "mappings.json");

    searchEngineClient.createIndexTemplate(indexTemplate, new IndexSettings(), true);

    // when
    when(indexTemplate.getMappingsClasspathFilename()).thenReturn("mappings-deleted-property.json");

    // then
    final var currentMappings = searchEngineClient.getMappings("*", MappingSource.INDEX_TEMPLATE);
    final var difference = validator.validateIndexMappings(currentMappings, Set.of(indexTemplate));

    assertThat(difference).isEmpty();
  }
}
