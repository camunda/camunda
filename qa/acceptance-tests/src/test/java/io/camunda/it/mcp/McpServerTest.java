/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.mcp;

import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.provider.Arguments;

public abstract class McpServerTest {

  public static final McpSyncHttpClientRequestCustomizer DEFAULT_REQUEST_CUSTOMIZER =
      (builder, method, endpoint, body, context) -> {};

  McpSyncClient mcpClient;

  protected McpSyncClient getMcpClient(final String endpoint) {
    return getMcpClient(endpoint, createMcpClientRequestCustomizer());
  }

  protected McpSyncClient getMcpClient(
      final String endpoint, final McpSyncHttpClientRequestCustomizer httpClientRequestCustomizer) {
    mcpClient = createMcpClient(testInstance(), httpClientRequestCustomizer, endpoint);
    return mcpClient;
  }

  @AfterEach
  void closeMcpClient() {
    if (mcpClient != null) {
      mcpClient.close();
      mcpClient = null;
    }
  }

  protected abstract TestCamundaApplication testInstance();

  protected McpSyncHttpClientRequestCustomizer createMcpClientRequestCustomizer() {
    return DEFAULT_REQUEST_CUSTOMIZER;
  }

  public static Stream<Arguments> testClients() {
    return Stream.of(
        arguments("/mcp/cluster", "Camunda 8 Orchestration API MCP Server"),
        arguments("/mcp/processes", "Camunda 8 Orchestration API Processes MCP Server"));
  }

  public static McpSyncClient createMcpClient(
      final TestCamundaApplication testInstance,
      final McpSyncHttpClientRequestCustomizer httpClientRequestCustomizer,
      final String endpoint) {
    final HttpClientStreamableHttpTransport.Builder transportBuilder =
        HttpClientStreamableHttpTransport.builder("%s".formatted(testInstance.restAddress()))
            .endpoint(endpoint)
            .openConnectionOnStartup(false);

    if (httpClientRequestCustomizer != null) {
      transportBuilder.httpRequestCustomizer(httpClientRequestCustomizer);
    }

    return McpClient.sync(transportBuilder.build()).build();
  }
}
