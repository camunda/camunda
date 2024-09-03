/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.IndexTemplateSummary;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.NoopExporterConfiguration.IndexSettings;
import io.camunda.exporter.schema.descriptors.IndexDescriptor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeAll;
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

  @Test
  void shouldPutMappingCorrectly() throws IOException {
    // given
    final var indexName = "test";
    elsClient.indices().create(req -> req.index(indexName));

    final var descriptor = mock(IndexDescriptor.class);
    doReturn(indexName).when(descriptor).getIndexName();
    doReturn("newProperties.json").when(descriptor).getMappingsClasspathFilename();

    // when

    elsEngineClient.putMapping(
        descriptor,
        IOUtils.resourceToString(
            "newProperties.json", StandardCharsets.UTF_8, getClass().getClassLoader()));

    // then
    final var index = elsClient.indices().get(req -> req.index(indexName)).get(indexName);

    assertThat(index.mappings().properties().get("email").isKeyword()).isTrue();
  }

  @Test
  void shouldCreateIndexTemplateCorrectly() throws IOException {
    // given, when
    final var indexTemplate =
        TestUtil.mockIndexTemplate(
            "index_name",
            "test*",
            "alias",
            Collections.emptyList(),
            "template_name",
            "mappings.json");

    final var settings = new IndexSettings();
    settings.numberOfShards = 1;
    settings.numberOfReplicas = 0;
    elsEngineClient.createIndexTemplate(indexTemplate, settings, true);

    // then
    final var indexTemplates =
        elsClient.indices().getIndexTemplate(req -> req.name("template_name")).indexTemplates();

    assertThat(indexTemplates.size()).isEqualTo(1);

    final var retrievedTemplate = indexTemplates.getFirst().indexTemplate().template();
    final var template =
        elsEngineClient.deserializeJson(
            IndexTemplateSummary._DESERIALIZER,
            getClass().getClassLoader().getResourceAsStream("mappings.json"));
    assertThat(retrievedTemplate.mappings().toString()).isEqualTo(template.mappings().toString());
  }

  @Test
  void shouldCreateIndexCorrectly() throws IOException {
    // given
    final var descriptor = mock(IndexDescriptor.class);
    final var qualifiedIndexName = "full_name";
    doReturn(qualifiedIndexName).when(descriptor).getFullQualifiedName();
    doReturn("alias").when(descriptor).getAlias();
    doReturn("index_name").when(descriptor).getIndexName();
    doReturn("mappings.json").when(descriptor).getMappingsClasspathFilename();

    final var template =
        elsEngineClient.deserializeJson(
            IndexTemplateSummary._DESERIALIZER,
            getClass().getClassLoader().getResourceAsStream("mappings.json"));

    // when
    elsEngineClient.createIndex(descriptor);

    // then
    final var index =
        elsClient.indices().get(req -> req.index(qualifiedIndexName)).get(qualifiedIndexName);

    assertThat(index.mappings().toString()).isEqualTo(template.mappings().toString());
  }
}
