/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.utils.TestObjectMapper;
import io.camunda.search.schema.IndexMapping;
import io.camunda.search.schema.IndexMappingProperty;
import io.camunda.search.schema.IndexSchemaValidator;
import io.camunda.search.schema.exceptions.IndexSchemaValidationException;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

public class IndexSchemaValidatorTest {

  private static final ObjectMapper MAPPER = TestObjectMapper.objectMapper();

  private static final IndexSchemaValidator VALIDATOR =
      new IndexSchemaValidator(TestObjectMapper.objectMapper());

  @Test
  void shouldDetectIndexWithAddedProperty() throws IOException {
    // given
    final var currentIndices =
        Map.of("qualified_name", jsonToIndexMappingProperties("/mappings.json", "qualified_name"));

    // when
    final var index =
        SchemaTestUtil.mockIndex(
            "qualified_name", "alias", "index_name", "/mappings-added-property.json");

    // then
    final var difference = VALIDATOR.validateIndexMappings(currentIndices, Set.of(index));

    assertThat(difference)
        .containsExactly(
            entry(
                index,
                Set.of(
                    new IndexMappingProperty.Builder()
                        .name("foo")
                        .typeDefinition(Map.of("type", "text"))
                        .build())));
  }

  @Test
  public void shouldDetectAnAddedIndexPropertyOnTwoIndicesWithMissingField() throws IOException {
    // given
    // a schema with two indices that has a missing field
    final var currentIndices =
        Map.of(
            "qualified_name",
            jsonToIndexMappingProperties("/mappings.json", "qualified_name"),
            "qualified_name_2",
            jsonToIndexMappingProperties("/mappings.json", "qualified_name_2"));

    // when
    final var index =
        SchemaTestUtil.mockIndex(
            "qualified_name", "aliasx", "qualified_name", "/mappings-added-property.json");

    final var difference = VALIDATOR.validateIndexMappings(currentIndices, Set.of(index));

    // then
    assertThat(difference)
        .containsExactly(
            entry(
                index,
                Set.of(
                    new IndexMappingProperty.Builder()
                        .name("foo")
                        .typeDefinition(Map.of("type", "text"))
                        .build())));
  }

  @Test
  void shouldValidateSameIndexWithNoDifferences() throws IOException {
    // given
    final var currentIndices =
        Map.of("qualified_name", jsonToIndexMappingProperties("/mappings.json", "qualified_name"));

    // when
    final var index =
        SchemaTestUtil.mockIndex("qualified_name", "alias", "index_name", "/mappings.json");

    // then
    final var difference = VALIDATOR.validateIndexMappings(currentIndices, Set.of(index));

    assertThat(difference).isEmpty();
  }

  @Test
  void shouldIgnoreNotCreatedIndicesFromValidation() {

    // given, when, then
    final var index =
        SchemaTestUtil.mockIndex("qualified_name", "alias", "index_name", "/mappings.json");
    final var difference = VALIDATOR.validateIndexMappings(Map.of(), Set.of(index));

    assertThat(difference).isEmpty();
  }

  @Test
  public void shouldDetectAmbiguousIndexDifference() throws IOException {
    // given
    final var currentIndices =
        Map.of(
            "qualified_name",
            jsonToIndexMappingProperties("/mappings-deleted-property.json", "qualified_name"),
            "qualified_name_2",
            jsonToIndexMappingProperties(
                "/mappings-deleted-different-property.json", "qualified_name_2"));

    // when
    final var currentIndex =
        SchemaTestUtil.mockIndex("qualified_name", "alias3", "index_name", "/mappings.json");

    // then
    assertThatThrownBy(() -> VALIDATOR.validateIndexMappings(currentIndices, Set.of(currentIndex)))
        .isInstanceOf(IndexSchemaValidationException.class)
        .hasMessageContaining("Ambiguous schema update.");
  }

  @Test
  void shouldIgnoreARemovedIndexProperty() throws IOException {
    // given
    final var currentIndices =
        Map.of(
            "qualified_name",
            jsonToIndexMappingProperties("/mappings-added-property.json", "qualified_name"));

    // when
    final var index =
        SchemaTestUtil.mockIndex("qualified_name", "alias", "index_name", "/mappings.json");

    // then
    final var difference = VALIDATOR.validateIndexMappings(currentIndices, Set.of(index));

    assertThat(difference).isEmpty();
  }

  @Test
  void shouldDetectChangedIndexMappingParameters() throws IOException {
    // given
    final var currentIndices =
        Map.of("qualified_name", jsonToIndexMappingProperties("/mappings.json", "qualified_name"));

    // when
    final var index =
        SchemaTestUtil.mockIndex(
            "qualified_name", "alias", "index_name", "/mappings-changed-property-invalid.json");

    // then
    assertThatThrownBy(() -> VALIDATOR.validateIndexMappings(currentIndices, Set.of(index)))
        .isInstanceOf(IndexSchemaValidationException.class)
        .hasMessageContaining(
            "Not supported index changes are introduced. Data migration is required.");
  }

  @Test
  void shouldDetectIndexTemplateWithAddedProperty() throws IOException {
    // given
    final var currentMappings =
        Map.of("template_name", jsonToIndexMappingProperties("/mappings.json", "template_name"));

    // when
    final var indexTemplate =
        SchemaTestUtil.mockIndexTemplate(
            "index_name",
            "index_name.*",
            "alias",
            Collections.emptyList(),
            "template_name",
            "/mappings-added-property.json");

    // then
    final var difference = VALIDATOR.validateIndexMappings(currentMappings, Set.of(indexTemplate));

    assertThat(difference)
        .containsExactly(
            entry(
                indexTemplate,
                Set.of(
                    new IndexMappingProperty.Builder()
                        .name("foo")
                        .typeDefinition(Map.of("type", "text"))
                        .build())));
  }

  @Test
  void shouldIgnoreARemovedTemplateProperty() throws IOException {
    // given
    final var currentMappings =
        Map.of("template_name", jsonToIndexMappingProperties("/mappings.json", "template_name"));

    // when
    final var indexTemplate =
        SchemaTestUtil.mockIndexTemplate(
            "index_name",
            "index_name.*",
            "alias",
            Collections.emptyList(),
            "template_name",
            "/mappings-deleted-property.json");

    // then
    final var difference = VALIDATOR.validateIndexMappings(currentMappings, Set.of(indexTemplate));

    assertThat(difference).isEmpty();
  }

  @SuppressWarnings("unchecked")
  private IndexMapping jsonToIndexMappingProperties(
      final String mappingsFileName, final String indexName) throws IOException {
    final Map<String, Object> jsonMap =
        MAPPER.readValue(
            getClass().getResourceAsStream(mappingsFileName), new TypeReference<>() {});
    final var propertiesMap =
        ((Map<String, Map<String, Object>>) jsonMap.get("mappings")).get("properties");
    final Set<IndexMappingProperty> properties =
        propertiesMap.entrySet().stream()
            .map(IndexMappingProperty::createIndexMappingProperty)
            .collect(Collectors.toSet());
    return new IndexMapping.Builder()
        .indexName(indexName)
        .properties(properties)
        .dynamic("strict")
        .build();
  }
}
