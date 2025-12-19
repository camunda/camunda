/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.service.TopologyServices.Topology;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

abstract class McpServerAuthenticationTest {

  protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
  private McpSyncClient mcpClient;

  @BeforeEach
  void setUp() {
    mcpClient = createMcpClient(createMcpClientRequestCustomizer());
  }

  @AfterEach
  void tearDown() {
    if (mcpClient != null) {
      mcpClient.close();
    }
  }

  @Test
  void registersAllExpectedTools() {
    final ListToolsResult listToolsResult = mcpClient.listTools();
    assertThat(listToolsResult.tools())
        .extracting(Tool::name)
        .containsExactlyInAnyOrder(
            "getClusterStatus", "getTopology", "getIncident", "resolveIncident", "searchIncidents");
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

    final var topology = OBJECT_MAPPER.convertValue(result.structuredContent(), Topology.class);
    assertThat(topology.clusterSize()).isEqualTo(1);
    assertThat(topology.brokers()).hasSize(1);
    assertThat(topology.brokers().getFirst().nodeId().toString())
        .isEqualTo(testInstance().nodeId().id());
  }

  protected abstract TestCamundaApplication testInstance();

  protected McpSyncClient createMcpClient(
      final McpSyncHttpClientRequestCustomizer httpClientRequestCustomizer) {
    final HttpClientStreamableHttpTransport.Builder transportBuilder =
        HttpClientStreamableHttpTransport.builder("%s/mcp".formatted(testInstance().restAddress()))
            .openConnectionOnStartup(false);

    if (httpClientRequestCustomizer != null) {
      transportBuilder.httpRequestCustomizer(httpClientRequestCustomizer);
    }

    return McpClient.sync(transportBuilder.build()).build();
  }

  protected McpSyncHttpClientRequestCustomizer createMcpClientRequestCustomizer() {
    return (builder, method, endpoint, body, context) -> {};
  }
}
