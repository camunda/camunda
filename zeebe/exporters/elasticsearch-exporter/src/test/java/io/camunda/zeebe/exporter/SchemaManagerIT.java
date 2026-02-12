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

import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import io.camunda.zeebe.util.VersionUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
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

  @BeforeAll
  static void setup() {
    CONFIG.url = "http://" + CONTAINER.getHttpHostAddress();
  }

  @Test
  public void shouldUseVersionedComponentTemplateForIndexTemplates() throws IOException {
    // given
    final var esClient = ElasticsearchClientFactory.of(CONFIG);
    final var esExporterClient = new ElasticsearchExporterClient(CONFIG, new SimpleMeterRegistry());

    // when
    // we create an 8.5 component template
    final var schemaManager = new ElasticsearchExporterSchemaManager(esExporterClient, CONFIG);
    schemaManager.createSchema("8.5.0");

    // create the 8.6 component template
    final var upgradedVersion = "8.6.0";
    try (final MockedStatic<VersionUtil> mockedVersionUtil = mockStatic(VersionUtil.class)) {
      mockedVersionUtil.when(VersionUtil::getVersion).thenReturn(upgradedVersion);
      mockedVersionUtil.when(VersionUtil::getVersionLowerCase).thenReturn(upgradedVersion);

      final var schemaManager2 = new ElasticsearchExporterSchemaManager(esExporterClient, CONFIG);
      schemaManager2.createSchema("8.6.0");
    }

    // Use a new schema manager to mock 8.5 broker restart which then triggers schema manager.
    // 8.5 schema manager runs again and attempts to create old component template
    final var schemaManager3 = new ElasticsearchExporterSchemaManager(esExporterClient, CONFIG);
    schemaManager3.createSchema("8.5.0");

    // then
    // 8.6.0 component template exists
    final var componentTemplates =
        esClient
            .cluster()
            .getComponentTemplate(b -> b.name(CONFIG.index.prefix + "*"))
            .componentTemplates();

    assertThat(componentTemplates)
        .anySatisfy(ct -> assertThat(ct.name()).isEqualTo("zeebe-record-" + upgradedVersion));

    // all 8.6.0 index templates are composed of the new index template
    final var indexTemplates =
        esClient
            .indices()
            .getIndexTemplate(b -> b.name("*" + upgradedVersion + "*"))
            .indexTemplates();

    assertThat(indexTemplates).isNotEmpty();
    assertThat(indexTemplates)
        .allSatisfy(
            item ->
                assertThat(item.indexTemplate().composedOf())
                    .contains("zeebe-record-" + upgradedVersion));
  }
}
