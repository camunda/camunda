/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import static io.camunda.search.schema.utils.SchemaTestUtil.createSchemaManager;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.search.schema.utils.SchemaManagerITInvocationProvider;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.http.HttpHost;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ExtendWith(SchemaManagerITInvocationProvider.class)
public class DynamicMappingRestrictionIT {

  private final SoftAssertions dynamicMappingSoftAssertions = new SoftAssertions();

  @TestTemplate
  void shouldCreateNoIndexWithDynamicMapping(
      final SearchEngineConfiguration config, final SearchClientAdapter ignored) {
    // Given
    final var indexDescriptors =
        new IndexDescriptors(
            config.connect().getIndexPrefix(), config.connect().getTypeEnum().isElasticSearch());

    try (final SchemaManager schemaManager =
        createSchemaManager(indexDescriptors.indices(), indexDescriptors.templates(), config)) {

      schemaManager.startup();

      assertThat(schemaManager.isSchemaReadyForUse()).isTrue();

      // When
      final JsonNode indexMappings = getIndexMappings(config.connect().getUrl());

      // Then
      assertThat(indexMappings).as("Index mappings should not be null").isNotNull();
      extractCamundaIndices(indexMappings, config.connect().getIndexPrefix())
          .forEach(this::assertThatIndexMappingHasNoDynamicMapping);

      dynamicMappingSoftAssertions.assertAll();
    }
  }

  private JsonNode getIndexMappings(final String storageUrl) {
    try (final HttpClient client = HttpClient.newHttpClient()) {
      final HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(HttpHost.create(storageUrl) + "/_mapping"))
              .GET()
              .build();

      final HttpResponse<String> response =
          client.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new IOException("Failed to retrieve mappings. Status: " + response.statusCode());
      }

      return new ObjectMapper().readTree(response.body());
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  private Map<String, JsonNode> extractCamundaIndices(
      final JsonNode indexMappings, final String indexPrefix) {
    final Map<String, JsonNode> indexNameToDefinition =
        indexMappings.properties().stream()
            .filter(entry -> entry.getKey().startsWith(indexPrefix))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    assertThat(indexNameToDefinition).as("Camunda indices should exist").isNotEmpty();
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
        (fieldName, fieldMapping) ->
            assertNoDynamicMappingOnField(indexName, fieldName, fieldMapping));
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

  private void assertNoDynamicMappingOnField(
      final String indexName, final String fieldName, final JsonNode fieldDefinitions) {
    assertThat(fieldDefinitions)
        .as("Field %s:%s should have non-null definitions", indexName, fieldName)
        .isNotNull();
    if (!fieldDefinitions.isObject()) {
      return;
    }
    final JsonNode dynamic = fieldDefinitions.get("dynamic");
    if (dynamic != null) {
      dynamicMappingSoftAssertions
          .assertThat(dynamic.asText())
          .as(
              "Field %s:%s should not have the 'dynamic' property set to 'true'",
              indexName, fieldName)
          .isNotEqualTo("true");
    }
    final var fieldProperties = fieldDefinitions.get("properties");
    if (fieldProperties == null) {
      return;
    }
    fieldProperties.forEachEntry(
        (subFieldName, subFieldDefinitions) ->
            assertNoDynamicMappingOnField(
                indexName, fieldName + "." + subFieldName, subFieldDefinitions));
  }
}
