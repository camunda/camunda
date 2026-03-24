/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gateway.mcp.config.server.ToolRepository;
import io.camunda.gateway.mcp.tool.ToolsTest;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.Either.Right;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
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
class ProcessesMcpServerDynamicToolsTest extends ToolsTest {

  @Autowired private MutableToolRepository toolRepository;

  @Override
  protected String endpoint() {
    return "/mcp/processes";
  }

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

  private static SyncToolSpecification toolSpecification(
      final String toolName, final String responseText) {
    return SyncToolSpecification.builder()
        .tool(
            Tool.builder()
                .name(toolName)
                .title(toolName)
                .description("Test process tool " + toolName)
                .inputSchema(new JsonSchema("object", null, null, false, null, null))
                .build())
        .callHandler(
            (transportContext, callToolRequest) ->
                CallToolResult.builder().addTextContent(responseText).build())
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
    public @NonNull List<@NonNull Tool> getTools(
        @NonNull final McpTransportContext transportContext) {
      return toolSpecifications.values().stream()
          .map(SyncToolSpecification::tool)
          .collect(Collectors.toList());
    }

    @Override
    public @NonNull Either<@NonNull String, @NonNull SyncToolSpecification> findTool(
        @NonNull final McpTransportContext transportContext, @NonNull final String toolName) {
      return new Right<>(toolSpecifications.get(toolName));
    }
  }
}
