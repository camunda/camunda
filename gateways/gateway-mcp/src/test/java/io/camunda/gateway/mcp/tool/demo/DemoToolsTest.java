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
import io.camunda.gateway.mcp.schema.CamundaJsonSchemaGenerator;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests for the demo @McpRequestBody tool. */
class DemoToolsTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldGenerateFlatSchemaForMcpRequestBodyParameter() throws Exception {
    // Given: Method with @McpRequestBody parameter
    Method method = DemoTools.class.getMethod("createTask", CreateTaskRequest.class);

    // When: Generate schema
    String schemaJson = CamundaJsonSchemaGenerator.generateForMethodInput(method);
    Map<String, Object> schema = objectMapper.readValue(schemaJson, Map.class);

    // Then: Schema has flat properties (not nested under "request")
    assertThat(schema).containsKey("properties");
    Map<String, Object> properties = (Map<String, Object>) schema.get("properties");

    // Verify all DTO fields are at root level
    assertThat(properties).containsKeys("taskName", "priority", "metadata", "urgent");

    // Verify NO $defs section (inline schemas)
    assertThat(schema).doesNotContainKey("$defs");
    assertThat(schema).doesNotContainKey("definitions");

    // Note: required fields come from Jackson annotations on DTO
    // (in this case, @NotBlank, @NotNull are from bean validation, not Jackson required)
  }

  @Test
  void shouldGenerateSameSchemaForOldWayComparison() throws Exception {
    // Given: Both methods (new @McpRequestBody vs old @McpToolParam)
    Method newWay = DemoTools.class.getMethod("createTask", CreateTaskRequest.class);
    Method oldWay =
        DemoTools.class.getMethod(
            "createTaskOldWay", String.class, String.class, Map.class, Boolean.class);

    // When: Generate schemas for both
    String newWaySchema = CamundaJsonSchemaGenerator.generateForMethodInput(newWay);
    String oldWaySchema = CamundaJsonSchemaGenerator.generateForMethodInput(oldWay);

    Map<String, Object> newWayMap = objectMapper.readValue(newWaySchema, Map.class);
    Map<String, Object> oldWayMap = objectMapper.readValue(oldWaySchema, Map.class);

    // Then: Both should have same root-level properties
    Map<String, Object> newWayProps = (Map<String, Object>) newWayMap.get("properties");
    Map<String, Object> oldWayProps = (Map<String, Object>) oldWayMap.get("properties");

    assertThat(newWayProps.keySet())
        .containsExactlyInAnyOrder("taskName", "priority", "metadata", "urgent");
    assertThat(oldWayProps.keySet())
        .containsExactlyInAnyOrder("taskName", "priority", "metadata", "urgent");

    // Note: Required fields may differ between approaches (DTO bean validation vs
    // @McpToolParam.required)
    // As long as properties are the same, the schemas are equivalent for our purposes
  }

  @Test
  void shouldInvokeToolWithMcpRequestBody() {
    // Given: Demo tool
    DemoTools demoTools = new DemoTools();
    CreateTaskRequest request =
        new CreateTaskRequest("Test task", "high", Map.of("key", "value"), true);

    // When: Invoke tool
    var result = demoTools.createTask(request);

    // Then: Result contains expected data
    assertThat(result).isNotNull();
    assertThat(result.content()).isNotEmpty();

    var content = result.content().getFirst();
    assertThat(content).isInstanceOf(io.modelcontextprotocol.spec.McpSchema.TextContent.class);

    var textContent = (io.modelcontextprotocol.spec.McpSchema.TextContent) content;
    assertThat(textContent.text()).contains("Test task");
    assertThat(textContent.text()).contains("high");
    assertThat(textContent.text()).contains("true");
  }
}
