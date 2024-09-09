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
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.config.ElasticsearchProperties.IndexSettings;
import io.camunda.exporter.schema.ElasticsearchEngineClient.MappingSource;
import io.camunda.exporter.utils.TestSupport;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
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
public class ElasticsearchEngineClientIT {
  @Container
  private static final ElasticsearchContainer CONTAINER = TestSupport.createDefaultContainer();

  private static ElasticsearchClient elsClient;
  private static ElasticsearchEngineClient elsEngineClient;

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
    doReturn(indexName).when(descriptor).getIndexName();

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
            "mappings.json");

    final var settings = new IndexSettings();
    elsEngineClient.createIndexTemplate(indexTemplate, settings, true);

    // then
    final var indexTemplates =
        elsClient.indices().getIndexTemplate(req -> req.name("template_name")).indexTemplates();

    assertThat(indexTemplates.size()).isEqualTo(1);
    validateMappings(
        indexTemplates.getFirst().indexTemplate().template().mappings(), "mappings.json");
  }

  @Test
  void shouldCreateIndexCorrectly() throws IOException {
    // given
    final var qualifiedIndexName = "full_name";
    final var descriptor =
        SchemaTestUtil.mockIndex(qualifiedIndexName, "alias", "index_name", "mappings.json");

    // when
    elsEngineClient.createIndex(descriptor);

    // then
    final var index =
        elsClient.indices().get(req -> req.index(qualifiedIndexName)).get(qualifiedIndexName);

    validateMappings(index.mappings(), "mappings.json");
  }

  @Test
  void shouldRetrieveAllIndexMappingsWithImplementationAgnosticReturnType() {
    final var index =
        SchemaTestUtil.mockIndex("index_qualified_name", "alias", "index_name", "mappings.json");

    elsEngineClient.createIndex(index);

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
            "index_name", "index_pattern.*", "alias", List.of(), "template_name", "mappings.json");

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
}
