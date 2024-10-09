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
import static org.mockito.Mockito.*;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.config.ExporterConfiguration.IndexSettings;
import io.camunda.exporter.utils.TestSupport;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class ElasticsearchEngineClientIT {
  @Container
  private static final ElasticsearchContainer CONTAINER = TestSupport.createDefaultContainer();

  private static ElasticsearchClient elsClient;
  private static ElasticsearchEngineClient elsEngineClient;

  @BeforeAll
  public static void init() {
    // Create the low-level client
    final var config = new ExporterConfiguration();
    config.getConnect().setUrl(CONTAINER.getHttpHostAddress());
    elsClient = new ElasticsearchConnector(config.getConnect()).createClient();

    elsEngineClient = new ElasticsearchEngineClient(elsClient);
  }

  @BeforeEach
  public void refresh() throws IOException {
    elsClient.indices().delete(req -> req.index("*"));
    elsClient.indices().deleteIndexTemplate(req -> req.name("*"));
  }

  @Test
  void shouldPutMappingCorrectly() throws IOException {
    // given
    final var indexName = "test";
    elsClient.indices().create(req -> req.index(indexName));

    final var descriptor = mock(IndexDescriptor.class);
    doReturn(indexName).when(descriptor).getFullQualifiedName();

    final Set<IndexMappingProperty> newProperties = new HashSet<>();
    newProperties.add(new IndexMappingProperty("email", Map.of("type", "keyword")));

    // when
    elsEngineClient.putMapping(descriptor, newProperties);

    // then
    final var index = elsClient.indices().get(req -> req.index(indexName)).get(indexName);

    assertThat(index.mappings().properties().get("email").isKeyword()).isTrue();
  }

  @Test
  void shouldCreateIndexTemplateCorrectly() throws IOException {
    // given, when
    final var indexTemplate =
        SchemaTestUtil.mockIndexTemplate(
            "index_name",
            "test*",
            "alias",
            Collections.emptyList(),
            "template_name",
            "/mappings.json");

    final var settings = new IndexSettings();
    elsEngineClient.createIndexTemplate(indexTemplate, settings, true);

    // then
    final var indexTemplates =
        elsClient.indices().getIndexTemplate(req -> req.name("template_name")).indexTemplates();

    assertThat(indexTemplates.size()).isEqualTo(1);
    validateMappings(
        indexTemplates.getFirst().indexTemplate().template().mappings(), "/mappings.json");
  }

  @Test
  void shouldCreateIndexCorrectly() throws IOException {
    // given
    final var qualifiedIndexName = "full_name";
    final var descriptor =
        SchemaTestUtil.mockIndex(qualifiedIndexName, "alias", "index_name", "/mappings.json");

    // when
    elsEngineClient.createIndex(descriptor, new IndexSettings());

    // then
    final var index =
        elsClient.indices().get(req -> req.index(qualifiedIndexName)).get(qualifiedIndexName);

    validateMappings(index.mappings(), "/mappings.json");
  }

  @Test
  void shouldRetrieveAllIndexMappingsWithImplementationAgnosticReturnType() {
    final var index =
        SchemaTestUtil.mockIndex("index_qualified_name", "alias", "index_name", "/mappings.json");

    elsEngineClient.createIndex(index, new IndexSettings());

    final var mappings = elsEngineClient.getMappings("*", MappingSource.INDEX);

    assertThat(mappings.size()).isEqualTo(1);
    assertThat(mappings.get("index_qualified_name").dynamic()).isEqualTo("strict");

    assertThat(mappings.get("index_qualified_name").properties())
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

  @Test
  void shouldRetrieveAllIndexTemplateMappingsWithImplementationAgnosticReturnType() {
    final var template =
        SchemaTestUtil.mockIndexTemplate(
            "index_name", "index_pattern.*", "alias", List.of(), "template_name", "/mappings.json");

    elsEngineClient.createIndexTemplate(template, new IndexSettings(), true);

    final var templateMappings =
        elsEngineClient.getMappings("template_name", MappingSource.INDEX_TEMPLATE);

    assertThat(templateMappings.size()).isEqualTo(1);
    assertThat(templateMappings.get("template_name").properties())
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

  @Test
  void shouldCreateIndexTemplateIfSourceFileContainsSettings() throws IOException {
    final var template =
        SchemaTestUtil.mockIndexTemplate(
            "index_name",
            "index_pattern.*",
            "alias",
            List.of(),
            "template_name",
            "/mappings-and-settings.json");

    elsEngineClient.createIndexTemplate(template, new IndexSettings(), true);

    final var createdTemplate =
        elsClient.indices().getIndexTemplate(req -> req.name("template_name")).indexTemplates();

    assertThat(createdTemplate.size()).isEqualTo(1);
    assertThat(
            createdTemplate
                .getFirst()
                .indexTemplate()
                .template()
                .settings()
                .index()
                .refreshInterval()
                .time())
        .isEqualTo("2s");
  }

  @Test
  void shouldUpdateSettingsWithPutSettingsRequest() throws IOException {
    final var index =
        SchemaTestUtil.mockIndex("index_name", "alias", "index_name", "/mappings.json");

    elsEngineClient.createIndex(index, new IndexSettings());

    final Map<String, String> newSettings = Map.of("index.lifecycle.name", "test");
    elsEngineClient.putSettings(List.of(index), newSettings);

    final var indices = elsClient.indices().get(req -> req.index("index_name"));

    assertThat(indices.result().size()).isEqualTo(1);
    assertThat(indices.result().get("index_name").settings().index().lifecycle().name())
        .isEqualTo("test");
  }

  @Test
  void shouldSetReplicasAndShardsFromConfigurationDuringIndexCreation() throws IOException {
    final var index =
        SchemaTestUtil.mockIndex("index_name", "alias", "index_name", "/mappings.json");

    final var settings = new IndexSettings();
    settings.setNumberOfReplicas(5);
    settings.setNumberOfShards(10);
    elsEngineClient.createIndex(index, settings);

    final var indices = elsClient.indices().get(req -> req.index("index_name"));

    assertThat(indices.result().size()).isEqualTo(1);
    assertThat(indices.result().get("index_name").settings().index().numberOfReplicas())
        .isEqualTo("5");
    assertThat(indices.result().get("index_name").settings().index().numberOfShards())
        .isEqualTo("10");
  }

  @Test
  void shouldCreateIndexLifeCyclePolicy() throws IOException {
    elsEngineClient.putIndexLifeCyclePolicy("policy_name", "20d");

    final var policy = elsClient.ilm().getLifecycle(req -> req.name("policy_name"));

    assertThat(policy.result().size()).isEqualTo(1);
    assertThat(policy.result().get("policy_name").policy().phases().delete().minAge().time())
        .isEqualTo("20d");
    assertThat(policy.result().get("policy_name").policy().phases().delete().actions()).isNotNull();
  }
}
