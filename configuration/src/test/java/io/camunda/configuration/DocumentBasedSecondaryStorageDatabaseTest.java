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

import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockEnvironment;

class DocumentBasedSecondaryStorageDatabaseTest {

  private static final String LEGACY_EXPORTER_ARGS_CREATE_SCHEMA_PROPERTY =
      "zeebe.broker.exporters.camundaexporter.args.createSchema";
  private static final String LEGACY_SCHEMA_MANAGER_CREATE_SCHEMA_PROPERTY =
      "camunda.database.schema-manager.create-schema";
  private static final String NEW_CREATE_SCHEMA_PROPERTY =
      "camunda.data.secondary-storage.elasticsearch.create-schema";
  private static final String LEGACY_OPERATE_HEALTH_CHECK_ENABLED_PROPERTY =
      "camunda.operate.elasticsearch.healthCheckEnabled";
  private static final String LEGACY_TASKLIST_HEALTH_CHECK_ENABLED_PROPERTY =
      "camunda.tasklist.elasticsearch.healthCheckEnabled";
  private static final String LEGACY_SCHEMA_MANAGER_HEALTH_CHECK_ENABLED_PROPERTY =
      "camunda.database.schema-manager.health-check-enabled";
  private static final String NEW_HEALTH_CHECK_ENABLED_PROPERTY =
      "camunda.data.secondary-storage.elasticsearch.health-check-enabled";

  private MockEnvironment mockEnvironment;
  private Elasticsearch elasticsearch;
  private Opensearch opensearch;

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
    opensearch = new Opensearch();
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

  @Test
  void shouldEnableHealthCheckByDefault() {
    // then - the cluster health of a freshly created schema is worth verifying, so an operator has
    // to opt out rather than opt in
    assertThat(elasticsearch.isHealthCheckEnabled()).isTrue();
  }

  @Test
  void shouldUseNewValueWhenNoLegacyHealthCheckEnabledPropertyIsSet() {
    // given
    elasticsearch.setHealthCheckEnabled(false);

    // then
    assertThat(elasticsearch.isHealthCheckEnabled()).isFalse();
  }

  @Test
  void shouldFallBackToLegacyOperateHealthCheckEnabledProperty() {
    // given - the opt-out a deployment without the 'monitor' cluster privilege already has set
    mockEnvironment.setProperty(LEGACY_OPERATE_HEALTH_CHECK_ENABLED_PROPERTY, "false");

    // then
    assertThat(elasticsearch.isHealthCheckEnabled()).isFalse();
  }

  @Test
  void shouldFallBackToLegacyTasklistHealthCheckEnabledProperty() {
    // given
    mockEnvironment.setProperty(LEGACY_TASKLIST_HEALTH_CHECK_ENABLED_PROPERTY, "false");

    // then
    assertThat(elasticsearch.isHealthCheckEnabled()).isFalse();
  }

  @Test
  void shouldFallBackToLegacySchemaManagerHealthCheckEnabledProperty() {
    // given
    mockEnvironment.setProperty(LEGACY_SCHEMA_MANAGER_HEALTH_CHECK_ENABLED_PROPERTY, "false");

    // then
    assertThat(elasticsearch.isHealthCheckEnabled()).isFalse();
  }

  @Test
  void shouldFallBackToTheLegacyPropertyOfItsOwnDatabase() {
    // given - only the OpenSearch opt-out is set
    mockEnvironment.setProperty("camunda.tasklist.opensearch.healthCheckEnabled", "false");

    // then - Elasticsearch does not read another database's legacy property
    assertThat(opensearch.isHealthCheckEnabled()).isFalse();
    assertThat(elasticsearch.isHealthCheckEnabled()).isTrue();
  }

  @Test
  void shouldFallBackWhicheverWayTasklistSpellsOpenSearch() {
    // given - Tasklist binds its OpenSearch block from a field named 'openSearch', so a deployment
    // may well have the key spelled that way rather than as the 'opensearch' the fallback declares.
    // A real Spring Boot environment has ConfigurationPropertySources attached, which is what makes
    // the two spellings the same property.
    final var environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "legacy", Map.of("camunda.tasklist.openSearch.healthCheckEnabled", "false")));
    ConfigurationPropertySources.attach(environment);
    UnifiedConfigurationHelper.setCustomEnvironment(environment);

    // then
    assertThat(opensearch.isHealthCheckEnabled()).isFalse();
  }

  @Test
  void shouldPreferNewValueWhenBothNewAndLegacyHealthCheckEnabledPropertiesArePresent() {
    // given
    elasticsearch.setHealthCheckEnabled(true);
    mockEnvironment.setProperty(NEW_HEALTH_CHECK_ENABLED_PROPERTY, "true");
    mockEnvironment.setProperty(LEGACY_OPERATE_HEALTH_CHECK_ENABLED_PROPERTY, "false");

    // then
    assertThat(elasticsearch.isHealthCheckEnabled()).isTrue();
  }

  @Test
  void shouldThrowWhenLegacyHealthCheckEnabledPropertiesConflict() {
    // given - Operate and Tasklist disagree, and silently picking one of them would leave the
    // startup gate behaving the opposite way to what half the configuration asks for
    mockEnvironment.setProperty(LEGACY_OPERATE_HEALTH_CHECK_ENABLED_PROPERTY, "false");
    mockEnvironment.setProperty(LEGACY_TASKLIST_HEALTH_CHECK_ENABLED_PROPERTY, "true");

    // then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(elasticsearch::isHealthCheckEnabled)
        .withMessageContaining("Ambiguous legacy configuration");
  }
}
