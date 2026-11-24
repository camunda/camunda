/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import io.camunda.webapps.schema.entities.variablehistory.VariableHistoryEntity;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The Elasticsearch and OpenSearch templates are the same file content, which {@code
 * SecondaryStorageSchemaConstraintsTest} checks, so this test reads the Elasticsearch one.
 */
class VariableHistoryTemplateTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  // The fields that a search filter, the sort or the cleanup reads. A change of this list changes
  // the stored index, which cannot be undone for an existing index.
  private static final Set<String> INDEXED_FIELDS =
      Set.of(
          "id",
          "variableHistoryItemKey",
          "variableKey",
          "name",
          "scopeKey",
          "processInstanceKey",
          "rootProcessInstanceKey",
          "processDefinitionKey",
          "processDefinitionId",
          "tenantId",
          "timestamp",
          "operationType",
          "variableOperationType",
          "elementInstanceKey",
          "elementId",
          "actorType",
          "actorId",
          "agentElementId");

  private static final Set<String> NOT_INDEXED_FIELDS =
      Set.of("value", "fullValue", "isPreview", "itemIndex");

  private final VariableHistoryTemplate template = new VariableHistoryTemplate("", true);

  @Test
  void shouldMapEveryEntityPropertyAndNothingElse() throws IOException {
    // given
    final var mappedFields = fieldNames(readProperties());

    // when / then
    assertThat(mappedFields)
        .as("strict mappings reject unmapped properties, so entity and mapping must match")
        .isEqualTo(entityProperties());
  }

  @Test
  void shouldIndexTheAgreedFieldsAndLeaveTheOthersUnindexed() throws IOException {
    // given
    final var properties = readProperties();

    // when
    final var notIndexed =
        entityProperties().stream()
            .filter(field -> !properties.path(field).path("index").asBoolean(true))
            .collect(Collectors.toSet());

    // then
    assertThat(notIndexed).isEqualTo(NOT_INDEXED_FIELDS);
    assertThat(entityProperties()).containsExactlyInAnyOrderElementsOf(allAgreedFields());
  }

  @Test
  void shouldKeepTheValuesOutOfTheIndexAndTheDocValues() throws IOException {
    // given
    final var properties = readProperties();

    // when / then
    for (final var field : Set.of("value", "fullValue")) {
      assertThat(properties.path(field).path("type").asText()).isEqualTo("keyword");
      assertThat(properties.path(field).path("index").asBoolean(true)).isFalse();
      assertThat(properties.path(field).path("doc_values").asBoolean(true)).isFalse();
    }
  }

  @Test
  void shouldStoreTheItemKeyAsSortableLong() throws IOException {
    // given
    final var itemKey = readProperties().path("variableHistoryItemKey");

    // when / then
    assertThat(itemKey.path("type").asText()).isEqualTo("long");
    assertThat(itemKey.path("index").asBoolean(true)).isTrue();
    assertThat(itemKey.path("doc_values").asBoolean(true)).isTrue();
  }

  @Test
  void shouldMapTheFieldsTheDescriptorUsesForCleanupAndTenancy() throws IOException {
    // given
    final var properties = readProperties();

    // when / then
    assertThat(properties.has(template.getProcessInstanceDependantField())).isTrue();
    assertThat(properties.has(template.getRootProcessInstanceKeyField())).isTrue();
    assertThat(template.getTenantIdField()).hasValue("tenantId");
    assertThat(properties.has("tenantId")).isTrue();
  }

  private JsonNode readProperties() throws IOException {
    try (final var stream =
        VariableHistoryTemplateTest.class.getResourceAsStream(
            template.getMappingsClasspathFilename())) {
      assertThat(stream).as(template.getMappingsClasspathFilename()).isNotNull();
      return MAPPER.readTree(stream).path("mappings").path("properties");
    }
  }

  private static Set<String> entityProperties() {
    return MAPPER
        .getSerializationConfig()
        .introspect(MAPPER.constructType(VariableHistoryEntity.class))
        .findProperties()
        .stream()
        .map(BeanPropertyDefinition::getName)
        .collect(Collectors.toSet());
  }

  private static Set<String> fieldNames(final JsonNode properties) {
    final var names = new HashSet<String>();
    properties.fieldNames().forEachRemaining(names::add);
    return names;
  }

  private static Set<String> allAgreedFields() {
    final var all = new HashSet<>(INDEXED_FIELDS);
    all.addAll(NOT_INDEXED_FIELDS);
    return all;
  }
}
