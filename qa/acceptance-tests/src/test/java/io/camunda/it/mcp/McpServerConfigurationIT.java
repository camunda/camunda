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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class McpServerConfigurationIT {

  @Nested
  @MultiDbTest
  class McpServerEnabledIT {

    @MultiDbTestApplication
    static final TestCamundaApplication TEST_INSTANCE =
        new TestCamundaApplication().withProperty("camunda.mcp.enabled", true);

    @ParameterizedTest
    @MethodSource("io.camunda.it.mcp.McpServerTest#mcpServersToTest")
    public void mcpInitializeRequestReturnsInitializationResponse(final String mcpServer) {
      try (final McpSyncClient mcpClient =
          createMcpClient(mcpServer, TEST_INSTANCE, DEFAULT_REQUEST_CUSTOMIZER)) {

        final var initializeResult = mcpClient.initialize();
        assertThat(initializeResult).isNotNull();
      }
    }
  }

  @Nested
  @MultiDbTest
  class McpServerEnabledWithRestGatewayDisabledIT {

    @MultiDbTestApplication(managedLifecycle = false)
    static final TestCamundaApplication TEST_INSTANCE =
        new TestCamundaApplication()
            .withProperty("camunda.mcp.enabled", true)
            .withProperty("camunda.rest.enabled", false);

    @BeforeEach
    void setUp() {
      if (!TEST_INSTANCE.isStarted()) {
        TEST_INSTANCE.start();
        final var grpcClient = TEST_INSTANCE.newClientBuilder().preferRestOverGrpc(false).build();
        TEST_INSTANCE.awaitCompleteTopology(TEST_INSTANCE.unifiedConfig(), grpcClient);
      }
    }

    @AfterEach
    void tearDown() {
      TEST_INSTANCE.stop();
    }

    @ParameterizedTest
    @MethodSource("io.camunda.it.mcp.McpServerTest#mcpServersToTest")
    public void mcpInitializeRequestReturnsInitializationResponse(final String mcpServer) {
      try (final McpSyncClient mcpClient =
          createMcpClient(mcpServer, TEST_INSTANCE, DEFAULT_REQUEST_CUSTOMIZER)) {

        final var initializeResult = mcpClient.initialize();
        assertThat(initializeResult).isNotNull();
      }
    }
  }

  @Nested
  @MultiDbTest
  class McpServerDisabledIT {

    @MultiDbTestApplication
    static final TestCamundaApplication TEST_INSTANCE = new TestCamundaApplication();

    @ParameterizedTest
    @MethodSource("io.camunda.it.mcp.McpServerTest#mcpServersToTest")
    public void mcpInitializeRequestReturnsNotFoundResponse(final String mcpServer) {
      try (final McpSyncClient mcpClient =
          createMcpClient(mcpServer, TEST_INSTANCE, DEFAULT_REQUEST_CUSTOMIZER)) {

        final Throwable throwable = catchThrowable(mcpClient::initialize);
        assertThat(throwable.getCause())
            .hasMessageContaining("Server Not Found")
            .hasMessageContaining("404");
      }
    }
  }
}
