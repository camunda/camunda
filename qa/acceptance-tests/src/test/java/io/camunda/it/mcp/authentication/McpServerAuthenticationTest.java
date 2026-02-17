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
import io.camunda.gateway.protocol.model.TopologyResponse;
import io.camunda.it.mcp.McpServerTest;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.Objects;
import org.junit.jupiter.api.Test;

abstract class McpServerAuthenticationTest extends McpServerTest {

  protected final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void returnsConfiguredInfoAndCapabilities() {
    final var initializeResult = mcpClient.initialize();
    assertThat(initializeResult).isNotNull();

    assertThat(initializeResult.serverInfo().name())
        .isEqualTo("Camunda 8 Orchestration API MCP Server");
    assertThat(initializeResult.serverInfo().version()).startsWith("8.");

    assertThat(initializeResult.capabilities())
        .extracting(
            ServerCapabilities::completions,
            ServerCapabilities::experimental,
            ServerCapabilities::logging,
            ServerCapabilities::prompts,
            ServerCapabilities::resources)
        .allMatch(Objects::isNull);

    assertThat(initializeResult.capabilities().tools())
        .isNotNull()
        .satisfies(tools -> assertThat(tools.listChanged()).isFalse());
  }

  @Test
  void registersAllExpectedTools() {
    final ListToolsResult listToolsResult = mcpClient.listTools();
    assertThat(listToolsResult.tools())
        .extracting(Tool::name)
        .contains("getClusterStatus", "getTopology");
  }

  @Test
  void fetchesClusterStatus() {
    final CallToolResult result =
        mcpClient.callTool(CallToolRequest.builder().name("getClusterStatus").build());

    assertThat(result.isError()).isFalse();
    assertThat(result.content())
        .hasSize(1)
        .first()
        .isInstanceOfSatisfying(
            TextContent.class, textContent -> assertThat(textContent.text()).isEqualTo("HEALTHY"));
  }

  @Test
  void fetchesTopology() {
    final CallToolResult result =
        mcpClient.callTool(CallToolRequest.builder().name("getTopology").build());

    assertThat(result.isError()).isFalse();
    assertThat(result.structuredContent()).isNotNull();

    final var topology =
        objectMapper.convertValue(result.structuredContent(), TopologyResponse.class);
    assertThat(topology.getClusterSize()).isEqualTo(1);
    assertThat(topology.getBrokers()).hasSize(1);
    assertThat(topology.getBrokers().getFirst().getNodeId().toString())
        .isEqualTo(testInstance().nodeId().id());
  }
}
