/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.model;

import static java.util.function.Predicate.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springaicommunity.mcp.method.tool.utils.JsonSchemaGenerator;

/**
 * Verifies that custom MCP model classes only expose their intended properties in the generated
 * JSON schema. This prevents accidentally introducing additional fields into the MCP-facing schema,
 * which would otherwise become available to MCP tools.
 */
public class CustomMcpModelPropertiesTest {

  static final ObjectMapper MAPPER = new ObjectMapper();

  static Stream<Arguments> modelsWithExpectedFields() {
    return Stream.of(
        Arguments.argumentSet(
            "McpIncidentFilter",
            McpIncidentFilter.class,
            Set.of(
                "creationTime",
                "elementId",
                "errorType",
                "processDefinitionId",
                "processDefinitionKey",
                "processInstanceKey",
                "state")),
        Arguments.argumentSet(
            "McpProcessDefinitionFilter",
            McpProcessDefinitionFilter.class,
            Set.of(
                "hasStartForm",
                "isLatestVersion",
                "name",
                "processDefinitionId",
                "processDefinitionKey",
                "resourceName",
                "version",
                "versionTag")),
        Arguments.argumentSet(
            "McpProcessInstanceFilter",
            McpProcessInstanceFilter.class,
            Set.of(
                "endDate",
                "hasIncident",
                "processDefinitionId",
                "processDefinitionKey",
                "processDefinitionName",
                "processDefinitionVersion",
                "processInstanceKey",
                "startDate",
                "state",
                "tags",
                "variables")),
        Arguments.argumentSet(
            "McpVariableFilter",
            McpVariableFilter.class,
            Set.of(
                "isTruncated", "name", "processInstanceKey", "scopeKey", "value", "variableKey")),
        Arguments.argumentSet(
            "McpSearchQueryPageRequest",
            McpSearchQueryPageRequest.class,
            Set.of("after", "before", "from", "limit")),
        Arguments.argumentSet(
            "McpUserTaskFilter",
            McpUserTaskFilter.class,
            Set.of(
                "assignee",
                "completionDate",
                "creationDate",
                "dueDate",
                "elementId",
                "elementInstanceKey",
                "followUpDate",
                "localVariables",
                "name",
                "priority",
                "processDefinitionId",
                "processDefinitionKey",
                "processInstanceKey",
                "processInstanceVariables",
                "state",
                "userTaskKey",
                "tags")),
        Arguments.argumentSet(
            "McpUserTaskAssignmentRequest",
            McpUserTaskAssignmentRequest.class,
            Set.of("action", "allowOverride")),
        Arguments.argumentSet(
            "McpProcessInstanceCreationInstruction",
            McpProcessInstanceCreationInstruction.class,
            Set.of(
                "awaitCompletion",
                "fetchVariables",
                "processDefinitionId",
                "processDefinitionKey",
                "processDefinitionVersion",
                "requestTimeout",
                "tags",
                "tenantId",
                "variables",
                "businessId")));
  }

  @ParameterizedTest
  @MethodSource("modelsWithExpectedFields")
  void shouldOnlyIncludeExpectedSchemaFields(
      final Class<?> modelClass, final Set<String> expectedFields) {
    final var properties = getProperties(modelClass);
    assertThat(properties)
        .withFailMessage(
            () ->
                """
                Unexpected properties %s detected for '%s'.
                Add a '@JsonIgnore' annotation for the properties to that model class to avoid exposing them to MCP tools.
                Alternatively, add the newly expected properties to this test case.
                """
                    .formatted(
                        properties.stream().filter(not(expectedFields::contains)).toList(),
                        modelClass.getName()))
        .containsExactlyInAnyOrderElementsOf(expectedFields);
  }

  @SuppressWarnings("unchecked")
  private Collection<String> getProperties(final Class<?> schemaClass) {
    try {
      final var schema = JsonSchemaGenerator.generateFromClass(schemaClass);
      final Map<String, Object> map = MAPPER.readValue(schema, Map.class);
      assertThat(map)
          .withFailMessage("Generated schema doesn't contain properties.")
          .hasFieldOrProperty("properties");
      final var properties = map.get("properties");
      assertThat(properties)
          .withFailMessage("Generated schema properties are not in a map.")
          .isInstanceOf(Map.class);
      return ((Map<String, Object>) properties).keySet();
    } catch (final Exception e) {
      return fail("Generated schema is not a valid JSON.", e);
    }
  }
}
