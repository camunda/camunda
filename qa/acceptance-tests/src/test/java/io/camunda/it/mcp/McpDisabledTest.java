/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class McpDisabledTest {

  private static CamundaClient camundaClient;

  @MultiDbTestApplication
  private static final TestStandaloneApplication<?> APPLICATION = new TestStandaloneBroker();

  @Test
  void shouldReturn404OnMcpEndpoints() throws Exception {
    try (final var httpClient = HttpClient.newHttpClient()) {
      // Test /sse endpoint
      final var sseRequest =
          HttpRequest.newBuilder()
              .uri(APPLICATION.uri("http", TestZeebePort.REST, "sse"))
              .GET()
              .build();

      final var sseResponse = httpClient.send(sseRequest, HttpResponse.BodyHandlers.ofString());
      assertThat(sseResponse.statusCode()).isEqualTo(404);

      // Test /mcp/message endpoint
      final var mcpRequest =
          HttpRequest.newBuilder()
              .uri(APPLICATION.uri("http", TestZeebePort.REST, "mcp", "message"))
              .POST(HttpRequest.BodyPublishers.noBody())
              .build();

      final var mcpResponse = httpClient.send(mcpRequest, HttpResponse.BodyHandlers.ofString());
      assertThat(mcpResponse.statusCode()).isEqualTo(404);
    }
  }
}
