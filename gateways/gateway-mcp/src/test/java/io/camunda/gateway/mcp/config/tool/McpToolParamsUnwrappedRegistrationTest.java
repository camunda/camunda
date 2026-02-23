/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.config.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.gateway.mcp.config.schema.CamundaJsonSchemaGenerator;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.validation.Valid;
import java.util.List;
import org.junit.jupiter.api.Test;

class McpToolParamsUnwrappedRegistrationTest {

  private final CamundaJsonSchemaGenerator schemaGenerator = new CamundaJsonSchemaGenerator();

  @Test
  void shouldRejectMultipleMcpToolParams() {
    final var provider =
        new CamundaSyncStatelessMcpToolProvider(
            List.of(new MultipleMcpToolParamsTool()), schemaGenerator);

    assertThatThrownBy(provider::getToolSpecifications)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Method 'MultipleMcpToolParamsTool.invalidTool' has multiple @McpToolParamsUnwrapped parameters. "
                + "Only a single @McpToolParamsUnwrapped parameter is allowed per method.");
  }

  @Test
  void shouldRejectMixingMcpToolParamsWithIndividualParams() {
    final var provider =
        new CamundaSyncStatelessMcpToolProvider(List.of(new MixedParamsTool()), schemaGenerator);

    assertThatThrownBy(provider::getToolSpecifications)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Method 'MixedParamsTool.invalidTool' mixes @McpToolParamsUnwrapped with individual parameter "
                + "'name' (type: String). Use either individual parameters OR a single "
                + "@McpToolParamsUnwrapped wrapper, not both.");
  }

  @Test
  void shouldAcceptMcpToolParamsWithFrameworkParams() {
    final var provider =
        new CamundaSyncStatelessMcpToolProvider(
            List.of(new ValidToolWithFrameworkParams()), schemaGenerator);

    assertThatNoException().isThrownBy(provider::getToolSpecifications);
    assertThat(provider.getToolSpecifications()).hasSize(1);
  }

  // --- Test tool classes ---

  record SimpleDto(String value) {}

  static class MultipleMcpToolParamsTool {
    @CamundaMcpTool(description = "invalid")
    public CallToolResult invalidTool(
        @McpToolParamsUnwrapped @Valid final SimpleDto first,
        @McpToolParamsUnwrapped @Valid final SimpleDto second) {
      return null;
    }
  }

  static class MixedParamsTool {
    @CamundaMcpTool(description = "invalid")
    public CallToolResult invalidTool(
        @McpToolParamsUnwrapped @Valid final SimpleDto dto, final String name) {
      return null;
    }
  }

  static class ValidToolWithFrameworkParams {
    @CamundaMcpTool(description = "valid")
    public CallToolResult validTool(
        @McpToolParamsUnwrapped @Valid final SimpleDto dto, final CallToolRequest request) {
      return null;
    }
  }
}
