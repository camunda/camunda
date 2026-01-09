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
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import java.util.Objects;
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
  void returnsRegisteredTools() {
    final ListToolsResult listToolsResult = mcpClient.listTools();
    assertThat(listToolsResult.tools()).isNotEmpty();
  }

  // TODO add smoke tests for primary, secondary storage operations + authorizations

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
