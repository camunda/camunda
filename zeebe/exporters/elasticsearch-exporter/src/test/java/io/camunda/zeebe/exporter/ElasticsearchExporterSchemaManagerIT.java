/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import org.elasticsearch.client.Request;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class ElasticsearchExporterSchemaManagerIT {
  @Container
  private static final ElasticsearchContainer CONTAINER =
      TestSearchContainers.createDefeaultElasticsearchContainer()
          .withEnv("action.destructive_requires_name", "false");

  private static final ElasticsearchExporterConfiguration CONFIG =
      new ElasticsearchExporterConfiguration();

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @BeforeAll
  static void setup() {
    CONFIG.url = "http://" + CONTAINER.getHttpHostAddress();
  }

  @Test
  public void shouldNotOverwriteNewerComponentTemplate() throws IOException {
    // given
    final var templateReader = spy(new TemplateReader(CONFIG));

    final var esClient =
        new ElasticsearchClient(
            CONFIG,
            new BulkIndexRequest(),
            RestClientFactory.of(CONFIG),
            new RecordIndexRouter(CONFIG.index),
            templateReader,
            new ElasticsearchMetrics(new SimpleMeterRegistry()));

    // when
    // we create an 8.5 component template
    final var schemaManager = new ElasticsearchExporterSchemaManager(esClient, CONFIG);
    schemaManager.createSchema("8.5.0");

    // create a future version of the component template (8.6.0) with a new foo field
    doReturn("/zeebe-record-template-with-foo-field.json")
        .when(templateReader)
        .zeebeRecordTemplateFile();
    final var schemaManager2 = new ElasticsearchExporterSchemaManager(esClient, CONFIG);
    schemaManager2.createSchema("8.6.0");

    // Use a new schema manager to mock 8.5 broker restart which then triggers schema manager.
    // 8.5 schema manager runs again and attempts to create old component template
    reset(templateReader);
    final var schemaManager3 = new ElasticsearchExporterSchemaManager(esClient, CONFIG);
    schemaManager3.createSchema("8.5.0");

    // then
    // verify component template is still the upgraded 8.6 version
    final var request = new Request("GET", "/_component_template/" + CONFIG.index.prefix);
    final var response = esClient.getRestClient().performRequest(request);
    final var componentTemplateInElasticsearch =
        MAPPER.readValue(response.getEntity().getContent().readAllBytes(), JsonNode.class);

    Assertions.assertFalse(
        componentTemplateInElasticsearch
            .at("/component_templates/0/component_template/template/mappings/properties/foo")
            .isEmpty());
  }
}
