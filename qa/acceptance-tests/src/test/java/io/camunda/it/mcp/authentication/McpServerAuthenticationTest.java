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
import io.camunda.zeebe.util.VersionUtil;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.Objects;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

abstract class McpServerAuthenticationTest extends McpServerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @ParameterizedTest
  @MethodSource("testClients")
  void returnsConfiguredInfoAndCapabilities(final String endpoint, final String name) {
    final var initializeResult = getMcpClient(endpoint).initialize();
    assertThat(initializeResult).isNotNull();

    assertThat(initializeResult.serverInfo().name()).isEqualTo(name);
    assertThat(initializeResult.serverInfo().version()).isEqualTo(VersionUtil.getVersion());

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

  @ParameterizedTest
  @MethodSource("testClients")
  void registersAllExpectedTools(final String endpoint) {
    final ListToolsResult listToolsResult = getMcpClient(endpoint).listTools();
    assertThat(listToolsResult.tools())
        .extracting(Tool::name)
        .contains("getClusterStatus", "getTopology");
  }

  @ParameterizedTest
  @MethodSource("testClients")
  void fetchesClusterStatus(final String endpoint) {
    final CallToolResult result =
        getMcpClient(endpoint).callTool(CallToolRequest.builder().name("getClusterStatus").build());

    assertThat(result.isError()).isFalse();
    assertThat(result.content())
        .hasSize(1)
        .first()
        .isInstanceOfSatisfying(
            TextContent.class, textContent -> assertThat(textContent.text()).isEqualTo("HEALTHY"));
  }

  @ParameterizedTest
  @MethodSource("testClients")
  void fetchesTopology(final String endpoint) {
    final CallToolResult result =
        getMcpClient(endpoint).callTool(CallToolRequest.builder().name("getTopology").build());

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
