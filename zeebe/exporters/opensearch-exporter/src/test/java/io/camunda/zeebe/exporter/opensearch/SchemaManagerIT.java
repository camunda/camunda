/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.opensearch;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import io.camunda.zeebe.util.VersionUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.opensearch.client.Request;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The OS exporter does not have a schema manager abstraction, all actions are done using the
 * Opensearch client in the camunda exporter, this is why the test does not have a schema manager
 * class.
 */
@Testcontainers
public class SchemaManagerIT {
  @Container
  private static final OpensearchContainer<?> CONTAINER =
      TestSearchContainers.createDefaultOpensearchContainer()
          .withEnv("action.destructive_requires_name", "false");

  private static final OpensearchExporterConfiguration CONFIG =
      new OpensearchExporterConfiguration();

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @BeforeAll
  static void setup() {
    CONFIG.url = CONTAINER.getHttpHostAddress();
  }

  @Test
  public void shouldUseVersionedComponentTemplateForIndexTemplates() throws IOException {
    // given
    final var restClient = RestClientFactory.of(CONFIG);
    final var osClient = new OpensearchClient(CONFIG, new SimpleMeterRegistry());

    // when
    // we create an 8.5 component template
    osClient.putComponentTemplate();
    osClient.putIndexTemplate(ValueType.PROCESS_INSTANCE);

    // create the 8.6 component template
    final var upgradedVersion = "8.6.0";
    try (final MockedStatic<VersionUtil> mockedVersionUtil = mockStatic(VersionUtil.class)) {
      mockedVersionUtil.when(VersionUtil::getVersion).thenReturn(upgradedVersion);
      mockedVersionUtil.when(VersionUtil::getVersionLowerCase).thenReturn(upgradedVersion);

      osClient.putComponentTemplate();
      osClient.putIndexTemplate(ValueType.PROCESS_INSTANCE);
    }

    // Use a new schema manager to mock 8.5 broker restart which then triggers schema manager.
    // 8.5 schema manager runs again and attempts to create old component template
    osClient.putComponentTemplate();
    osClient.putIndexTemplate(ValueType.PROCESS_INSTANCE);

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
