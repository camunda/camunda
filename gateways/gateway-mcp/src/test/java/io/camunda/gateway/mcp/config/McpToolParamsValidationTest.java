package io.camunda.gateway.mcp.config;

import static org.assertj.core.api.Assertions.*;

import io.camunda.gateway.mcp.tool.demo.CreateTaskRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.validation.Valid;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

class McpToolParamsValidationTest {

  // Valid usage: @McpToolParams alone
  @Component
  static class ValidSingleRequestBody {
    @McpTool
    public CallToolResult validMethod(@McpToolParams CreateTaskRequest request) {
      return null;
    }
  }

  // Invalid usage: @McpToolParams mixed with @Valid object
  @Component
  static class InvalidMixedParameters {
    @McpTool
    public CallToolResult invalidMethod(
        @McpToolParams CreateTaskRequest request, @Valid CreateTaskRequest other) {
      return null;
    }
  }

  // Valid usage: @McpToolParams with simple type
  @Component
  static class ValidWithSimpleType {
    @McpTool
    public CallToolResult validMethod(
        @McpToolParams CreateTaskRequest request, String simpleParam) {
      return null;
    }
  }

  @Test
  void shouldAcceptSingleRequestBodyParameter() {
    // Should not throw
    assertThatCode(
            () ->
                new CamundaSyncStatelessMcpToolProvider(List.of(new ValidSingleRequestBody()))
                    .getToolSpecifications())
        .doesNotThrowAnyException();
  }

  @Test
  void shouldAcceptRequestBodyWithSimpleParameters() {
    // Should not throw
    assertThatCode(
            () ->
                new CamundaSyncStatelessMcpToolProvider(List.of(new ValidWithSimpleType()))
                    .getToolSpecifications())
        .doesNotThrowAnyException();
  }

  @Test
  void shouldRejectMixedRequestBodyAndValidParameters() {
    assertThatThrownBy(
            () ->
                new CamundaSyncStatelessMcpToolProvider(List.of(new InvalidMixedParameters()))
                    .getToolSpecifications())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("mixes @McpToolParams with complex parameter")
        .hasMessageContaining("invalidMethod");
  }

  // Valid usage: @McpToolParams with validated primitive
  @Component
  static class ValidWithValidatedPrimitive {
    @McpTool
    public CallToolResult validMethod(
        @McpToolParams CreateTaskRequest request,
        @jakarta.validation.constraints.NotBlank String name,
        @jakarta.validation.constraints.Min(1) int count) {
      return null;
    }
  }

  @Test
  void shouldAcceptRequestBodyWithValidatedPrimitives() {
    // @NotBlank/@Min on primitives are allowed (not the same as @Valid on objects)
    assertThatCode(
            () ->
                new CamundaSyncStatelessMcpToolProvider(List.of(new ValidWithValidatedPrimitive()))
                    .getToolSpecifications())
        .doesNotThrowAnyException();
  }
}
