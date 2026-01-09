/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.mcp;

import static io.camunda.it.mcp.McpServerTest.DEFAULT_REQUEST_CUSTOMIZER;
import static io.camunda.it.mcp.McpServerTest.createMcpClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class McpServerConfigurationIT {

  @Nested
  @MultiDbTest
  class McpServerEnabledIT {

    @MultiDbTestApplication
    static final TestCamundaApplication MCP_SERVER_ENABLED_INSTANCE =
        new TestCamundaApplication().withProperty("camunda.mcp.enabled", true);

    @Test
    public void mcpInitializeRequestReturnsInitializationResponse() {
      try (final McpSyncClient mcpClient =
          createMcpClient(MCP_SERVER_ENABLED_INSTANCE, DEFAULT_REQUEST_CUSTOMIZER)) {

        final var initializeResult = mcpClient.initialize();
        assertThat(initializeResult).isNotNull();
      }
    }
  }

  @Nested
  @MultiDbTest
  class McpServerDisabledIT {

    @MultiDbTestApplication
    static final TestCamundaApplication MCP_SERVER_DISABLED_INSTANCE = new TestCamundaApplication();

    @Test
    public void mcpInitializeRequestReturnsNotFoundResponse() {
      try (final McpSyncClient mcpClient =
          createMcpClient(MCP_SERVER_DISABLED_INSTANCE, DEFAULT_REQUEST_CUSTOMIZER)) {

        final Throwable throwable = catchThrowable(mcpClient::initialize);
        assertThat(throwable.getCause())
            .hasMessageContaining("Server Not Found")
            .hasMessageContaining("404");
      }
    }
  }
}
