/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.mcp;

import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class McpServerTest {

  public static final McpSyncHttpClientRequestCustomizer DEFAULT_REQUEST_CUSTOMIZER =
      (builder, method, endpoint, body, context) -> {};

  protected McpSyncClient mcpClient;

  @BeforeEach
  void createMcpClient() {
    mcpClient = createMcpClient(testInstance(), createMcpClientRequestCustomizer());
  }

  @AfterEach
  void closeMcpClient() {
    if (mcpClient != null) {
      mcpClient.close();
    }
  }

  protected abstract TestCamundaApplication testInstance();

  protected McpSyncHttpClientRequestCustomizer createMcpClientRequestCustomizer() {
    return DEFAULT_REQUEST_CUSTOMIZER;
  }

  public static McpSyncClient createMcpClient(
      final TestCamundaApplication testInstance,
      final McpSyncHttpClientRequestCustomizer httpClientRequestCustomizer) {
    final HttpClientStreamableHttpTransport.Builder transportBuilder =
        HttpClientStreamableHttpTransport.builder("%s".formatted(testInstance.restAddress()))
            .endpoint("/mcp/cluster")
            .openConnectionOnStartup(false);

    if (httpClientRequestCustomizer != null) {
      transportBuilder.httpRequestCustomizer(httpClientRequestCustomizer);
    }

    return McpClient.sync(transportBuilder.build()).build();
  }
}
