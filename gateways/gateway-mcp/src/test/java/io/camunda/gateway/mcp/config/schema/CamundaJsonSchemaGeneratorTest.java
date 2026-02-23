/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.config.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.gateway.mcp.config.tool.McpToolParamsUnwrapped;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import jakarta.validation.Valid;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springaicommunity.mcp.context.McpSyncRequestContext;

class CamundaJsonSchemaGeneratorTest {

  private final CamundaJsonSchemaGenerator schemaGenerator = new CamundaJsonSchemaGenerator();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldGenerateMinimalSchemaForMethodWithOnlyFrameworkParams() throws Exception {
    final JsonNode schema = generateMethodSchema("withOnlyFrameworkParams", CallToolRequest.class);

    assertThat(schema.get("properties").isEmpty()).isTrue();
    assertThat(schema.get("required").isEmpty()).isTrue();
  }

  @Test
  void shouldInlineRepeatedTypesWithoutDefs() throws Exception {
    final JsonNode schema =
        generateMethodSchema("withRepeatedTypesIndividual", Address.class, Address.class);

    assertThat(schema.has("$defs")).isFalse();
    assertThat(schema.has("definitions")).isFalse();
    assertInlinedAddressProperties(schema);
  }

  @Test
  void shouldUnwrapMcpToolParamsToRootLevel() throws Exception {
    final JsonNode schema = generateMethodSchema("withMcpToolParams", TaskDto.class);
    final JsonNode properties = schema.get("properties");

    // DTO fields appear at root, not nested under parameter name
    assertThat(properties.has("name")).isTrue();
    assertThat(properties.has("address")).isTrue();
    assertThat(properties.has("dto")).isFalse();

    // descriptions are preserved
    assertThat(properties.get("name").get("description").asText()).isEqualTo("Task name");

    // required fields from DTO are propagated to root
    assertThat(schema.get("required")).hasSize(1);
    assertThat(schema.get("required").get(0).asText()).isEqualTo("name");

    // nested objects are represented as typed sub-schemas
    final JsonNode address = properties.get("address");
    assertThat(address.get("type").asText()).isEqualTo("object");
    assertThat(address.get("properties").has("street")).isTrue();
    assertThat(address.get("properties").has("zipCode")).isTrue();
  }

  @Test
  void shouldInlineRepeatedTypesInMcpToolParams() throws Exception {
    final JsonNode schema =
        generateMethodSchema("withRepeatedTypesMcpToolParams", DtoWithRepeatedTypes.class);

    assertThat(schema.has("$defs")).isFalse();
    assertThat(schema.has("definitions")).isFalse();
    assertInlinedAddressProperties(schema);
  }

  @Test
  void shouldExcludeFrameworkParamsFromMcpToolParamsSchema() throws Exception {
    final JsonNode schema =
        generateMethodSchema(
            "withMcpToolParamsAndContext", TaskDto.class, McpSyncRequestContext.class);
    final JsonNode properties = schema.get("properties");

    assertThat(properties.has("name")).isTrue();
    assertThat(properties.has("address")).isTrue();
    assertThat(properties.has("context")).isFalse();
  }

  private JsonNode generateMethodSchema(final String methodName, final Class<?>... paramTypes)
      throws Exception {
    final Method method = TestToolMethods.class.getMethod(methodName, paramTypes);
    return objectMapper.readTree(schemaGenerator.generateForMethodInput(method));
  }

  private void assertInlinedAddressProperties(final JsonNode schema) {
    final JsonNode properties = schema.get("properties");

    assertThat(properties.has("homeAddress")).isTrue();
    assertThat(properties.get("homeAddress").get("type").asText()).isEqualTo("object");
    assertThat(properties.get("homeAddress").has("properties")).isTrue();

    assertThat(properties.has("workAddress")).isTrue();
    assertThat(properties.get("workAddress").get("type").asText()).isEqualTo("object");
    assertThat(properties.get("workAddress").has("properties")).isTrue();
  }

  // --- Test models ---

  public record Address(String street, String zipCode) {}

  public record TaskDto(
      @McpToolParam(description = "Task name", required = true) String name,
      @McpToolParam(description = "Task address", required = false) Address address) {}

  public record DtoWithRepeatedTypes(Address homeAddress, Address workAddress) {}

  public static class TestToolMethods {
    public void withOnlyFrameworkParams(final CallToolRequest request) {}

    public void withRepeatedTypesIndividual(final Address homeAddress, final Address workAddress) {}

    public void withMcpToolParams(@McpToolParamsUnwrapped @Valid final TaskDto dto) {}

    public void withRepeatedTypesMcpToolParams(
        @McpToolParamsUnwrapped @Valid final DtoWithRepeatedTypes dto) {}

    public void withMcpToolParamsAndContext(
        @McpToolParamsUnwrapped @Valid final TaskDto dto, final McpSyncRequestContext context) {}
  }
}
