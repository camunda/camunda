/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DocumentBasedSecondaryStorageDatabaseTest {

  private static final String LEGACY_EXPORTER_ARGS_CREATE_SCHEMA_PROPERTY =
      "zeebe.broker.exporters.camundaexporter.args.createSchema";
  private static final String LEGACY_SCHEMA_MANAGER_CREATE_SCHEMA_PROPERTY =
      "camunda.database.schema-manager.create-schema";
  private static final String NEW_CREATE_SCHEMA_PROPERTY =
      "camunda.data.secondary-storage.elasticsearch.create-schema";

  private MockEnvironment mockEnvironment;
  private Elasticsearch elasticsearch;

  @BeforeAll
  @AfterAll
  static void clearStaticEnvironment() {
    // UnifiedConfigurationHelper keeps its environment in a static field. Clear it so a
    // previous/subsequent Spring-based test in the same JVM doesn't leak its environment here
    // (or vice versa).
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  @BeforeEach
  void setup() {
    mockEnvironment = new MockEnvironment();
    UnifiedConfigurationHelper.setCustomEnvironment(mockEnvironment);
    elasticsearch = new Elasticsearch();
  }

  @Test
  void shouldUseNewValueWhenNoLegacyCreateSchemaPropertyIsSet() {
    // given
    elasticsearch.setCreateSchema(false);

    // then
    assertThat(elasticsearch.isCreateSchema()).isFalse();
  }

  @Test
  void shouldFallBackToLegacyExporterArgsCreateSchemaProperty() {
    // given
    mockEnvironment.setProperty(LEGACY_EXPORTER_ARGS_CREATE_SCHEMA_PROPERTY, "false");

    // then
    assertThat(elasticsearch.isCreateSchema()).isFalse();
  }

  @Test
  void shouldFallBackToLegacySchemaManagerCreateSchemaProperty() {
    // given
    mockEnvironment.setProperty(LEGACY_SCHEMA_MANAGER_CREATE_SCHEMA_PROPERTY, "false");

    // then
    assertThat(elasticsearch.isCreateSchema()).isFalse();
  }

  @Test
  void shouldPreferNewValueWhenBothNewAndLegacyPropertiesArePresent() {
    // given
    elasticsearch.setCreateSchema(false);
    mockEnvironment.setProperty(NEW_CREATE_SCHEMA_PROPERTY, "false");
    mockEnvironment.setProperty(LEGACY_SCHEMA_MANAGER_CREATE_SCHEMA_PROPERTY, "true");

    // then
    assertThat(elasticsearch.isCreateSchema()).isFalse();
  }

  @Test
  void shouldThrowWhenLegacyCreateSchemaPropertiesConflict() {
    // given
    mockEnvironment.setProperty(LEGACY_EXPORTER_ARGS_CREATE_SCHEMA_PROPERTY, "true");
    mockEnvironment.setProperty(LEGACY_SCHEMA_MANAGER_CREATE_SCHEMA_PROPERTY, "false");

    // then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(elasticsearch::isCreateSchema)
        .withMessageContaining("Ambiguous legacy configuration");
  }
}
