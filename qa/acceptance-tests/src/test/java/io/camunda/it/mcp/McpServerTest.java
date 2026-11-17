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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

public abstract class McpServerTest {

  public static final McpSyncHttpClientRequestCustomizer DEFAULT_REQUEST_CUSTOMIZER =
      (builder, method, endpoint, body, context) -> {};

  protected static List<String> mcpServersToTest() {
    return List.of("cluster", "processes");
  }

  protected static List<String> physicalTenantMcpServersToTest() {
    return List.of("physical-tenants/default/cluster", "physical-tenants/default/processes");
  }

  protected abstract TestCamundaApplication testInstance();

  protected McpSyncHttpClientRequestCustomizer createMcpClientRequestCustomizer() {
    return DEFAULT_REQUEST_CUSTOMIZER;
  }

  public static McpSyncClient createMcpClient(
      final String mcpServer,
      final TestCamundaApplication testInstance,
      final McpSyncHttpClientRequestCustomizer httpClientRequestCustomizer) {
    return createMcpClientWithEndpoint(
        "/mcp/" + mcpServer, testInstance, httpClientRequestCustomizer);
  }

  public static McpSyncClient createPhysicalTenantMcpClient(
      final String mcpServer,
      final TestCamundaApplication testInstance,
      final McpSyncHttpClientRequestCustomizer httpClientRequestCustomizer) {
    final String[] parts = mcpServer.split("/");
    final String tenantId = parts[1];
    final String serverName = parts[2];
    return createMcpClientWithEndpoint(
        "/physical-tenants/" + tenantId + "/mcp/" + serverName,
        testInstance,
        httpClientRequestCustomizer);
  }

  private static McpSyncClient createMcpClientWithEndpoint(
      final String endpoint,
      final TestCamundaApplication testInstance,
      final McpSyncHttpClientRequestCustomizer httpClientRequestCustomizer) {
    final HttpClientStreamableHttpTransport.Builder transportBuilder =
        HttpClientStreamableHttpTransport.builder("%s".formatted(testInstance.restAddress()))
            .endpoint(endpoint)
            .openConnectionOnStartup(false);

    if (httpClientRequestCustomizer != null) {
      transportBuilder.httpRequestCustomizer(httpClientRequestCustomizer);
    }

    return McpClient.sync(transportBuilder.build()).build();
  }

  public static McpSyncHttpClientRequestCustomizer createBasicAuthCustomizer(
      final String username, final String password) {
    final var credentialsString = "%s:%s".formatted(username, password);
    final var encodedCredentials =
        Base64.getEncoder().encodeToString(credentialsString.getBytes(StandardCharsets.UTF_8));

    return (builder, method, endpoint, body, context) ->
        builder.header("Authorization", "Basic " + encodedCredentials);
  }
}
