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

import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import io.camunda.zeebe.util.VersionUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.cluster.ComponentTemplate;
import org.opensearch.client.opensearch.cluster.GetComponentTemplateRequest;
import org.opensearch.client.opensearch.indices.GetIndexTemplateRequest;
import org.opensearch.client.opensearch.indices.IndexTemplate;
import org.opensearch.client.opensearch.indices.get_index_template.IndexTemplateItem;
import org.opensearch.testcontainers.OpenSearchContainer;
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
  private static final OpenSearchContainer<?> CONTAINER =
      TestSearchContainers.createDefaultOpensearchContainer()
          .withEnv("action.destructive_requires_name", "false");

  private static final OpensearchExporterConfiguration CONFIG =
      new OpensearchExporterConfiguration();

  private static OpenSearchClient openSearchClient;

  @BeforeAll
  static void setup() {
    CONFIG.url = CONTAINER.getHttpHostAddress();
    openSearchClient = OpensearchConnector.of(CONFIG).createClient();
  }

  @Test
  public void shouldUseVersionedComponentTemplateForIndexTemplates() throws IOException {
    // given
    final var osClient = new OpensearchClient(CONFIG, new SimpleMeterRegistry());

    // when
    // we create an 8.5 component template
    final var schemaManager = new OpensearchExporterSchemaManager(osClient, CONFIG);
    schemaManager.createSchema("8.5.0");

    // create the 8.6 component template
    final var upgradedVersion = "8.6.0";
    try (final MockedStatic<VersionUtil> mockedVersionUtil = mockStatic(VersionUtil.class)) {
      mockedVersionUtil.when(VersionUtil::getVersion).thenReturn(upgradedVersion);
      mockedVersionUtil.when(VersionUtil::getVersionLowerCase).thenReturn(upgradedVersion);

      // trigger schema manager for 8.6
      final var schemaManager2 = new OpensearchExporterSchemaManager(osClient, CONFIG);
      schemaManager2.createSchema(upgradedVersion);
    }

    // Use a new schema manager to mock 8.5 broker restart which then triggers schema manager.
    // 8.5 schema manager runs again and attempts to create old component template
    final var schemaManager3 = new OpensearchExporterSchemaManager(osClient, CONFIG);
    schemaManager3.createSchema("8.5.0");

    // then
    // 8.6.0 component template exists
    final List<ComponentTemplate> componentTemplates =
        getComponentTemplates(CONFIG.index.prefix + "*");

    assertThat(componentTemplates)
        .hasSize(2)
        .extracting(ComponentTemplate::name)
        .contains("zeebe-record-" + upgradedVersion);

    // all 8.6.0 index templates are composed of the new index template
    final List<IndexTemplateItem> indexTemplates =
        getIndexTemplateItems("*" + upgradedVersion + "*");
    assertThat(indexTemplates)
        .hasSizeGreaterThan(0)
        .extracting(IndexTemplateItem::indexTemplate)
        .extracting(IndexTemplate::composedOf)
        .containsOnly(List.of("zeebe-record-" + upgradedVersion));
  }

  private List<ComponentTemplate> getComponentTemplates(final String templateName)
      throws IOException {
    return openSearchClient
        .cluster()
        .getComponentTemplate(GetComponentTemplateRequest.builder().name(templateName).build())
        .componentTemplates();
  }

  private List<IndexTemplateItem> getIndexTemplateItems(final String templateName)
      throws IOException {
    return openSearchClient
        .indices()
        .getIndexTemplate(GetIndexTemplateRequest.builder().name(templateName).build())
        .indexTemplates();
  }
}
