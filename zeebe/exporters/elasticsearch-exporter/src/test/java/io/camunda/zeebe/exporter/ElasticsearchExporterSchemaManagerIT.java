/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
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

  @BeforeAll
  static void setup() {
    CONFIG.url = "http://" + CONTAINER.getHttpHostAddress();
  }

  @Test
  public void shouldNotOverwriteNewerComponentTemplate() {
    // broker 1 schema manager
    final var schemaManager = new ElasticsearchExporterSchemaManager(CONFIG);
    schemaManager.createSchema("8.5.0");

    final var schemaManager2 = new ElasticsearchExporterSchemaManager(CONFIG);
    schemaManager2.createSchema("8.6.0");

    schemaManager.createSchema("8.5.0");
  }
}
