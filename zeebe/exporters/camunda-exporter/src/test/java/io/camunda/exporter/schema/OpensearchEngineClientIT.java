/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.config.ExporterConfiguration.IndexSettings;
import io.camunda.exporter.exceptions.OpensearchExporterException;
import io.camunda.exporter.utils.TestSupport;
import io.camunda.search.connect.os.OpensearchConnector;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    // given
    final var descriptor =
        SchemaTestUtil.mockIndex("qualified_name", "alias", "index_name", "/mappings.json");

    // when
    final var indexSettings = new IndexSettings();
    opensearchEngineClient.createIndex(descriptor, indexSettings);

    // then
    final var index =
        openSearchClient.indices().get(req -> req.index("qualified_name")).get("qualified_name");

    SchemaTestUtil.validateMappings(index.mappings(), "/mappings.json");

    assertThat(index.aliases().keySet()).isEqualTo(Set.of("alias"));
    assertThat(index.settings().index().numberOfReplicas())
        .isEqualTo(indexSettings.getNumberOfReplicas().toString());
    assertThat(index.settings().index().numberOfShards())
        .isEqualTo(indexSettings.getNumberOfShards().toString());
  }

  @Test
  void shouldCreateIndexTemplate() throws IOException {
    // given
    final var template =
        SchemaTestUtil.mockIndexTemplate(
            "index_name",
            "index_pattern.*",
            "alias",
            List.of(),
            "template_name",
            "/mappings-and-settings.json");

    // when
    final var expectedIndexSettings = new IndexSettings();
    opensearchEngineClient.createIndexTemplate(template, expectedIndexSettings, false);

    // then
    final var createdTemplate =
        openSearchClient
            .indices()
            .getIndexTemplate(req -> req.name("template_name"))
            .indexTemplates();

    assertThat(createdTemplate.size()).isEqualTo(1);

    final var indexSettings =
        createdTemplate
            .getFirst()
            .indexTemplate()
            .template()
            .settings()
            .get("index")
            .toJson()
            .asJsonObject();

    assertThat(indexSettings.getString("number_of_shards"))
        .isEqualTo(expectedIndexSettings.getNumberOfShards().toString());
    assertThat(indexSettings.getString("number_of_replicas"))
        .isEqualTo(expectedIndexSettings.getNumberOfReplicas().toString());
    assertThat(indexSettings.getString("refresh_interval")).isEqualTo("2s");

    SchemaTestUtil.validateMappings(
        createdTemplate.getFirst().indexTemplate().template().mappings(),
        template.getMappingsClasspathFilename());
  }

  @Test
  void shouldFailIndexTemplateUpdateIfCreateTrue() {
    // given
    final var template =
        SchemaTestUtil.mockIndexTemplate(
            "index_name", "index_pattern.*", "alias", List.of(), "template_name", "/mappings.json");
    opensearchEngineClient.createIndexTemplate(template, new IndexSettings(), false);

    // when
    // then
    assertThatThrownBy(
            () -> opensearchEngineClient.createIndexTemplate(template, new IndexSettings(), true))
        .isInstanceOf(OpensearchExporterException.class)
        .hasMessageContaining("Cannot update template [template_name] as create = true");
  }

  @Test
  void shouldPutMappingCorrectly() throws IOException {
    // given
    final var descriptor =
        SchemaTestUtil.mockIndex("qualified_name", "alias", "index_name", "/mappings.json");
    opensearchEngineClient.createIndex(descriptor, new IndexSettings());

    final Set<IndexMappingProperty> newProperties = new HashSet<>();
    newProperties.add(new IndexMappingProperty("email", Map.of("type", "keyword")));
    newProperties.add(new IndexMappingProperty("age", Map.of("type", "integer")));

    // when
    opensearchEngineClient.putMapping(descriptor, newProperties);

    // then
    final var indices =
        openSearchClient
            .indices()
            .get(req -> req.index(descriptor.getFullQualifiedName()))
            .result();

    assertThat(indices.size()).isEqualTo(1);
    final var properties = indices.get(descriptor.getFullQualifiedName()).mappings().properties();

    assertThat(properties.get("email").isKeyword()).isTrue();
    assertThat(properties.get("age").isInteger()).isTrue();
  }
}
