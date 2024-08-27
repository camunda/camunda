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
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.put_index_template.IndexTemplateMapping;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.camunda.exporter.schema.descriptors.IndexDescriptor;
import io.camunda.exporter.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.zeebe.exporter.TestSupport;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class ElasticsearchEngineClientIT {
  @Container
  private static final ElasticsearchContainer CONTAINER = TestSupport.createDefaultContainer();

  private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchEngineClientIT.class);
  private static final IndexTemplateMapping MAPPINGS =
      new IndexTemplateMapping.Builder()
          .mappings(m -> m.properties("test", type -> type.keyword(kw -> kw)))
          .build();

  private static ElasticsearchClient elsClient;
  private static ElasticsearchEngineClient elsEngineClient;

  @BeforeAll
  public static void init() {
    // Create the low-level client
    final RestClient restClient =
        RestClient.builder(HttpHost.create(CONTAINER.getHttpHostAddress())).build();

    // Create the transport with a Jackson mapper
    final ElasticsearchTransport transport =
        new RestClientTransport(restClient, new JacksonJsonpMapper());

    // And create the API client
    elsClient = new ElasticsearchClient(transport);
  }

  private static String serialiseElsDataStructures(final Object elsDataStructure) {
    final var writer = new StringWriter();
    final var mapper = elsClient._jsonpMapper();
    try (final var generator = mapper.jsonProvider().createGenerator(writer)) {
      mapper.serialize(elsDataStructure, generator);
    }

    return writer.toString();
  }

  @BeforeEach
  public void startEngineClient() {
    final var resourceRetriever = mock(ResourceRetriever.class);
    doReturn(IOUtils.toInputStream(serialiseElsDataStructures(MAPPINGS), StandardCharsets.UTF_8))
        .when(resourceRetriever)
        .getResourceAsStream(any());

    elsEngineClient = new ElasticsearchEngineClient(elsClient, LOG, resourceRetriever);
  }

  @Test
  void shouldPutMappingCorrectly() throws IOException {
    // given
    final var indexName = "test";
    elsClient.indices().create(req -> req.index(indexName));

    final var descriptor = mock(IndexDescriptor.class);
    doReturn(indexName).when(descriptor).getIndexName();

    // when
    final var newProperties =
        new TypeMapping.Builder().properties("field", p -> p.keyword(kw -> kw)).build();

    elsEngineClient.putMapping(descriptor, serialiseElsDataStructures(newProperties));

    // then
    final var index = elsClient.indices().get(req -> req.index(indexName)).get(indexName);

    assertThat(index.mappings().properties().get("field").isKeyword()).isTrue();
  }

  @Test
  void shouldCreateComponentTemplateCorrectly() throws IOException {
    // given
    final var mappingsJson = serialiseElsDataStructures(MAPPINGS);
    final var componentTemplateName = "test";

    // when
    elsEngineClient.createComponentTemplate(componentTemplateName, mappingsJson);

    // then
    final var templates =
        elsClient.cluster().getComponentTemplate(req -> req.name(componentTemplateName));

    assertThat(templates.componentTemplates().size()).isEqualTo(1);

    final var componentTemplate = templates.componentTemplates().getFirst();
    assertThat(componentTemplate.name()).isEqualTo(componentTemplateName);
    assertThat(componentTemplate.componentTemplate().template().mappings().toString())
        .isEqualTo(MAPPINGS.mappings().toString());
  }

  @Test
  void shouldCreateIndexTemplateCorrectly() throws IOException {
    // given
    final var descriptor = mock(IndexTemplateDescriptor.class);
    doReturn("index_name").when(descriptor).getIndexName();
    doReturn("test*").when(descriptor).getIndexPattern();
    doReturn("alias").when(descriptor).getAlias();
    doReturn(Collections.emptyList()).when(descriptor).getComposedOf();

    final var templateName = "template_name";
    doReturn(templateName).when(descriptor).getTemplateName();

    // when
    elsEngineClient.createIndexTemplate(descriptor);

    // then
    final var indexTemplates =
        elsClient.indices().getIndexTemplate(req -> req.name(templateName)).indexTemplates();

    assertThat(indexTemplates.size()).isEqualTo(1);

    final var retrievedTemplate = indexTemplates.getFirst().indexTemplate().template();
    assertThat(retrievedTemplate.mappings().toString()).isEqualTo(MAPPINGS.mappings().toString());
  }

  @Test
  void shouldCreateIndexCorrectly() throws IOException {
    // given
    final var descriptor = mock(IndexDescriptor.class);
    final var qualifiedIndexName = "full_name";
    doReturn(qualifiedIndexName).when(descriptor).getFullQualifiedName();
    doReturn("alias").when(descriptor).getAlias();
    doReturn("index_name").when(descriptor).getIndexName();

    // when
    elsEngineClient.createIndex(descriptor);

    // then
    final var index =
        elsClient.indices().get(req -> req.index(qualifiedIndexName)).get(qualifiedIndexName);

    assertThat(index.mappings().toString()).isEqualTo(MAPPINGS.mappings().toString());
  }
}
