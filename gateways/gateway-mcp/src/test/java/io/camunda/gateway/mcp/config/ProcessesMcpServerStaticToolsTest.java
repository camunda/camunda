/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gateway.mcp.ProcessesToolsTest;
import io.camunda.gateway.mcp.config.server.ToolRepository;
import io.camunda.zeebe.util.Either;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(classes = ProcessesMcpServerStaticToolsTest.StaticToolsConfiguration.class)
class ProcessesMcpServerStaticToolsTest extends ProcessesToolsTest {

  @Test
  void shouldListStaticallyConfiguredTools() {
    // when
    final var result = mcpClient.listTools();

    // then
    assertThat(result.tools()).extracting(Tool::name).containsExactly("staticProcess");
  }

  @Test
  void shouldCallStaticallyConfiguredTools() {
    // when
    final CallToolResult result =
        mcpClient.callTool(CallToolRequest.builder().name("staticProcess").build());

    // then
    assertThat(result.isError()).isFalse();
    assertThat(result.content())
        .singleElement()
        .isInstanceOfSatisfying(
            TextContent.class,
            textContent -> assertThat(textContent.text()).isEqualTo("started static process"));
  }

  @Configuration
  static class StaticToolsConfiguration {

    @Bean(name = "processesToolRepository")
    ToolRepository processesToolRepository() {
      return new ToolRepository() {

        private final Map<String, SyncToolSpecification> tools =
            Map.of(
                "staticProcess",
                SyncToolSpecification.builder()
                    .tool(
                        Tool.builder()
                            .name("staticProcess")
                            .title("staticProcess")
                            .description("Static fallback process tool")
                            .inputSchema(new JsonSchema("object", null, null, false, null, null))
                            .build())
                    .callHandler(
                        (context, callToolRequest) ->
                            CallToolResult.builder()
                                .addTextContent("started static process")
                                .build())
                    .build());

        @Override
        public @NonNull List<Tool> getTools(@NonNull final McpTransportContext transportContext) {
          return tools.values().stream().map(SyncToolSpecification::tool).toList();
        }

        @Override
        public @NonNull Either<String, SyncToolSpecification> findTool(
            @NonNull final McpTransportContext transportContext, @NonNull final String toolName) {
          return Either.right(tools.get(toolName));
        }
      };
    }
  }
}
