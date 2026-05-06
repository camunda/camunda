/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp;

import static org.mockito.Mockito.when;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.MessageServices;
import io.camunda.service.MessageSubscriptionServices;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@EnableAutoConfiguration
abstract class ToolsTest {

  public static final CamundaAuthentication AUTHENTICATION_WITH_DEFAULT_TENANT =
      CamundaAuthentication.of(a -> a.user("foo").group("groupId").tenant("<default>"));

  protected McpSyncClient mcpClient;

  @MockitoBean protected CamundaAuthenticationProvider authenticationProvider;
  @MockitoBean protected MessageServices messageServices;
  @MockitoBean protected MessageSubscriptionServices messageSubscriptionServices;

  @LocalServerPort private int serverPort;

  @BeforeEach
  void setUp() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);

    mcpClient = createMcpClient();
  }

  protected abstract String endpoint();

  private McpSyncClient createMcpClient() {
    final HttpClientStreamableHttpTransport.Builder transportBuilder =
        HttpClientStreamableHttpTransport.builder("http://localhost:%d".formatted(serverPort))
            .endpoint(endpoint())
            .openConnectionOnStartup(false);

    return McpClient.sync(transportBuilder.build()).build();
  }
}
