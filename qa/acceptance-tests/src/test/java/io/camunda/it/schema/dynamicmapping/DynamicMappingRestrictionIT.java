/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.schema.dynamicmapping;

import static io.camunda.webapps.schema.SupportedVersions.SUPPORTED_ELASTICSEARCH_VERSION;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.qa.util.cluster.TestStandaloneSchemaManager;
import io.camunda.zeebe.exporter.ElasticsearchExporter;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@ZeebeIntegration
@Testcontainers
public class DynamicMappingRestrictionIT {

  private static final String INDEX_PREFIX = "dynamic-mapping-check";

  @TestZeebe(autoStart = false)
  private final TestStandaloneSchemaManager schemaManager =
      new TestStandaloneSchemaManager()
          .withProperty(
              "zeebe.broker.exporters.elasticsearch.class-name",
              ElasticsearchExporter.class.getName())
          .withProperty("camunda.data.secondary-storage.type", "elasticsearch")
          .withProperty("camunda.data.secondary-storage.elasticsearch.index-prefix", INDEX_PREFIX);

  @Container
  private final ElasticsearchContainer es =
      new ElasticsearchContainer(
              DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch")
                  .withTag(SUPPORTED_ELASTICSEARCH_VERSION))
          .withStartupTimeout(Duration.ofMinutes(5))
          .withStartupAttempts(3)
          .withEnv("xpack.security.enabled", "false");

  private final SoftAssertions dynamicMappingSoftAssertions = new SoftAssertions();

  private String esUrl;

  @BeforeEach
  void setup() {
    esUrl = "http://" + es.getHttpHostAddress();
    schemaManager
        .withProperty("camunda.data.secondary-storage.elasticsearch.url", esUrl)
        .withProperty("zeebe.broker.exporters.elasticsearch.args.url", esUrl);
  }

  @Test
  void shouldCreateNoIndexWithDynamicMapping() {
    // Given
    schemaManager.start();

    // When
    final var indexMappings = new RestTemplate().getForObject(esUrl + "/_mapping", JsonNode.class);

    // Then
    assertThat(indexMappings).as("Index mappings should not be null").isNotNull();
    extractIndexMappings(indexMappings).forEach(this::assertThatIndexMappingHasNoDynamicMapping);

    dynamicMappingSoftAssertions.assertAll();
  }

  private Map<String, JsonNode> extractIndexMappings(final JsonNode indexMappings) {
    final Map<String, JsonNode> indexNameToDefinition =
        indexMappings.properties().stream()
            .filter(entry -> entry.getKey().startsWith(INDEX_PREFIX))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    assertThat(indexNameToDefinition).as("Index definitions should not be empty").isNotEmpty();
    return indexNameToDefinition;
  }

  private void assertThatIndexMappingHasNoDynamicMapping(
      final String indexName, final JsonNode indexDefinition) {
    final JsonNode indexMappings = indexDefinition.get("mappings");
    assertNoDynamicMappingOnIndexLevel(indexName, indexMappings);

    final JsonNode indexProperties = indexMappings.get("properties");
    assertThat(indexProperties)
        .as("Index '%s' should have properties defined", indexName)
        .isNotNull();
    indexProperties.forEachEntry(
        (propertyName, propertyMapping) ->
            assertNoDynamicMappingOnProperty(indexName, propertyName, propertyMapping));
  }

  private void assertNoDynamicMappingOnIndexLevel(
      final String indexName, final JsonNode indexMappings) {
    assertThat(indexMappings).as("Index '%s' should have mappings defined", indexName).isNotNull();
    final JsonNode dynamic = indexMappings.get("dynamic");
    if (dynamic != null) {
      dynamicMappingSoftAssertions
          .assertThat(dynamic.asText())
          .as("Index '%s' should not have the 'dynamic' property set to 'true'", indexName)
          .isNotEqualTo("true");
    }
  }

  private void assertNoDynamicMappingOnProperty(
      final String indexName, final String propertyName, final JsonNode propertyDefinitions) {
    assertThat(propertyDefinitions)
        .as("%s:%s should have non-null definitions", indexName, propertyName)
        .isNotNull();
    if (!propertyDefinitions.isObject()) {
      return;
    }
    final JsonNode dynamic = propertyDefinitions.get("dynamic");
    if (dynamic != null) {
      dynamicMappingSoftAssertions
          .assertThat(dynamic.asText())
          .as(
              "Property %s:%s should not have the 'dynamic' property set to 'true'",
              indexName, propertyName)
          .isNotEqualTo("true");
    }
    final var propertyProperties = propertyDefinitions.get("properties");
    if (propertyProperties == null) {
      return;
    }
    propertyProperties.forEachEntry(
        (subPropertyName, subPropertyDefinitions) ->
            assertNoDynamicMappingOnProperty(
                indexName, propertyName + "." + subPropertyName, subPropertyDefinitions));
  }
}
