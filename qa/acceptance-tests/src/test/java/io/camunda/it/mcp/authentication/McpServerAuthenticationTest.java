/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.mcp.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedTopologyResponseStrictContract;
import io.camunda.it.mcp.McpServerTest;
import io.camunda.zeebe.util.VersionUtil;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

abstract class McpServerAuthenticationTest extends McpServerTest {

  protected final ObjectMapper objectMapper = new ObjectMapper();

  @ParameterizedTest
  @MethodSource("mcpServersToTest")
  void returnsConfiguredInfoAndCapabilities(final String mcpServer) {
    try (final McpSyncClient mcpClient =
        createMcpClient(mcpServer, testInstance(), createMcpClientRequestCustomizer())) {
      final var initializeResult = mcpClient.initialize();
      assertThat(initializeResult).isNotNull();

      assertThat(initializeResult.serverInfo().name())
          .contains("Camunda 8 Orchestration API")
          .contains("MCP Server");
      assertThat(initializeResult.serverInfo().version()).isEqualTo(VersionUtil.getVersion());
      assertThat(initializeResult.instructions()).isNotBlank();

      assertThat(initializeResult.capabilities())
          .extracting(
              ServerCapabilities::completions,
              ServerCapabilities::experimental,
              ServerCapabilities::logging)
          .allMatch(Objects::isNull);

      assertThat(initializeResult.capabilities().tools())
          .isNotNull()
          .satisfies(tools -> assertThat(tools.listChanged()).isFalse());

      assertThat(initializeResult.capabilities().prompts())
          .isNotNull()
          .satisfies(prompts -> assertThat(prompts.listChanged()).isFalse());

      assertThat(initializeResult.capabilities().resources())
          .isNotNull()
          .satisfies(
              resources -> {
                assertThat(resources.listChanged()).isFalse();
                assertThat(resources.subscribe()).isFalse();
              });
    }
  }

  @Test
  void registersAllExpectedTools() {
    try (final McpSyncClient mcpClient =
        createMcpClient("cluster", testInstance(), createMcpClientRequestCustomizer())) {
      final ListToolsResult listToolsResult = mcpClient.listTools();
      assertThat(listToolsResult.tools())
          .extracting(Tool::name)
          .contains("getClusterStatus", "getTopology");
    }
  }

  @Test
  void fetchesClusterStatus() {
    try (final McpSyncClient mcpClient =
        createMcpClient("cluster", testInstance(), createMcpClientRequestCustomizer())) {
      final CallToolResult result =
          mcpClient.callTool(CallToolRequest.builder().name("getClusterStatus").build());

      assertThat(result.isError()).isFalse();
      assertThat(result.content())
          .hasSize(1)
          .first()
          .isInstanceOfSatisfying(
              TextContent.class,
              textContent -> assertThat(textContent.text()).isEqualTo("HEALTHY"));
    }
  }

  @Test
  void fetchesTopology() {
    try (final McpSyncClient mcpClient =
        createMcpClient("cluster", testInstance(), createMcpClientRequestCustomizer())) {
      final CallToolResult result =
          mcpClient.callTool(CallToolRequest.builder().name("getTopology").build());

      assertThat(result.isError()).isFalse();
      assertThat(result.structuredContent()).isNotNull();

      final var topology =
          objectMapper.convertValue(
              result.structuredContent(), GeneratedTopologyResponseStrictContract.class);
      assertThat(topology.clusterSize()).isEqualTo(1);
      assertThat(topology.brokers()).hasSize(1);
      assertThat(topology.brokers().getFirst().nodeId().toString())
          .isEqualTo(testInstance().nodeId().id());
    }
  }
}
