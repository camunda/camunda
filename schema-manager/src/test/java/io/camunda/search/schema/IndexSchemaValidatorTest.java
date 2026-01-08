/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import static io.camunda.search.schema.utils.SchemaTestUtil.createTestIndexDescriptor;
import static io.camunda.search.schema.utils.SchemaTestUtil.createTestTemplateDescriptor;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.schema.exceptions.IndexSchemaValidationException;
import io.camunda.search.test.utils.TestObjectMapper;
import java.io.IOException;
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
    final var index = createTestIndexDescriptor("index_name", "/mappings-added-property.json");
    final var currentIndices =
        Map.of(
            index.getFullQualifiedName(),
            jsonToIndexMappingProperties("/mappings.json", index.getFullQualifiedName()));

    // when
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
  public void shouldDetectAnAddedIndexPropertyOnTwoIndicesWithMissingField() throws IOException {
    // given
    final var index = createTestIndexDescriptor("qualified_name", "/mappings-added-property.json");
    final var fullQualifiedName = index.getFullQualifiedName();
    // a schema with two indices that has a missing field
    final var currentIndices =
        Map.of(
            fullQualifiedName,
            jsonToIndexMappingProperties("/mappings.json", fullQualifiedName),
            fullQualifiedName + "_2",
            jsonToIndexMappingProperties("/mappings.json", fullQualifiedName + "_2"));

    // when
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
    final var index = createTestIndexDescriptor("index_name", "/mappings.json");

    // then
    final var difference = VALIDATOR.validateIndexMappings(currentIndices, Set.of(index));

    assertThat(difference).isEmpty();
  }

  @Test
  void shouldIgnoreNotCreatedIndicesFromValidation() {

    // given, when, then
    final var index = createTestIndexDescriptor("index_name", "/mappings.json");
    final var difference = VALIDATOR.validateIndexMappings(Map.of(), Set.of(index));

    assertThat(difference).isEmpty();
  }

  @Test
  public void shouldDetectAmbiguousIndexDifference() throws IOException {
    // given
    final var currentIndex = createTestIndexDescriptor("index_name", "/mappings.json");
    final var qualifiedName = currentIndex.getFullQualifiedName();
    final var currentIndices =
        Map.of(
            qualifiedName,
            jsonToIndexMappingProperties("/mappings-deleted-property.json", qualifiedName),
            qualifiedName + "_2",
            jsonToIndexMappingProperties(
                "/mappings-deleted-different-property.json", qualifiedName + "_2"));

    // when
    // then
    assertThatThrownBy(() -> VALIDATOR.validateIndexMappings(currentIndices, Set.of(currentIndex)))
        .isInstanceOf(IndexSchemaValidationException.class)
        .hasMessageContaining("Ambiguous schema update.");
  }

  @Test
  public void shouldDetectAmbiguousIndexDifferenceCaseWithDynamicProperties() throws IOException {
    // given
    final var indexMapping =
        createTestIndexDescriptor("index_name", "/mappings-dynamic-property.json");
    final String fullQualifiedName = indexMapping.getFullQualifiedName();
    final var currentIndices =
        Map.of(
            fullQualifiedName,
            jsonToIndexMappingProperties(
                "/mappings-dynamic-property-properties.json", fullQualifiedName),
            fullQualifiedName + "_2",
            jsonToIndexMappingProperties(
                "/mappings-dynamic-property-properties-deleted.json", fullQualifiedName + "_2"));

    // when
    // then
    assertThatThrownBy(() -> VALIDATOR.validateIndexMappings(currentIndices, Set.of(indexMapping)))
        .isInstanceOf(IndexSchemaValidationException.class)
        .hasMessageContaining("Ambiguous schema update.");
  }

  @Test
  public void shouldSkipIndexDifferenceWhenRelatesToDynamicProperty() throws IOException {
    // given
    final var indexMapping =
        createTestIndexDescriptor("index_name", "/mappings-dynamic-property-added.json");
    final String fullQualifiedName = indexMapping.getFullQualifiedName();
    final var currentIndices =
        Map.of(
            fullQualifiedName,
            jsonToIndexMappingProperties("/mappings-dynamic-property.json", fullQualifiedName),
            fullQualifiedName + "_2",
            jsonToIndexMappingProperties(
                "/mappings-dynamic-property-properties.json", fullQualifiedName + "_2"));

    // when
    final var actual = VALIDATOR.validateIndexMappings(currentIndices, Set.of(indexMapping));

    // then
    assertThat(actual).hasSize(1);
    assertThat(actual)
        .containsValue(
            Set.of(
                new IndexMappingProperty.Builder()
                    .name("foo")
                    .typeDefinition(Map.of("type", "keyword"))
                    .build()));
  }

  @Test
  void shouldIgnoreARemovedIndexProperty() throws IOException {
    // given
    final var currentIndices =
        Map.of(
            "qualified_name",
            jsonToIndexMappingProperties("/mappings-added-property.json", "qualified_name"));

    // when
    final var index = createTestIndexDescriptor("index_name", "/mappings.json");

    // then
    final var difference = VALIDATOR.validateIndexMappings(currentIndices, Set.of(index));

    assertThat(difference).isEmpty();
  }

  @Test
  void shouldDetectChangedIndexMappingParameters() throws IOException {
    // given
    final var index =
        createTestIndexDescriptor("index_name", "/mappings-changed-property-invalid.json");
    final var currentIndices =
        Map.of(
            index.getFullQualifiedName(),
            jsonToIndexMappingProperties("/mappings.json", index.getFullQualifiedName()));

    // when
    // then
    assertThatThrownBy(() -> VALIDATOR.validateIndexMappings(currentIndices, Set.of(index)))
        .isInstanceOf(IndexSchemaValidationException.class)
        .hasMessageContaining(
            "Unsupported index changes have been introduced. Data migration is required.");
  }

  @Test
  void shouldDetectIndexTemplateWithAddedProperty() throws IOException {
    // given
    final var indexTemplate =
        createTestTemplateDescriptor("template_name", "/mappings-added-property.json");
    final var currentMappings =
        Map.of(
            indexTemplate.getFullQualifiedName(),
            jsonToIndexMappingProperties("/mappings.json", indexTemplate.getFullQualifiedName()));

    // when
    final var difference = VALIDATOR.validateIndexMappings(currentMappings, Set.of(indexTemplate));

    // then
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
        createTestTemplateDescriptor("template_name", "/mappings-deleted-property.json");

    // then
    final var difference = VALIDATOR.validateIndexMappings(currentMappings, Set.of(indexTemplate));

    assertThat(difference).isEmpty();
  }

  @Test
  void shouldIgnoreOrderInLists() throws IOException {
    // given
    final var currentMappings =
        Map.of(
            "qualified_name",
            jsonToIndexMappingProperties("/mappings-with-list-1.json", "qualified_name"));

    // when
    final var index = createTestIndexDescriptor("index_name", "/mappings-with-list-2.json");

    // then
    final var difference = VALIDATOR.validateIndexMappings(currentMappings, Set.of(index));

    assertThat(difference).isEmpty();
  }

  @Test
  void shouldIgnoreMetaProperty() throws IOException {
    // given
    final var mappings = jsonToIndexMappingProperties("/mappings.json", "qualified_name");

    // when
    final var index = createTestIndexDescriptor("qualified_name", "/mappings.json");

    final var currentMappings =
        Map.of(
            index.getFullQualifiedName(),
            new IndexMapping.Builder()
                .indexName(index.getFullQualifiedName())
                .properties(mappings.properties())
                .dynamic("strict")
                .metaProperties(Map.of("meta_key", "meta_value"))
                .build());
    // then
    final var difference = VALIDATOR.validateIndexMappings(currentMappings, Set.of(index));

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
