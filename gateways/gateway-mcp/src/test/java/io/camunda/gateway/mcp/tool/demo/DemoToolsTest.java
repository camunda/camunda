/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.gateway.mcp.config.CamundaJsonSchemaGenerator;
import io.camunda.gateway.mcp.tool.ToolsTest;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

/**
 * Test suite for DemoTools demonstrating @McpToolParams functionality.
 *
 * <p>This test class validates that the @McpToolParams annotation correctly unwraps DTO parameters
 * to root-level schema properties, and that bean validation annotations on DTO fields are properly
 * enforced.
 */
@ContextConfiguration(classes = {DemoTools.class})
class DemoToolsTest extends ToolsTest {

  @Autowired private ObjectMapper objectMapper;

  @Nested
  class CreateTask {

    @Test
    void shouldCreateTaskWithMcpToolParams() {
      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("createTask")
                  .arguments(
                      Map.of(
                          "taskName",
                          "Deploy Application",
                          "priority",
                          "high",
                          "metadata",
                          Map.of("assignee", "John", "dueDate", "2026-02-10"),
                          "urgent",
                          true))
                  .build());

      // then
      if (result.isError()) {
        System.out.println("ERROR: " + result.content());
        if (result.content() != null
            && !result.content().isEmpty()
            && result.content().get(0) instanceof TextContent tc) {
          System.out.println("ERROR TEXT: " + tc.text());
        }
      }
      assertThat(result.isError()).isFalse();
      assertThat(result.content())
          .hasSize(1)
          .first()
          .isInstanceOfSatisfying(
              TextContent.class,
              textContent -> {
                assertThat(textContent.text()).contains("Deploy Application");
                assertThat(textContent.text()).contains("high");
                assertThat(textContent.text()).contains("true");
              });
    }

    @Test
    void shouldFailCreateTaskWhenTaskNameIsBlank() {
      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("createTask")
                  .arguments(
                      Map.of(
                          "taskName", "",
                          "priority", "high",
                          "urgent", false))
                  .build());

      // then
      assertThat(result.isError()).isTrue();
      assertThat(result.structuredContent()).isNull();
      assertThat(result.content())
          .hasSize(1)
          .first()
          .isInstanceOfSatisfying(
              TextContent.class,
              textContent ->
                  assertThat(textContent.text())
                      .containsIgnoringCase("taskName")
                      .containsAnyOf("must not be blank", "blank"));
    }

    @Test
    void shouldFailCreateTaskWhenPriorityIsInvalid() {
      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("createTask")
                  .arguments(
                      Map.of(
                          "taskName", "Test Task",
                          "priority", "super-duper-high",
                          "urgent", false))
                  .build());

      // then
      assertThat(result.isError()).isTrue();
      assertThat(result.structuredContent()).isNull();
      assertThat(result.content())
          .hasSize(1)
          .first()
          .isInstanceOfSatisfying(
              TextContent.class,
              textContent ->
                  assertThat(textContent.text())
                      .containsIgnoringCase("priority")
                      .containsAnyOf("lowercase", "must match", "pattern"));
    }

    @Test
    void shouldCreateTaskWithOptionalMetadata() {
      // when: metadata is optional and not provided
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("createTask")
                  .arguments(
                      Map.of(
                          "taskName", "Quick Task",
                          "priority", "medium",
                          "urgent", false))
                  .build());

      // then
      assertThat(result.isError()).isFalse();
      assertThat(result.content())
          .hasSize(1)
          .first()
          .isInstanceOfSatisfying(
              TextContent.class,
              textContent -> {
                assertThat(textContent.text()).contains("Quick Task");
                assertThat(textContent.text()).contains("medium");
              });
    }

    @Test
    void shouldCreateTaskWithDefaultUrgentValue() {
      // when: urgent is optional (defaults to false in DTO)
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("createTask")
                  .arguments(
                      Map.of(
                          "taskName", "Background Task",
                          "priority", "low"))
                  .build());

      // then
      assertThat(result.isError()).isFalse();
      assertThat(result.content())
          .hasSize(1)
          .first()
          .isInstanceOfSatisfying(
              TextContent.class,
              textContent -> {
                assertThat(textContent.text()).contains("Background Task");
                assertThat(textContent.text()).contains("low");
                assertThat(textContent.text()).contains("false");
              });
    }
  }

  @Nested
  class CreateTaskOldWay {

    @Test
    void shouldCreateTaskUsingTraditionalParameters() {
      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("createTaskOldWay")
                  .arguments(
                      Map.of(
                          "taskName",
                          "Traditional Task",
                          "priority",
                          "high",
                          "metadata",
                          Map.of("key", "value"),
                          "urgent",
                          true))
                  .build());

      // then
      assertThat(result.isError()).isFalse();
      assertThat(result.content())
          .hasSize(1)
          .first()
          .isInstanceOfSatisfying(
              TextContent.class,
              textContent -> {
                assertThat(textContent.text()).contains("Traditional Task");
                assertThat(textContent.text()).contains("high");
                assertThat(textContent.text()).contains("true");
              });
    }
  }

  @Nested
  class SchemaGeneration {

    @Test
    void shouldGenerateFlatSchemaForMcpToolParamsParameter() throws Exception {
      // Given: Method with @McpToolParams parameter
      final Method method = DemoTools.class.getMethod("createTask", CreateTaskRequest.class);

      // When: Generate schema
      final String schemaJson = CamundaJsonSchemaGenerator.generateForMethodInput(method);
      final Map<String, Object> schema = objectMapper.readValue(schemaJson, Map.class);

      // Then: Schema has flat properties (not nested under "request")
      assertThat(schema).containsKey("properties");
      final Map<String, Object> properties = (Map<String, Object>) schema.get("properties");

      // Verify all DTO fields are at root level
      assertThat(properties).containsKeys("taskName", "priority", "metadata", "urgent");

      // Verify NO $defs section (inline schemas)
      assertThat(schema).doesNotContainKey("$defs");
      assertThat(schema).doesNotContainKey("definitions");
    }

    @Test
    void shouldGenerateSamePropertiesForBothApproaches() throws Exception {
      // Given: Two methods with different parameter styles
      final Method newWay = DemoTools.class.getMethod("createTask", CreateTaskRequest.class);
      final Method oldWay =
          DemoTools.class.getMethod(
              "createTaskOldWay", String.class, String.class, Map.class, Boolean.class);

      // When: Generate schemas for both
      final String newWaySchema = CamundaJsonSchemaGenerator.generateForMethodInput(newWay);
      final String oldWaySchema = CamundaJsonSchemaGenerator.generateForMethodInput(oldWay);

      final Map<String, Object> newWayMap = objectMapper.readValue(newWaySchema, Map.class);
      final Map<String, Object> oldWayMap = objectMapper.readValue(oldWaySchema, Map.class);

      // Then: Both should have same root-level properties
      final Map<String, Object> newWayProps = (Map<String, Object>) newWayMap.get("properties");
      final Map<String, Object> oldWayProps = (Map<String, Object>) oldWayMap.get("properties");

      assertThat(newWayProps.keySet())
          .containsExactlyInAnyOrder("taskName", "priority", "metadata", "urgent");
      assertThat(oldWayProps.keySet())
          .containsExactlyInAnyOrder("taskName", "priority", "metadata", "urgent");
    }
  }
}
