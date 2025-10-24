/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema.utils;

import static java.lang.Boolean.parseBoolean;
import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Maps;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.schema.IndexMappingDifference.OrderInsensitiveEquivalence;
import io.camunda.search.schema.SchemaManager;
import io.camunda.search.schema.SearchEngineClient;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.search.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.search.schema.opensearch.OpensearchEngineClient;
import io.camunda.search.test.utils.TestObjectMapper;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public final class SchemaTestUtil {

  private SchemaTestUtil() {}

  public static TestTemplateDescriptor createTestTemplateDescriptor(
      final String indexName, final String mappingsFileName) {
    return new TestTemplateDescriptor(indexName, mappingsFileName);
  }

  public static TestIndexDescriptor createTestIndexDescriptor(
      final String indexName, final String mappingsFileName) {
    return new TestIndexDescriptor(indexName, mappingsFileName);
  }

  @SuppressWarnings("unchecked")
  public static void validateMappings(final TypeMapping mapping, final String fileName)
      throws IOException {
    final var propertiesMap = getFileProperties(fileName);

    assertThat(mapping.properties().size()).isEqualTo(propertiesMap.size());
    propertiesMap.forEach(
        (key, value) ->
            assertThat(mapping.properties().get(key)._kind().jsonValue())
                .isEqualTo(value.get("type")));
  }

  public static void validateMappings(
      final org.opensearch.client.opensearch._types.mapping.TypeMapping mapping,
      final String fileName)
      throws IOException {

    final var propertiesMap = getFileProperties(fileName);

    assertThat(mapping.properties().size()).isEqualTo(propertiesMap.size());
    propertiesMap.forEach(
        (key, value) ->
            assertThat(mapping.properties().get(key)._kind().jsonValue())
                .isEqualTo(value.get("type")));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Map<String, Object>> getFileProperties(final String fileName)
      throws IOException {
    try (final var expectedMappings = SchemaTestUtil.class.getResourceAsStream(fileName)) {
      final var jsonMap =
          TestObjectMapper.objectMapper()
              .readValue(
                  expectedMappings, new TypeReference<Map<String, Map<String, Object>>>() {});
      return (Map<String, Map<String, Object>>) jsonMap.get("mappings").get("properties");
    }
  }

  public static boolean mappingsMatch(final JsonNode mappings, final String fileName)
      throws IOException {
    try (final var expectedMappingsJson = SchemaTestUtil.class.getResourceAsStream(fileName)) {
      final var expectedMappingsTree =
          TestObjectMapper.objectMapper().readTree(expectedMappingsJson);
      return mappings.equals(expectedMappingsTree.get("mappings"));
    }
  }

  public static void assertMappingsMatch(
      final JsonNode mappings, final IndexDescriptor indexDescriptor) {
    try (final var expectedMappingsJson =
        SchemaTestUtil.class.getResourceAsStream(indexDescriptor.getMappingsClasspathFilename())) {
      final var expectedMappingsTree =
          TestObjectMapper.objectMapper()
              .convertValue(
                  TestObjectMapper.objectMapper().readTree(expectedMappingsJson).get("mappings"),
                  Map.class);
      final var actualMappingsTree =
          TestObjectMapper.objectMapper().convertValue(mappings, Map.class);

      // remove _meta for comparison as it is not part of the mapping validation as the diff
      // is based only on the mapping properties
      actualMappingsTree.remove("_meta");
      expectedMappingsTree.remove("_meta");

      if (parseBoolean(actualMappingsTree.getOrDefault("dynamic", "false").toString())
          && parseBoolean(expectedMappingsTree.getOrDefault("dynamic", "false").toString())) {
        // if dynamic is true, skip mappings validation
        return;
      }
      final var difference =
          Maps.difference(
              actualMappingsTree, expectedMappingsTree, OrderInsensitiveEquivalence.equals());
      assertThat(difference.areEqual())
          .isTrue()
          .withFailMessage("Mappings did not match: %s", difference);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static SchemaManager createSchemaManager(
      final Collection<IndexDescriptor> indexDescriptors,
      final Collection<IndexTemplateDescriptor> templateDescriptors,
      final SearchEngineConfiguration config) {
    return new SchemaManager(
        searchEngineClientFromConfig(config),
        indexDescriptors,
        templateDescriptors,
        config,
        TestObjectMapper.objectMapper());
  }

  public static SearchEngineClient searchEngineClientFromConfig(
      final SearchEngineConfiguration config) {
    switch (config.connect().getTypeEnum()) {
      case ELASTICSEARCH -> {
        final var connector = new ElasticsearchConnector(config.connect());
        final var client = connector.createClient();
        final var objectMapper = connector.objectMapper();
        return new ElasticsearchEngineClient(client, objectMapper);
      }
      case OPENSEARCH -> {
        final var connector = new OpensearchConnector(config.connect());
        final var client = connector.createClient();
        final var objectMapper = connector.objectMapper();
        return new OpensearchEngineClient(client, objectMapper);
      }
      default ->
          throw new IllegalArgumentException(
              "Unknown connection type: " + config.connect().getTypeEnum());
    }
  }
}
