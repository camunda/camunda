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
import io.camunda.zeebe.util.Either;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.ErrorCodes;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCResponse.JSONRPCError;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(
    classes = ProcessesMcpServerFailingToolRepositoryTest.FailingToolsConfiguration.class)
class ProcessesMcpServerFailingToolRepositoryTest extends ProcessesToolsTest {

  @Test
  void shouldCreateMcpErrorForToolListException() {
    // when/then
    assertThatThrownBy(() -> mcpClient.listTools())
        .isInstanceOfSatisfying(
            McpError.class,
            exception ->
                assertThat(exception.getJsonRpcError())
                    .extracting(JSONRPCError::code, JSONRPCError::message, JSONRPCError::data)
                    .containsExactly(
                        ErrorCodes.INTERNAL_ERROR, "Simulated failure in getTools", null));
  }

  @Test
  void shouldCreateMcpErrorForToolFindException() {
    // when/then
    assertThatThrownBy(() -> mcpClient.callTool(CallToolRequest.builder().name("anyTool").build()))
        .isInstanceOfSatisfying(
            McpError.class,
            exception ->
                assertThat(exception.getJsonRpcError())
                    .extracting(JSONRPCError::code, JSONRPCError::message, JSONRPCError::data)
                    .containsExactly(
                        ErrorCodes.INTERNAL_ERROR,
                        "Error on finding tool",
                        "Simulated failure in findTool"));
  }

  @Configuration
  static class FailingToolsConfiguration {

    @Bean(name = "processesToolRepository")
    ToolRepository processesToolRepository() {
      return new ToolRepository() {

        @Override
        public @NonNull List<Tool> getTools(@NonNull final McpTransportContext transportContext) {
          throw new NullPointerException("Simulated failure in getTools");
        }

        @Override
        public @NonNull Either<String, SyncToolSpecification> findTool(
            @NonNull final McpTransportContext transportContext, @NonNull final String toolName) {
          throw new NullPointerException("Simulated failure in findTool");
        }
      };
    }
  }
}
