/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.ApiServices;
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
public abstract class ToolsTest {

  public static final CamundaAuthentication AUTHENTICATION_WITH_DEFAULT_TENANT =
      CamundaAuthentication.of(a -> a.user("foo").group("groupId").tenant("<default>"));

  protected McpSyncClient mcpClient;

  @LocalServerPort private int serverPort;
  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setUp() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);

    mcpClient = createMcpClient();
  }

  private McpSyncClient createMcpClient() {
    final HttpClientStreamableHttpTransport.Builder transportBuilder =
        HttpClientStreamableHttpTransport.builder("http://localhost:%d".formatted(serverPort))
            .endpoint("/mcp/cluster")
            .openConnectionOnStartup(false);

    return McpClient.sync(transportBuilder.build()).build();
  }

  protected <T extends ApiServices<?>> void mockApiServiceAuthentication(final T service) {
    doReturn(service).when(service).withAuthentication(any(CamundaAuthentication.class));
  }
}
