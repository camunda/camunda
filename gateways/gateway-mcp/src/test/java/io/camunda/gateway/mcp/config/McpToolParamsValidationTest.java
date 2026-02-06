/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.config;

import static org.assertj.core.api.Assertions.*;

import io.camunda.gateway.mcp.config.schema.CamundaJsonSchemaGenerator;
import io.camunda.gateway.mcp.config.tool.CamundaMcpTool;
import io.camunda.gateway.mcp.config.tool.CamundaSyncStatelessMcpToolProvider;
import io.camunda.gateway.mcp.config.tool.McpToolParams;
import io.camunda.gateway.mcp.tool.demo.CreateTaskRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.validation.Valid;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

class McpToolParamsValidationTest {

  private final CamundaJsonSchemaGenerator camundaJsonSchemaGenerator =
      new CamundaJsonSchemaGenerator();

  @Test
  void shouldAcceptSingleRequestBodyParameter() {
    // Should not throw
    assertThatCode(
            () ->
                new CamundaSyncStatelessMcpToolProvider(
                        List.of(new ValidSingleRequestBody()), camundaJsonSchemaGenerator)
                    .getToolSpecifications())
        .doesNotThrowAnyException();
  }

  @Test
  void shouldAcceptRequestBodyWithSimpleParameters() {
    // Should not throw
    assertThatCode(
            () ->
                new CamundaSyncStatelessMcpToolProvider(
                        List.of(new ValidWithSimpleType()), camundaJsonSchemaGenerator)
                    .getToolSpecifications())
        .doesNotThrowAnyException();
  }

  @Test
  void shouldRejectMixedRequestBodyAndValidParameters() {
    assertThatThrownBy(
            () ->
                new CamundaSyncStatelessMcpToolProvider(
                        List.of(new InvalidMixedParameters()), camundaJsonSchemaGenerator)
                    .getToolSpecifications())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("mixes @McpToolParams with complex parameter")
        .hasMessageContaining("invalidMethod");
  }

  @Test
  void shouldAcceptRequestBodyWithValidatedPrimitives() {
    // @NotBlank/@Min on primitives are allowed (not the same as @Valid on objects)
    assertThatCode(
            () ->
                new CamundaSyncStatelessMcpToolProvider(
                        List.of(new ValidWithValidatedPrimitive()), camundaJsonSchemaGenerator)
                    .getToolSpecifications())
        .doesNotThrowAnyException();
  }

  // Valid usage: @McpToolParams alone
  @Component
  static class ValidSingleRequestBody {
    @CamundaMcpTool
    public CallToolResult validMethod(@McpToolParams final CreateTaskRequest request) {
      return null;
    }
  }

  // Invalid usage: @McpToolParams mixed with @Valid object
  @Component
  static class InvalidMixedParameters {
    @CamundaMcpTool
    public CallToolResult invalidMethod(
        @McpToolParams final CreateTaskRequest request, @Valid final CreateTaskRequest other) {
      return null;
    }
  }

  // Valid usage: @McpToolParams with simple type
  @Component
  static class ValidWithSimpleType {
    @CamundaMcpTool
    public CallToolResult validMethod(
        @McpToolParams final CreateTaskRequest request, final String simpleParam) {
      return null;
    }
  }

  // Valid usage: @McpToolParams with validated primitive
  @Component
  static class ValidWithValidatedPrimitive {
    @CamundaMcpTool
    public CallToolResult validMethod(
        @McpToolParams final CreateTaskRequest request,
        @jakarta.validation.constraints.NotBlank final String name,
        @jakarta.validation.constraints.Min(1) final int count) {
      return null;
    }
  }
}
