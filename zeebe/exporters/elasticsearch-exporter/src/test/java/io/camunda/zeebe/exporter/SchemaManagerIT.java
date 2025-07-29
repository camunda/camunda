/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import io.camunda.zeebe.util.VersionUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.elasticsearch.client.Request;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class SchemaManagerIT {
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
  public void shouldUseVersionedComponentTemplateForIndexTemplates() throws IOException {
    // given
    final var restClient = RestClientFactory.of(CONFIG);
    final var esClient = new ElasticsearchClient(CONFIG, new SimpleMeterRegistry());

    // when
    // we create an 8.5 component template
    final var schemaManager = new ElasticsearchExporterSchemaManager(esClient, CONFIG);
    schemaManager.createSchema("8.5.0");

    // create the 8.6 component template
    final var upgradedVersion = "8.6.0";
    try (final MockedStatic<VersionUtil> mockedVersionUtil = mockStatic(VersionUtil.class)) {
      mockedVersionUtil.when(VersionUtil::getVersion).thenReturn(upgradedVersion);
      mockedVersionUtil.when(VersionUtil::getVersionLowerCase).thenReturn(upgradedVersion);

      final var schemaManager2 = new ElasticsearchExporterSchemaManager(esClient, CONFIG);
      schemaManager2.createSchema("8.6.0");
    }

    // Use a new schema manager to mock 8.5 broker restart which then triggers schema manager.
    // 8.5 schema manager runs again and attempts to create old component template
    final var schemaManager3 = new ElasticsearchExporterSchemaManager(esClient, CONFIG);
    schemaManager3.createSchema("8.5.0");

    // then
    // 8.6.0 component template exists
    final var request = new Request("GET", "/_component_template/" + CONFIG.index.prefix + "*");
    final var response = restClient.performRequest(request);
    final var componentTemplateInElasticsearch =
        MAPPER.readValue(response.getEntity().getContent().readAllBytes(), JsonNode.class);

    assertThat(componentTemplateInElasticsearch.at("/component_templates"))
        .anySatisfy(
            node ->
                assertThat(node.at("/name").asText()).isEqualTo("zeebe-record-" + upgradedVersion));

    // all 8.6.0 index templates are composed of the new index template
    final var templateReq = new Request("GET", "/_index_template/*" + upgradedVersion + "*");
    final var templateRes = restClient.performRequest(templateReq);
    final var indexTemplates =
        MAPPER.readValue(templateRes.getEntity().getContent().readAllBytes(), JsonNode.class);

    assertThat(indexTemplates.at("/index_templates").size()).isGreaterThan(0);
    assertThat(indexTemplates.at("/index_templates"))
        .allSatisfy(
            node -> {
              final JsonNode composedOfNode = node.at("/index_template/composed_of");
              assertThat(composedOfNode.isArray()).isTrue();

              // Convert ArrayNode to List<String> for easier assertion
              final List<String> stringList = new ArrayList<>();
              final ArrayNode arrayNode = (ArrayNode) composedOfNode;
              for (final JsonNode element : arrayNode) {
                if (element.isTextual()) {
                  stringList.add(element.asText());
                }
              }
              assertThat(stringList).contains("zeebe-record-" + upgradedVersion);
            });
  }
}
