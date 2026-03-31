/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.gateway.mcp.ProcessesToolsTest;
import io.camunda.gateway.mcp.config.server.ToolRepository;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.Either.Right;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ErrorCodes;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCResponse.JSONRPCError;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(classes = ProcessesMcpServerDynamicToolsTest.DynamicToolsConfiguration.class)
class ProcessesMcpServerDynamicToolsTest extends ProcessesToolsTest {

  @Autowired private MutableToolRepository toolRepository;

  @Test
  void shouldListToolsFromRepositoryOnEveryRequest() {
    // given
    toolRepository.setTools(List.of(toolSpecification("firstProcess", "started first process")));

    // when
    final var firstResult = mcpClient.listTools();

    toolRepository.setTools(List.of(toolSpecification("secondProcess", "started second process")));
    final var secondResult = mcpClient.listTools();

    // then
    assertThat(firstResult.tools()).extracting(Tool::name).containsExactly("firstProcess");
    assertThat(secondResult.tools()).extracting(Tool::name).containsExactly("secondProcess");
  }

  @Test
  void shouldResolveToolCallsUsingLatestRepositoryTools() {
    // given
    toolRepository.setTools(List.of(toolSpecification("alphaProcess", "started alpha process")));

    // when
    final CallToolResult firstCallResult =
        mcpClient.callTool(CallToolRequest.builder().name("alphaProcess").build());

    toolRepository.setTools(List.of(toolSpecification("betaProcess", "started beta process")));
    final CallToolResult secondCallResult =
        mcpClient.callTool(CallToolRequest.builder().name("betaProcess").build());

    // then
    assertThat(firstCallResult.isError()).isFalse();
    assertThat(firstCallResult.content())
        .singleElement()
        .isInstanceOfSatisfying(
            TextContent.class,
            textContent -> assertThat(textContent.text()).isEqualTo("started alpha process"));

    assertThat(secondCallResult.isError()).isFalse();
    assertThat(secondCallResult.content())
        .singleElement()
        .isInstanceOfSatisfying(
            TextContent.class,
            textContent -> assertThat(textContent.text()).isEqualTo("started beta process"));
  }

  @Test
  void shouldFailOnToolCallToOutdatedRepositoryTools() {
    // given
    toolRepository.setTools(List.of(toolSpecification("alphaProcess", "started alpha process")));
    final ListToolsResult oldTools = mcpClient.listTools();
    toolRepository.setTools(List.of(toolSpecification("betaProcess", "started beta process")));

    // when/then
    assertThatThrownBy(
            () ->
                mcpClient.callTool(
                    CallToolRequest.builder().name(oldTools.tools().getFirst().name()).build()))
        .isInstanceOfSatisfying(
            McpError.class,
            exception ->
                assertThat(exception.getJsonRpcError())
                    .extracting(JSONRPCError::code, JSONRPCError::message, JSONRPCError::data)
                    .containsExactly(
                        ErrorCodes.INVALID_PARAMS,
                        "Unknown tool: invalid_tool_name",
                        "Tool not found: " + oldTools.tools().getFirst().name()));
  }

  @Test
  void shouldCreateMcpErrorForCallHandlerException() {
    // given
    toolRepository.setTools(
        List.of(
            toolSpecification(
                "alphaProcess",
                "started alpha process",
                new ServiceException("Just a test", Status.INVALID_ARGUMENT))));

    // when/then
    assertThatThrownBy(
            () -> mcpClient.callTool(CallToolRequest.builder().name("alphaProcess").build()))
        .isInstanceOfSatisfying(
            McpError.class,
            exception ->
                assertThat(exception.getJsonRpcError())
                    .extracting(JSONRPCError::code, JSONRPCError::message, JSONRPCError::data)
                    .containsExactly(ErrorCodes.INTERNAL_ERROR, "Just a test", null));
  }

  @Test
  void shouldPassOnCallHandlerMcpError() {
    // given
    toolRepository.setTools(
        List.of(
            toolSpecification(
                "alphaProcess",
                "started alpha process",
                McpError.builder(ErrorCodes.INVALID_PARAMS)
                    .message("Provoked")
                    .data("We wanted this")
                    .build())));

    // when/then
    assertThatThrownBy(
            () -> mcpClient.callTool(CallToolRequest.builder().name("alphaProcess").build()))
        .isInstanceOfSatisfying(
            McpError.class,
            exception ->
                assertThat(exception.getJsonRpcError())
                    .extracting(JSONRPCError::code, JSONRPCError::message, JSONRPCError::data)
                    .containsExactly(ErrorCodes.INVALID_PARAMS, "Provoked", "We wanted this"));
  }

  private static SyncToolSpecification toolSpecification(
      final String toolName, final String responseText) {
    return toolSpecification(toolName, responseText, null);
  }

  private static SyncToolSpecification toolSpecification(
      final String toolName, final String responseText, final RuntimeException failure) {
    return SyncToolSpecification.builder()
        .tool(
            Tool.builder()
                .name(toolName)
                .title(toolName)
                .description("Test process tool " + toolName)
                .inputSchema(new JsonSchema("object", null, null, false, null, null))
                .build())
        .callHandler(
            (transportContext, callToolRequest) -> {
              if (failure != null) {
                throw failure;
              }
              return CallToolResult.builder().addTextContent(responseText).build();
            })
        .build();
  }

  @Configuration
  static class DynamicToolsConfiguration {

    @Bean(name = "processesToolRepository")
    MutableToolRepository mutableSyncToolRepository() {
      return new MutableToolRepository();
    }
  }

  static final class MutableToolRepository implements ToolRepository {

    private volatile Map<String, SyncToolSpecification> toolSpecifications = Map.of();

    void setTools(final List<SyncToolSpecification> toolSpecifications) {
      this.toolSpecifications =
          toolSpecifications.stream()
              .collect(Collectors.toMap(toolSpec -> toolSpec.tool().name(), toolSpec -> toolSpec));
    }

    @Override
    public @NonNull List<Tool> getTools(@NonNull final McpTransportContext transportContext) {
      return toolSpecifications.values().stream()
          .map(SyncToolSpecification::tool)
          .collect(Collectors.toList());
    }

    @Override
    public @NonNull Either<String, SyncToolSpecification> findTool(
        @NonNull final McpTransportContext transportContext, @NonNull final String toolName) {
      return new Right<>(toolSpecifications.get(toolName));
    }
  }
}
